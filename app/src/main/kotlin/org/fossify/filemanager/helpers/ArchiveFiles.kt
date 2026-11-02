package org.fossify.filemanager.helpers

import java.io.File
import java.io.IOException

/** Validate before either conflict handling or writing an untrusted archive entry. */
fun resolveArchiveEntry(destination: File, entryName: String): File {
    val name = entryName.replace('\\', '/')
    val isAbsolute = name.startsWith('/') || Regex("^[A-Za-z]:").containsMatchIn(name)
    if (name.isBlank() || isAbsolute || name.split('/').contains("..")) {
        throw IOException("Unsafe archive entry: $entryName")
    }
    val root = destination.canonicalFile
    val target = File(destination, name)
    val prefix = root.path.trimEnd(File.separatorChar) + File.separator
    if (target.canonicalPath == root.path || !target.canonicalPath.startsWith(prefix)) {
        throw IOException("Archive entry leaves the destination: $entryName")
    }
    return target
}

/** An unreadable or vanished directory must not become a successful empty archive. */
fun listFilesForArchive(directory: File): Array<File> {
    return directory.listFiles() ?: throw IOException("Cannot read directory: ${directory.path}")
}
