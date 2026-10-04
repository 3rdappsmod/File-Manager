package org.fossify.filemanager.helpers

import android.app.Activity
import android.content.ActivityNotFoundException
import androidx.core.net.toUri
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.fossify.commons.extensions.ensurePublicUri
import org.fossify.commons.extensions.getMimeType
import org.fossify.commons.extensions.getMimeTypeFromUri
import org.fossify.commons.extensions.openPathIntent
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast
import java.io.File
import org.fossify.commons.helpers.REAL_FILE_PATH
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.filemanager.BuildConfig
import org.fossify.filemanager.R
import org.fossify.filemanager.extensions.config
import org.fossify.filemanager.extensions.isPlayableAudioFast
import org.fossify.filemanager.extensions.openAudioInBuiltInPlayer

class DefaultAppLauncher(private val activity: Activity) {
    fun open(path: String, forcedMime: String, forceChooser: Boolean, onOpened: () -> Unit) {
        ensureBackgroundThread {
            try {
                prepare(path, forcedMime, forceChooser, onOpened)
            } catch (exception: IllegalArgumentException) {
                activity.runOnUiThread { activity.showErrorToast(exception) }
            } catch (exception: SecurityException) {
                activity.runOnUiThread { activity.showErrorToast(exception) }
            }
        }
    }

    private fun prepare(path: String, forcedMime: String, forceChooser: Boolean, onOpened: () -> Unit) {
        val mime = forcedMime.ifEmpty {
            if (path.startsWith("content://")) {
                activity.getMimeTypeFromUri(path.toUri())
            } else {
                DefaultAppFeature.spreadsheetMimeType(path) ?: path.getMimeType()
            }
        }
        val feature = DefaultAppFeature.fromMimeType(mime)
            ?: if (forcedMime.isEmpty() && path.isPlayableAudioFast()) DefaultAppFeature.AUDIO else null
        if (feature == null) {
            activity.runOnUiThread {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    activity.openPathIntent(path, forceChooser, BuildConfig.APPLICATION_ID, forcedMime)
                    onOpened()
                }
            }
            return
        }
        val uri = activity.ensurePublicUri(path, BuildConfig.APPLICATION_ID)
        if (uri == null) {
            activity.runOnUiThread { activity.toast(R.string.feature_default_open_failed) }
            return
        }
        val targetMime = if (DefaultAppFeature.fromMimeType(mime) == feature) mime else feature.mimeType
        val targets = DefaultAppTargets(activity).find(feature, uri, targetMime)
        attachOriginalPath(targets, path)
        activity.runOnUiThread {
            if (!activity.isFinishing && !activity.isDestroyed) {
                openResolved(feature, path, targets, forceChooser, onOpened)
            }
        }
    }

    private fun attachOriginalPath(targets: List<DefaultAppTarget>, path: String) {
        if (path.startsWith("/") && File(path).canRead()) {
            targets.forEach { it.intent?.putExtra(REAL_FILE_PATH, path) }
        }
    }

    private fun openResolved(
        feature: DefaultAppFeature,
        path: String,
        targets: List<DefaultAppTarget>,
        forceChooser: Boolean,
        onOpened: () -> Unit
    ) {
        val preferred = targets.find { it.id == activity.config.defaultApps.get(feature) }
        if (!forceChooser && preferred != null && launch(preferred, path)) {
            onOpened()
        } else {
            choose(feature, targets) { target, always ->
                if (launch(target, path)) {
                    if (always) activity.config.defaultApps.set(feature, target.id)
                    onOpened()
                }
            }
        }
    }

    private fun choose(
        feature: DefaultAppFeature,
        targets: List<DefaultAppTarget>,
        onChoice: (DefaultAppTarget, Boolean) -> Unit
    ) {
        if (targets.isEmpty()) {
            activity.toast(R.string.feature_default_no_apps)
            return
        }
        var selected = -1
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(feature.title)
            .setSingleChoiceItems(targets.map { it.label }.toTypedArray(), selected) { dialog, index ->
                selected = index
                (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = true
            }
            .setPositiveButton(R.string.feature_default_always) { _, _ -> onChoice(targets[selected], true) }
            .setNegativeButton(R.string.feature_default_once) { _, _ -> onChoice(targets[selected], false) }
            .setNeutralButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = false
        }
        dialog.show()
    }

    private fun launch(target: DefaultAppTarget, path: String): Boolean = try {
        if (target.id == DefaultAppFeature.BUILT_IN_AUDIO) {
            activity.openAudioInBuiltInPlayer(path)
        } else {
            activity.startActivity(target.intent)
        }
        true
    } catch (exception: ActivityNotFoundException) {
        activity.showErrorToast(exception)
        false
    } catch (exception: SecurityException) {
        activity.showErrorToast(exception)
        false
    }
}
