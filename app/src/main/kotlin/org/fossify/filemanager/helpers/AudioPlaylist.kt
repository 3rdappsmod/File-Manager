package org.fossify.filemanager.helpers

import org.fossify.filemanager.extensions.isPathInHiddenFolder
import org.fossify.filemanager.extensions.isPlayableAudioFast
import java.io.File
import java.util.Locale

fun isVisibleAudio(path: String, showHidden: Boolean): Boolean =
    path.isPlayableAudioFast() && (showHidden || (!File(path).name.startsWith(".") && !path.isPathInHiddenFolder()))

fun buildAudioPlaylist(selectedPath: String, candidates: List<String>, showHidden: Boolean): List<String> {
    if (!isVisibleAudio(selectedPath, showHidden)) return emptyList()
    return (candidates + selectedPath)
        .distinct()
        .filter { isVisibleAudio(it, showHidden) }
        .sortedBy { File(it).name.lowercase(Locale.ROOT) }
}
