package org.fossify.filemanager.helpers

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import java.io.File
import org.fossify.filemanager.BuildConfig

internal class DocumentWriteAccess(private val context: Context) {
    fun canGrant(uri: Uri, path: String): Boolean {
        // Our FileProvider can delegate access to writable local files. For other providers,
        // forward only an existing grant (including SAF tree grants), never assume write access.
        return if (uri.authority == "${BuildConfig.APPLICATION_ID}.provider") {
            File(path).canWrite()
        } else {
            context.checkUriPermission(
                uri, Process.myPid(), Process.myUid(), Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}
