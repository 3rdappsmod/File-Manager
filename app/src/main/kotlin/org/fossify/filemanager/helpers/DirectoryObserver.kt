package org.fossify.filemanager.helpers

import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File

private const val REFRESH_INTERVAL_MS = 400L
private const val WATCH_RECOVERY_INTERVAL_MS = 2000L
private const val WATCHED_EVENTS = FileObserver.CREATE or FileObserver.DELETE or
    FileObserver.MOVED_FROM or FileObserver.MOVED_TO or FileObserver.MODIFY or
    FileObserver.CLOSE_WRITE or FileObserver.ATTRIB or FileObserver.DELETE_SELF or FileObserver.MOVE_SELF

/**
 * Watches a single directory (non-recursively, inotify-backed) so the listing showing it can
 * refresh itself when something changes on disk, instead of requiring the user to navigate away
 * and back. Events are coalesced without postponing a pending refresh.
 * A lightweight identity check also recovers watches after deletion or replacement of the
 * directory (including its ancestors); it does not enumerate directory contents.
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

    private var directoryIdentity: Pair<Long, Long>? = null
    private val checkDirectory = object : Runnable {
        override fun run() {
            if (!active) return
            reconcileWatcher()
            handler.postDelayed(this, WATCH_RECOVERY_INTERVAL_MS)
        }
    }

    fun start() {
        close()
        // SAF pseudo-paths cannot be watched through the filesystem API.
        if (!File(path).isAbsolute) return
        active = true
        reconcileWatcher()
        handler.postDelayed(checkDirectory, WATCH_RECOVERY_INTERVAL_MS)
    }

    private fun readDirectoryIdentity(): Pair<Long, Long>? {
        return try {
            val stat = Os.stat(path)
            if (OsConstants.S_ISDIR(stat.st_mode)) stat.st_dev to stat.st_ino else null
        } catch (_: ErrnoException) {
            null
        }
    }

    private fun reconcileWatcher() {
        val identity = readDirectoryIdentity()
        if (identity == directoryIdentity) return
        generation++
        observer?.stopWatching()
        observer = null
        directoryIdentity = identity
        scheduleRefresh()
        if (identity == null) return

        val currentGeneration = generation
        @Suppress("DEPRECATION")
        observer = object : FileObserver(path, WATCHED_EVENTS) {
            override fun onEvent(event: Int, relativePath: String?) {
                handler.post {
                    if (active && generation == currentGeneration) {
                        if (event and (FileObserver.DELETE_SELF or FileObserver.MOVE_SELF) != 0) {
                            reconcileWatcher()
                        }
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
        directoryIdentity = null
    }
}
