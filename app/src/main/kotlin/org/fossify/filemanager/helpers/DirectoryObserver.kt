package org.fossify.filemanager.helpers

import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val REFRESH_INTERVAL_MS = 400L
private const val WATCH_RECOVERY_INTERVAL_MS = 2000L
private const val STRUCTURE_EVENTS = FileObserver.CREATE or FileObserver.DELETE or
    FileObserver.MOVED_FROM or FileObserver.MOVED_TO or FileObserver.DELETE_SELF or FileObserver.MOVE_SELF
private const val WATCHED_EVENTS =
    STRUCTURE_EVENTS or FileObserver.MODIFY or FileObserver.CLOSE_WRITE or FileObserver.ATTRIB

/** Watches descendants too, so the parent listing's folder counts and sizes stay current. */
class DirectoryObserver(private val path: String, private val onChanged: () -> Unit) {
    private data class Watch(val identity: Pair<Long, Long>, val observer: FileObserver)

    private val handler = Handler(Looper.getMainLooper())
    private val watches = HashMap<String, Watch>()
    private var worker: ExecutorService? = null
    private var active = false
    private var generation = 0
    private var refreshPending = false
    private var scanPending = false
    private var scanning = false
    private var rescan = false
    private var rootIdentity: Pair<Long, Long>? = null
    private val pendingRefresh = Runnable {
        refreshPending = false
        if (active) onChanged()
    }
    private val pendingScan = Runnable {
        scanPending = false
        scanTree()
    }
    private val checkDirectory = object : Runnable {
        override fun run() {
            if (!active) return
            val identity = readIdentity(path)
            if (identity != rootIdentity) {
                rootIdentity = identity
                requestScan()
                scheduleRefresh()
            }
            handler.postDelayed(this, WATCH_RECOVERY_INTERVAL_MS)
        }
    }

    fun start() {
        close()
        if (!File(path).isAbsolute) return
        active = true
        worker = Executors.newSingleThreadExecutor()
        rootIdentity = readIdentity(path)
        // Install the root immediately to catch changes during the initial tree walk.
        rootIdentity?.let { watches[path] = Watch(it, createWatcher(path)) }
        requestScan()
        handler.postDelayed(checkDirectory, WATCH_RECOVERY_INTERVAL_MS)
    }

    private fun readIdentity(directory: String): Pair<Long, Long>? {
        return try {
            val stat = Os.stat(directory)
            if (OsConstants.S_ISDIR(stat.st_mode)) stat.st_dev to stat.st_ino else null
        } catch (_: ErrnoException) {
            null
        }
    }

    private fun requestScan() {
        if (scanning) {
            rescan = true
        } else if (!scanPending) {
            scanPending = true
            handler.postDelayed(pendingScan, REFRESH_INTERVAL_MS)
        }
    }

    private fun scanTree() {
        if (!active) return
        scanning = true
        val currentGeneration = generation
        worker?.execute {
            val identities = directoryWatchTree(File(path)).mapNotNull { directory ->
                readIdentity(directory.path)?.let { directory.path to it }
            }.toMap()
            handler.post {
                if (active && generation == currentGeneration) {
                    // Rewalk newly watched subtrees once to close the enumeration/registration gap.
                    if (applyTree(identities)) rescan = true
                    scanning = false
                    // Pick up content changes that occurred before new watches were installed.
                    scheduleRefresh()
                    if (rescan) {
                        rescan = false
                        requestScan()
                    }
                }
            }
        }
    }

    private fun applyTree(identities: Map<String, Pair<Long, Long>>): Boolean {
        var added = false
        watches.keys.toList().forEach { directory ->
            if (watches[directory]?.identity != identities[directory]) {
                watches.remove(directory)?.observer?.stopWatching()
            }
        }
        identities.forEach { (directory, identity) ->
            if (directory !in watches) {
                watches[directory] = Watch(identity, createWatcher(directory))
                added = true
            }
        }
        return added
    }

    @Suppress("DEPRECATION")
    private fun createWatcher(directory: String): FileObserver {
        val currentGeneration = generation
        return object : FileObserver(directory, WATCHED_EVENTS) {
            override fun onEvent(event: Int, relativePath: String?) {
                val source = this
                handler.post {
                    if (active && generation == currentGeneration && watches[directory]?.observer === source) {
                        if (event and STRUCTURE_EVENTS != 0) requestScan()
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
        generation++
        handler.removeCallbacksAndMessages(null)
        watches.values.forEach { it.observer.stopWatching() }
        watches.clear()
        worker?.shutdownNow()
        worker = null
        refreshPending = false
        scanPending = false
        scanning = false
        rescan = false
        rootIdentity = null
    }
}
