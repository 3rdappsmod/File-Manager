package org.fossify.filemanager.helpers.packages

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import java.io.File

// PackageManager's integer overload is also available on Android 8–12.
@Suppress("DEPRECATION")
class InstalledApps(private val manager: PackageManager) {
    fun list(): List<BackupApp> = manager.getInstalledPackages(0).mapNotNull { info ->
        val app = info.applicationInfo ?: return@mapNotNull null
        if (app.sourceDir.isNullOrEmpty()) return@mapNotNull null
        BackupApp(
            info.packageName, app.loadLabel(manager).toString(), info.versionName.orEmpty(),
            app.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        )
    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })

    fun snapshot(packageName: String): PackageSnapshot {
        val info = manager.getPackageInfo(packageName, 0)
        val app = requireNotNull(info.applicationInfo)
        val files = listOf(File(app.sourceDir)) + app.splitSourceDirs.orEmpty().map { File(it) }
        require(files.all { it.isFile && it.canRead() }) { "Cannot read all APK files" }
        return PackageSnapshot(info, files)
    }
}

data class BackupApp(val packageName: String, val label: String, val version: String, val system: Boolean) {
    override fun toString() = "$label ($version)\n$packageName"
}

data class PackageSnapshot(val info: PackageInfo, val files: List<File>) {
    fun matches(other: PackageSnapshot): Boolean = info.lastUpdateTime == other.info.lastUpdateTime &&
        files.map { it.path } == other.files.map { it.path }
}
