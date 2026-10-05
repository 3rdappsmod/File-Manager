package org.fossify.filemanager.helpers

import org.fossify.commons.extensions.searchMatches

/** Recompute against the current buffer before moving the cursor; old offsets may no longer exist. */
internal class EditorSearch {
    private var query = ""
    private var matches = emptyList<Int>()
    private var index = 0

    fun select(text: String, query: String, direction: Int = 0): Int? {
        val current = if (query.isNotBlank() && query.length > 1) text.searchMatches(query) else emptyList()
        if (this.query != query || matches != current) {
            index = 0
        } else if (current.isNotEmpty()) {
            index = Math.floorMod(index + direction, current.size)
        }
        this.query = query
        matches = current
        return matches.getOrNull(index)
    }
}
