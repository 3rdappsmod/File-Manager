package org.fossify.filemanager.helpers

import java.io.File
import java.nio.file.Files

/** Enumerate on a worker thread. Do not follow child symlinks out of the tree or into cycles. */
fun directoryWatchTree(root: File): List<File> {
    val directories = ArrayList<File>()
    val pending = ArrayDeque<File>()
    pending.add(root)
    while (pending.isNotEmpty() && !Thread.currentThread().isInterrupted) {
        val directory = pending.removeLast()
        if (!directory.isDirectory) continue
        directories.add(directory)
        directory.listFiles()?.forEach { child ->
            if (!Files.isSymbolicLink(child.toPath()) && child.isDirectory) pending.add(child)
        }
    }
    return directories
}
