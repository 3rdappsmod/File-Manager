package org.fossify.filemanager.extensions

import org.fossify.commons.extensions.isAudioFast
import org.fossify.filemanager.helpers.extraPlayableAudioExtensions
import java.util.Locale

fun String.isZipFile() = endsWith(".zip", true)

// broader than isAudioFast(): also covers formats (e.g. AMR/AMR-WB call recordings) that
// the default mimetype lookup doesn't recognize, so the built-in music player picks them up too
fun String.isPlayableAudioFast(): Boolean {
    if (isAudioFast()) {
        return true
    }

    val extension = substringAfterLast('.', "").lowercase(Locale.getDefault())
    return extension.isNotEmpty() && extraPlayableAudioExtensions.contains(extension)
}

fun String.isPathInHiddenFolder(): Boolean {
    val parts = split("/")
    for (i in 1 until parts.size - 1) {
        val part = parts[i]
        val isHidden = part.startsWith(".") && part != "." && part != ".." && part.isNotEmpty()
        if (isHidden) {
            return true
        }
    }
    return false
}
