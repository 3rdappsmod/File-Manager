package org.fossify.filemanager.helpers

import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import java.io.File

private const val REFRESH_DEBOUNCE_MS = 400L
private const val WATCHED_EVENTS = FileObserver.CREATE or FileObserver.DELETE or
    FileObserver.MOVED_FROM or FileObserver.MOVED_TO or FileObserver.MODIFY or
    FileObserver.CLOSE_WRITE or FileObserver.ATTRIB or FileObserver.DELETE_SELF or FileObserver.MOVE_SELF

/**
 * Watches a single directory (non-recursively, inotify-backed) so the listing showing it can
 * refresh itself when something changes on disk, instead of requiring the user to navigate away
 * and back. Bursts of events (e.g. a multi-file copy) are coalesced into one refresh.
 */
class DirectoryObserver(private val path: String, private val onChanged: () -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private val pendingRefresh = Runnable { onChanged() }
    private var observer: FileObserver? = null

    fun start() {
        if (!File(path).isDirectory) {
            return
        }

        @Suppress("DEPRECATION")
        observer = object : FileObserver(path, WATCHED_EVENTS) {
            override fun onEvent(event: Int, relativePath: String?) {
                handler.removeCallbacks(pendingRefresh)
                handler.postDelayed(pendingRefresh, REFRESH_DEBOUNCE_MS)
            }
        }.apply { startWatching() }
    }

    fun close() {
        handler.removeCallbacks(pendingRefresh)
        observer?.stopWatching()
        observer = null
    }
}
