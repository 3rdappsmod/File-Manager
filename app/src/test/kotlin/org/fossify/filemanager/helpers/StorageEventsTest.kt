package org.fossify.filemanager.helpers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageEventsTest {
    @Test
    fun volumeRemovalOnlyMatchesItsOwnFiles() {
        assertTrue("/storage/ABCD/Music/song.mp3".isWithinStorage("/storage/ABCD/"))
        assertTrue("/storage/ABCD".isWithinStorage("/storage/ABCD"))
        assertFalse("/storage/ABCD2/song.mp3".isWithinStorage("/storage/ABCD"))
        assertFalse("/storage/emulated/0/song.mp3".isWithinStorage("/storage/ABCD"))
        assertFalse("/storage/song.mp3".isWithinStorage(""))
    }
}
