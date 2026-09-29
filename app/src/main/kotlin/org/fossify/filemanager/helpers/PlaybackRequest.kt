package org.fossify.filemanager.helpers

/** Distinguish lifecycle redelivery from a new user request, even for the same track. */
class PlaybackRequest {
    enum class Action { IGNORE, RESUME, LOAD }

    private var lastId: String? = null
    var generation = 0
        private set

    fun accept(id: String, samePreparedTrack: Boolean = false): Action {
        if (id == lastId) return Action.IGNORE
        lastId = id
        invalidate()
        return if (samePreparedTrack) Action.RESUME else Action.LOAD
    }

    fun invalidate() {
        generation++
    }
}
