package org.fossify.filemanager.helpers

import org.fossify.filemanager.extensions.isPathInHiddenFolder
import org.fossify.filemanager.extensions.isPlayableAudioFast
import java.io.File
import java.util.Locale

/** External documents may have opaque IDs and no filename extension. */
fun isExternalAudioUri(path: String): Boolean = path.startsWith("content://") || path.startsWith("file://")

fun isVisibleAudio(path: String, showHidden: Boolean): Boolean =
    path.isPlayableAudioFast() && (showHidden || (!File(path).name.startsWith(".") && !path.isPathInHiddenFolder()))

fun buildAudioPlaylist(selectedPath: String, candidates: List<String>, showHidden: Boolean): List<String> {
    // A grant to one document does not authorize enumerating its parent or siblings.
    if (isExternalAudioUri(selectedPath)) return listOf(selectedPath)
    if (!isVisibleAudio(selectedPath, showHidden)) return emptyList()
    return (candidates + selectedPath)
        .distinct()
        .filter { isVisibleAudio(it, showHidden) }
        .sortedBy { File(it).name.lowercase(Locale.ROOT) }
}
