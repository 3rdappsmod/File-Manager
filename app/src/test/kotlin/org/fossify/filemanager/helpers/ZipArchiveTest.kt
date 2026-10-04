package org.fossify.filemanager.helpers

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ZipArchiveTest {
    private fun archive(): ByteArray {
        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zip ->
            for ((name, text) in listOf("first.txt" to "first data", "second.txt" to "second data")) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
        return buffer.toByteArray()
    }

    @Test
    fun entriesAreConsumedBeforeAdvancingAndSourceIsClosed() {
        var closed = false
        val input = object : ByteArrayInputStream(archive()) {
            override fun close() { closed = true }
        }
        val contents = linkedMapOf<String, String>()
        forEachZipEntry({ input }) { zip, header ->
            contents[header.fileName] = zip.readBytes().decodeToString()
        }
        assertEquals(mapOf("first.txt" to "first data", "second.txt" to "second data"), contents)
        assertTrue(closed)
    }

    @Test
    fun missingInputIsReportedAsFailure() {
        assertThrows(IOException::class.java) { forEachZipEntry({ null }) { _, _ -> } }
    }

    @Test
    fun closeFailureCannotBeReportedAsSuccess() {
        val input = object : ByteArrayInputStream(archive()) {
            override fun close() { throw IOException("close failed") }
        }
        assertThrows(IOException::class.java) { forEachZipEntry({ input }) { _, _ -> } }
    }

    @Test
    fun failedDestinationStopsBeforeTheNextEntryAndClosesInput() {
        var closed = false
        var entries = 0
        val input = object : ByteArrayInputStream(archive()) {
            override fun close() { closed = true }
        }
        assertThrows(IOException::class.java) {
            forEachZipEntry({ input }) { _, _ ->
                entries++
                throw IOException("output failed")
            }
        }
        assertEquals(1, entries)
        assertTrue(closed)
    }
}
