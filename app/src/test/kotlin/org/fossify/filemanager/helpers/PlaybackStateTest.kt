package org.fossify.filemanager.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStateTest {
    @Test
    fun pauseDuringPreparationPreventsAutoplay() {
        val state = PlaybackState()
        state.prepare()
        state.pause()
        state.ready()
        assertFalse(state.playWhenReady)
        assertTrue(state.isPrepared)
    }

    @Test
    fun repeatedPauseDoesNotRequestPlayback() {
        val state = PlaybackState()
        state.prepare()
        state.ready()
        state.started()
        state.pause()
        state.pause()
        assertEquals(PlaybackState.Status.PAUSED, state.status)
        assertFalse(state.playWhenReady)
    }

    @Test
    fun stopAndErrorDiscardPreparedStateAndAllowFreshPreparation() {
        for (failed in listOf(false, true)) {
            val state = PlaybackState()
            state.prepare()
            state.ready()
            state.started()
            state.stop(failed)
            assertFalse(state.isPrepared)
            assertFalse(state.playWhenReady)
            state.prepare()
            state.ready()
            assertTrue(state.playWhenReady)
            state.started()
            assertEquals(PlaybackState.Status.PLAYING, state.status)
        }
    }
    @Test
    fun focusDelayRetainsPreparedPlayerAndAutoplayIntent() {
        val state = PlaybackState()
        state.prepare()
        state.ready()
        state.awaitFocus()
        assertTrue(state.isPrepared)
        assertTrue(state.isWaitingToPlay)
        state.started()
        assertFalse(state.waitingForFocus)
        assertEquals(PlaybackState.Status.PLAYING, state.status)
    }

    @Test
    fun explicitPauseCancelsDelayedPlayback() {
        val state = PlaybackState()
        state.prepare()
        state.ready()
        state.awaitFocus()
        state.pause()
        assertFalse(state.playWhenReady)
        assertFalse(state.waitingForFocus)
        assertTrue(state.isPrepared)
    }
}
