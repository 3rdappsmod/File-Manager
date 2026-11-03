package org.fossify.filemanager.helpers.packages

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import org.fossify.commons.extensions.getFilenameFromUri
import org.fossify.filemanager.activities.PackageInstallResultActivity
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

class PackageInstallSession(private val context: Context) {
    private val installer = context.packageManager.packageInstaller

    fun prepare(uri: Uri): Int {
        require(uri.scheme == "content" || uri.scheme == "file") { "Unsupported file URI" }
        val name = if (uri.scheme == "file") uri.lastPathSegment.orEmpty() else context.getFilenameFromUri(uri)
        require(name.endsWith(".apk", true) || name.endsWith(".apk+", true)) { "Select an APK or APK+ file" }
        return if (name.endsWith(".apk+", true)) prepareBundle(uri) else prepareSingle(uri)
    }

    private fun prepareSingle(uri: Uri): Int = create { session ->
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot read APK")
        input.use { source ->
            session.openWrite("base.apk", 0, -1).use { output ->
                require(ApkBundle.copy(source, output) > 0) { "APK is empty" }
                session.fsync(output)
            }
        }
    }

    private fun prepareBundle(uri: Uri): Int {
        val temporary = File.createTempFile("apk-bundle-", ".zip", context.cacheDir)
        try {
            val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot read APK+ file")
            input.use { source -> temporary.outputStream().use { ApkBundle.copy(source, it) } }
            ZipFile(temporary).use { zip ->
                val entries = ApkBundle.entries(zip)
                return create { session ->
                    entries.forEach { entry ->
                        zip.getInputStream(entry).use { source ->
                            session.openWrite(entry.name, 0, entry.size).use { output ->
                                require(ApkBundle.copy(source, output, entry.size) == entry.size) {
                                    "Truncated APK entry"
                                }
                                session.fsync(output)
                            }
                        }
                    }
                }
            }
        } finally {
            temporary.delete()
        }
    }

    private fun create(write: (PackageInstaller.Session) -> Unit): Int {
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        }
        val id = installer.createSession(params)
        var complete = false
        try {
            installer.openSession(id).use(write)
            complete = true
            return id
        } finally {
            if (!complete) installer.abandonSession(id)
        }
    }

    // Android 15 needs the legacy opt-in; Android 16 uses its explicit replacement.
    @Suppress("DEPRECATION")
    fun commit(id: Int) {
        val intent = Intent(context, PackageInstallResultActivity::class.java).apply {
            action = "${context.packageName}.INSTALL_RESULT"
            data = Uri.Builder().scheme("package-install-result").authority(id.toString()).build()
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val options = ActivityOptions.makeBasic()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            options.setPendingIntentCreatorBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            options.setPendingIntentCreatorBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            )
        }
        val callback = PendingIntent.getActivity(context, id, intent, flags, options.toBundle())
        installer.openSession(id).use { it.commit(callback.intentSender) }
    }

    fun abandon(id: Int) {
        installer.abandonSession(id)
    }
}
