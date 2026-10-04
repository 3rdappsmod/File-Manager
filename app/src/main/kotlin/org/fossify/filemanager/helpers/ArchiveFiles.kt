package org.fossify.filemanager.helpers

import java.io.File
import java.io.IOException
import java.nio.file.Files

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
    val files = directory.listFiles() ?: throw IOException("Cannot read directory: ${directory.path}")
    if (files.any { Files.isSymbolicLink(it.toPath()) }) {
        throw IOException("Cannot archive symbolic links: ${directory.path}")
    }
    return files
}

/** Reject before opening/truncating the output, including paths reached through storage aliases. */
fun validateArchiveDestination(destination: File, sources: List<File>) {
    val target = destination.canonicalPath
    sources.forEach { source ->
        val root = source.canonicalPath
        if (target == root || target.startsWith(root.trimEnd(File.separatorChar) + File.separator)) {
            throw IOException("Archive destination is inside its source: $destination")
        }
    }
}
