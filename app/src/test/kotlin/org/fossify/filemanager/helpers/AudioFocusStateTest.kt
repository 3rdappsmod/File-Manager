package org.fossify.filemanager.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFocusStateTest {
    @Test
    fun delayedFocusTriggersPlaybackOnlyOnce() {
        val state = AudioFocusState()
        val token = state.begin()
        state.complete(token, AudioFocusState.Result.DELAYED)
        assertTrue(state.gain(token))
        assertEquals(AudioFocusState.Result.GRANTED, state.result)
        assertFalse(state.gain(token))
    }

    @Test
    fun cancelPreventsLateAutoplayAfterPauseStopOrHeadphoneRemoval() {
        val state = AudioFocusState()
        val token = state.begin()
        state.cancel()
        assertFalse(state.isCurrent(token))
        assertFalse(state.gain(token))
    }

    @Test
    fun oldCallbackCannotResumeOrInterruptANewerRequest() {
        val state = AudioFocusState()
        val old = state.begin()
        state.cancel()
        val current = state.begin()
        assertFalse(state.isCurrent(old))
        assertFalse(state.gain(old))
        assertTrue(state.gain(current))
    }

    @Test
    fun grantedOrFailedRequestDoesNotTriggerDelayedAutoplay() {
        for (result in listOf(AudioFocusState.Result.GRANTED, AudioFocusState.Result.FAILED)) {
            val state = AudioFocusState()
            val token = state.begin()
            state.complete(token, result)
            assertFalse(state.gain(token))
        }
    }
}
