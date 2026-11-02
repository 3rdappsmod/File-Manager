package org.fossify.filemanager.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioPlaylistTest {
    @Test
    fun hiddenRecordingsAreExcludedUnlessVisible() {
        val selected = "/Music/song.mp3"
        val hidden = "/Music/.recording.amr"
        assertEquals(listOf(selected), buildAudioPlaylist(selected, listOf(selected, hidden), false))
        assertEquals(listOf(hidden, selected), buildAudioPlaylist(selected, listOf(selected, hidden), true))
    }

    @Test
    fun hiddenParentAndRevokedVisibilityCannotStartPlayback() {
        assertTrue(buildAudioPlaylist("/Music/.private/call.amr", emptyList(), false).isEmpty())
        assertTrue(buildAudioPlaylist("/Music/.call.amr", emptyList(), false).isEmpty())
    }

    @Test
    fun selectedFileIsRetainedWhenDirectoryCannotBeListed() {
        assertEquals(listOf("/Music/call.AMR"), buildAudioPlaylist("/Music/call.AMR", emptyList(), false))
    }

    @Test
    fun nonAudioAndDuplicatesAreRemoved() {
        assertEquals(listOf("/Music/a.mp3", "/Music/b.mp3"), buildAudioPlaylist(
            "/Music/b.mp3", listOf("/Music/b.mp3", "/Music/a.mp3", "/Music/readme.txt"), false
        ))
    }
}
