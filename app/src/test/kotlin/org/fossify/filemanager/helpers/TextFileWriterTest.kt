package org.fossify.filemanager.helpers

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TextFileWriterTest {
    @Test
    fun savesUtf8AndClosesBeforeReturning() {
        var closed = false
        val output = object : ByteArrayOutputStream() {
            override fun close() { closed = true }
        }
        writeEditedText({ output }, "한글 문서\nsecond line")
        assertEquals("한글 문서\nsecond line", output.toByteArray().toString(Charsets.UTF_8))
        assertTrue(closed)
    }

    @Test
    fun nullProviderStreamIsNotSuccessful() {
        assertThrows(IOException::class.java) { writeEditedText({ null }, "unsaved") }
    }

    @Test
    fun readOnlyGrantDoesNotBecomeSuccessfulSave() {
        assertThrows(SecurityException::class.java) {
            writeEditedText({ throw SecurityException("Read-only document") }, "unsaved")
        }
    }

    @Test
    fun writeFailureClosesTheStreamAndReportsFailure() {
        var closed = false
        val output = object : OutputStream() {
            override fun write(value: Int) { throw IOException("Disk full") }
            override fun close() { closed = true }
        }
        assertThrows(IOException::class.java) { writeEditedText({ output }, "unsaved") }
        assertTrue(closed)
    }

    @Test
    fun providerCloseFailureIsNotSuccessful() {
        val output = object : ByteArrayOutputStream() {
            override fun close() { throw IOException("Provider commit failed") }
        }
        assertThrows(IOException::class.java) { writeEditedText({ output }, "unsaved") }
    }
}
