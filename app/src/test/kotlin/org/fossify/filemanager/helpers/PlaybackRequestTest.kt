package org.fossify.filemanager.helpers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRequestTest {
    @Test
    fun reconnectingDoesNotRestartTheOriginalTrack() {
        val requests = PlaybackRequest()
        assertTrue(requests.accept("activity-request"))
        assertFalse(requests.accept("activity-request"))
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
}
