package org.fossify.filemanager.helpers.packages

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.graphics.createBitmap
import android.os.Build
import org.json.JSONObject
import java.io.OutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.FileAlreadyExistsException
import org.fossify.commons.extensions.getInternalStoragePath
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PackageBackup(private val context: Context) {
    private val apps = InstalledApps(context.packageManager)

    fun save(app: BackupApp): String {
        val snapshot = apps.snapshot(app.packageName)
        val folder = File(context.getInternalStoragePath(), "backups/apps")
        check(folder.isDirectory || folder.mkdirs()) { "Cannot create backups/apps folder" }
        val name = ApkBundle.fileName(app.label, snapshot.info.versionName.orEmpty(), snapshot.files.size > 1)
        val temporary = File.createTempFile(".backup-", ".tmp", folder)
        try {
            temporary.outputStream().use { output -> write(snapshot, app, output) }
            require(snapshot.matches(apps.snapshot(app.packageName))) { "App changed during backup. Please retry." }
            return publish(temporary, folder, name)
        } finally {
            temporary.delete()
        }
    }

    private fun publish(temporary: File, folder: File, name: String): String {
        val stem = name.substringBeforeLast('.')
        val extension = name.substringAfterLast('.')
        var candidate = name
        var index = 1
        while (true) {
            try {
                Files.move(temporary.toPath(), File(folder, candidate).toPath())
                return candidate
            } catch (_: FileAlreadyExistsException) {
                candidate = "$stem (${index++}).$extension"
            }
        }
    }

    private fun write(snapshot: PackageSnapshot, app: BackupApp, output: OutputStream) {
        if (snapshot.files.size == 1) {
            snapshot.files.first().inputStream().use { ApkBundle.copy(it, output) }
            return
        }
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("apk+.json"))
            zip.write(metadata(snapshot, app).toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            writeIcon(app, zip)
            var total = 0L
            snapshot.files.forEachIndexed { index, file ->
                zip.putNextEntry(ZipEntry(if (index == 0) "base.apk" else "split_$index.apk"))
                total += file.inputStream().use { ApkBundle.copy(it, zip, ApkBundle.MAX_BYTES - total) }
                zip.closeEntry()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun metadata(snapshot: PackageSnapshot, app: BackupApp) = JSONObject().apply {
        put("app_name", app.label)
        put("package_name", app.packageName)
        put("version_name", snapshot.info.versionName.orEmpty())
        val version = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            snapshot.info.longVersionCode
        } else {
            snapshot.info.versionCode.toLong()
        }
        put("version_code", version)
        put("min_sdk_version", snapshot.info.applicationInfo?.minSdkVersion)
    }

    private fun writeIcon(app: BackupApp, zip: ZipOutputStream) {
        val drawable = context.packageManager.getApplicationIcon(app.packageName)
        val bitmap = createBitmap(ICON_SIZE, ICON_SIZE)
        try {
            drawable.setBounds(0, 0, ICON_SIZE, ICON_SIZE)
            drawable.draw(Canvas(bitmap))
            zip.putNextEntry(ZipEntry("icon.png"))
            bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, zip)
            zip.closeEntry()
        } finally {
            bitmap.recycle()
        }
    }

    companion object {
        private const val ICON_SIZE = 96
        private const val PNG_QUALITY = 100
    }
}
