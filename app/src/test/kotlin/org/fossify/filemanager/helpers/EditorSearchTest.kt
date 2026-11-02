package org.fossify.filemanager.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EditorSearchTest {
    @Test
    fun shortenedDocumentNeverReusesAnOutOfBoundsOffset() {
        val search = EditorSearch()
        assertEquals(10, search.select("0123456789match", "match"))
        assertNull(search.select("", "match", 1))
        assertEquals(0, search.select("match", "match", 1))
    }

    @Test
    fun clearingOrShorteningTheQueryClearsOldMatches() {
        val search = EditorSearch()
        search.select("hello world", "world")
        assertNull(search.select("hello world", ""))
        assertNull(search.select("hello world", "w", 1))
        assertNull(search.select("hello world", "  ", -1))
    }

    @Test
    fun movingInBothDirectionsWrapsOnlyWithinCurrentMatches() {
        val search = EditorSearch()
        assertEquals(0, search.select("ab AB ab", "ab"))
        assertEquals(3, search.select("ab AB ab", "ab", 1))
        assertEquals(6, search.select("ab AB ab", "ab", 1))
        assertEquals(0, search.select("ab AB ab", "ab", 1))
        assertEquals(6, search.select("ab AB ab", "ab", -1))
    }

    @Test
    fun changedMatchPositionsResetTheSelection() {
        val search = EditorSearch()
        search.select("ab ab", "ab")
        search.select("ab ab", "ab", 1)
        assertEquals(1, search.select(" ab", "ab", 1))
        assertNull(search.select(" ab", "missing"))
    }
}
