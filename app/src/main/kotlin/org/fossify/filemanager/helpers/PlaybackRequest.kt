package org.fossify.filemanager.helpers

/** Ignore recreated activities and discard directory loads superseded by a newer request. */
class PlaybackRequest {
    private var lastId: String? = null
    var generation = 0
        private set

    fun accept(id: String): Boolean {
        if (id == lastId) return false
        lastId = id
        invalidate()
        return true
    }

    fun invalidate() {
        generation++
    }
}
