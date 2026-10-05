package org.fossify.filemanager.helpers

import android.app.Activity
import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import org.fossify.filemanager.R
import org.fossify.filemanager.activities.OpenAudioActivity

internal data class DefaultAppTarget(val id: String, val label: String, val intent: Intent? = null)

internal class DefaultAppTargets(private val activity: Activity) {
    @Suppress("DEPRECATION")
    fun find(feature: DefaultAppFeature, uri: Uri, mimeType: String): List<DefaultAppTarget> {
        val manager = activity.packageManager
        val actions = if (feature == DefaultAppFeature.TEXT) {
            listOf(Intent.ACTION_EDIT, Intent.ACTION_VIEW)
        } else {
            listOf(Intent.ACTION_VIEW)
        }
        val targets = actions.flatMap { action ->
            val intent = Intent(action).setDataAndType(uri, DefaultAppFeature.normalizeMimeType(mimeType))
            manager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).mapNotNull { resolved ->
                val info = resolved.activityInfo
                val permitted = info.permission == null ||
                    activity.checkSelfPermission(info.permission) == PackageManager.PERMISSION_GRANTED
                val accessible = info.enabled && info.exported && permitted
                if (!accessible || info.name == OpenAudioActivity::class.java.name) {
                    null
                } else {
                    val component = ComponentName(info.packageName, info.name)
                    val target = Intent(intent).setComponent(component).apply {
                        clipData = ClipData.newRawUri("", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        if (info.packageName != activity.packageName) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        if (action == Intent.ACTION_EDIT) addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    }
                    DefaultAppTarget(
                        "$action|${component.flattenToString()}", label(info), target
                    )
                }
            }
        }.distinctBy { it.intent?.component }.sortedBy { it.label.lowercase() }.toMutableList()
        if (feature == DefaultAppFeature.AUDIO) {
            val builtIn = DefaultAppTarget(
                DefaultAppFeature.BUILT_IN_AUDIO, activity.getString(R.string.built_in_player)
            )
            targets.add(0, builtIn)
        }
        return targets
    }

    private fun label(info: ActivityInfo): String {
        val appName = info.applicationInfo.loadLabel(activity.packageManager).toString()
        val activityName = info.loadLabel(activity.packageManager).toString()
        return if (appName == activityName) appName else "$appName · $activityName"
    }

    fun label(id: String): String {
        if (id.isEmpty()) return activity.getString(R.string.feature_default_ask)
        if (id == DefaultAppFeature.BUILT_IN_AUDIO) return activity.getString(R.string.built_in_player)
        val component = ComponentName.unflattenFromString(id.substringAfter('|', ""))
            ?: return activity.getString(R.string.feature_default_unavailable)
        return try {
            @Suppress("DEPRECATION")
            label(activity.packageManager.getActivityInfo(component, 0))
        } catch (_: PackageManager.NameNotFoundException) {
            activity.getString(R.string.feature_default_unavailable)
        }
    }
}
