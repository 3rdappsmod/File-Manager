package org.fossify.filemanager.helpers

/** Playback intent is retained while asynchronous preparation is in progress. */
class PlaybackState {
    enum class Status { STOPPED, PREPARING, PAUSED, PLAYING, ERROR }

    var status = Status.STOPPED
        private set
    var playWhenReady = false
        private set
    val isPrepared get() = status == Status.PAUSED || status == Status.PLAYING

    fun prepare() {
        status = Status.PREPARING
        playWhenReady = true
    }

    fun ready() {
        status = Status.PAUSED
    }

    fun requestPlay() {
        playWhenReady = true
    }

    fun started() {
        status = Status.PLAYING
        playWhenReady = true
    }

    fun pause() {
        playWhenReady = false
        if (isPrepared) status = Status.PAUSED
    }

    fun stop(failed: Boolean = false) {
        status = if (failed) Status.ERROR else Status.STOPPED
        playWhenReady = false
    }
}
