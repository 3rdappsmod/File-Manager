package org.fossify.filemanager.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRequestTest {
    @Test
    fun reconnectingDoesNotRestartTheOriginalTrack() {
        val requests = PlaybackRequest()
        assertEquals(PlaybackRequest.Action.LOAD, requests.accept("activity-request"))
        assertEquals(PlaybackRequest.Action.IGNORE, requests.accept("activity-request", samePreparedTrack = true))
    }

    @Test
    fun newerSelectionAndStopInvalidateAnOutstandingDirectoryLoad() {
        val requests = PlaybackRequest()
        requests.accept("first")
        val original = requests.generation
        requests.accept("second")
        assertTrue(original != requests.generation)
        val second = requests.generation
        requests.invalidate()
        assertTrue(second != requests.generation)
    }
    @Test
    fun newOpenRequestForPausedOrCompletedTrackRequestsResume() {
        for (request in listOf("reopen-paused", "reopen-completed")) {
            val requests = PlaybackRequest()
            requests.accept("original")
            assertEquals(PlaybackRequest.Action.RESUME, requests.accept(request, samePreparedTrack = true))
            // Redelivery of that same request must not override a subsequent manual pause.
            assertEquals(PlaybackRequest.Action.IGNORE, requests.accept(request, samePreparedTrack = true))
        }
    }

    @Test
    fun newRequestAfterStopOrErrorReloadsTheTrack() {
        val requests = PlaybackRequest()
        requests.accept("original")
        requests.invalidate()
        assertEquals(PlaybackRequest.Action.LOAD, requests.accept("reopen", samePreparedTrack = false))
    }
}
