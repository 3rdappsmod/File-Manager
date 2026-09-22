package org.fossify.filemanager.helpers

import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import java.io.File

private const val REFRESH_INTERVAL_MS = 400L
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
    // Lifecycle and event handling are serialized on the main looper.
    private var active = false
    private var generation = 0
    private var refreshPending = false
    private val pendingRefresh = Runnable {
        refreshPending = false
        if (active) onChanged()
    }
    private var observer: FileObserver? = null

    fun start() {
        close()
        if (!File(path).isDirectory) {
            return
        }

        active = true
        val currentGeneration = generation
        @Suppress("DEPRECATION")
        observer = object : FileObserver(path, WATCHED_EVENTS) {
            override fun onEvent(event: Int, relativePath: String?) {
                handler.post {
                    if (active && generation == currentGeneration) {
                        scheduleRefresh()
                    }
                }
            }
        }.apply { startWatching() }
    }

    private fun scheduleRefresh() {
        if (!refreshPending) {
            refreshPending = true
            handler.postDelayed(pendingRefresh, REFRESH_INTERVAL_MS)
        }
    }

    fun close() {
        active = false
        refreshPending = false
        generation++
        handler.removeCallbacksAndMessages(null)
        observer?.stopWatching()
        observer = null
    }
}
