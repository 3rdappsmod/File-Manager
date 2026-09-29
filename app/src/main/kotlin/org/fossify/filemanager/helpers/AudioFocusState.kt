package org.fossify.filemanager.helpers

/** A canceled or replaced focus request must never trigger delayed autoplay. */
class AudioFocusState {
    enum class Result { GRANTED, DELAYED, FAILED }

    var result = Result.FAILED
        private set
    private var generation = 0

    fun begin(): Int {
        generation++
        result = Result.DELAYED
        return generation
    }

    fun complete(token: Int, outcome: Result) {
        if (token == generation) result = outcome
    }

    fun isCurrent(token: Int) = token == generation && result != Result.FAILED

    fun gain(token: Int): Boolean {
        if (!isCurrent(token) || result != Result.DELAYED) return false
        result = Result.GRANTED
        return true
    }

    fun cancel() {
        generation++
        result = Result.FAILED
    }
}
