package org.fossify.filemanager.extensions

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import androidx.core.net.toUri
import java.util.UUID
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getParentPath
import org.fossify.commons.extensions.renameFile
import org.fossify.commons.extensions.setAsIntent
import org.fossify.commons.extensions.sharePathsIntent
import org.fossify.filemanager.BuildConfig
import org.fossify.filemanager.activities.MusicPlayerActivity
import org.fossify.filemanager.helpers.DefaultAppLauncher
import org.fossify.filemanager.helpers.OPEN_AS_AUDIO
import org.fossify.filemanager.helpers.OPEN_AS_DEFAULT
import org.fossify.filemanager.helpers.OPEN_AS_IMAGE
import org.fossify.filemanager.helpers.OPEN_AS_TEXT
import org.fossify.filemanager.helpers.OPEN_AS_VIDEO

fun Activity.sharePaths(paths: ArrayList<String>) {
    sharePathsIntent(paths, BuildConfig.APPLICATION_ID)
}

fun Activity.tryOpenPathIntent(path: String, forceChooser: Boolean, openAsType: Int = OPEN_AS_DEFAULT, finishActivity: Boolean = false) {
    openPath(path, forceChooser, openAsType) {
        if (finishActivity) finish()
    }
}

fun Activity.openPath(
    path: String,
    forceChooser: Boolean,
    openAsType: Int = OPEN_AS_DEFAULT,
    onOpened: () -> Unit = {}
) {
    val mime = getMimeType(openAsType)
    DefaultAppLauncher(this).open(path, mime, forceChooser, onOpened)
}

private fun getMimeType(type: Int) = when (type) {
    OPEN_AS_DEFAULT -> ""
    OPEN_AS_TEXT -> "text/*"
    OPEN_AS_IMAGE -> "image/*"
    OPEN_AS_AUDIO -> "audio/*"
    OPEN_AS_VIDEO -> "video/*"
    else -> "*/*"
}

fun Activity.setAs(path: String) {
    setAsIntent(path, BuildConfig.APPLICATION_ID)
}

fun Activity.openAudioInBuiltInPlayer(path: String) {
    Intent(this, MusicPlayerActivity::class.java).apply {
        if (path.startsWith("content://")) {
            data = path.toUri()
            clipData = ClipData.newRawUri("audio", data!!)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        putExtra(MusicPlayerActivity.EXTRA_PATH, path)
        putExtra(MusicPlayerActivity.EXTRA_REQUEST_ID, UUID.randomUUID().toString())
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(this)
    }
}

fun BaseSimpleActivity.toggleItemVisibility(oldPath: String, hide: Boolean, callback: ((newPath: String) -> Unit)? = null) {
    val path = oldPath.getParentPath()
    var filename = oldPath.getFilenameFromPath()
    if ((hide && filename.startsWith('.')) || (!hide && !filename.startsWith('.'))) {
        callback?.invoke(oldPath)
        return
    }

    filename = if (hide) {
        ".${filename.trimStart('.')}"
    } else {
        filename.substring(1, filename.length)
    }

    val newPath = "$path/$filename"
    if (oldPath != newPath) {
        renameFile(oldPath, newPath, false) { success, useAndroid30Way ->
            callback?.invoke(newPath)
        }
    }
}
