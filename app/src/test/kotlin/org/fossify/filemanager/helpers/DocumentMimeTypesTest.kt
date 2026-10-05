package org.fossify.filemanager.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentMimeTypesTest {
    @Test
    fun documentsFormatsHaveDefaultsForLocalFilesAndProviderUris() {
        val groups = mapOf(
            DefaultAppFeature.PDF to listOf("pdf"),
            DefaultAppFeature.WORD to listOf("doc", "docx", "docm", "odt", "rtf"),
            DefaultAppFeature.HANGUL to listOf("hwp", "hwpx"),
            DefaultAppFeature.PRESENTATION to listOf("ppt", "pptx", "ppsx", "odp"),
            DefaultAppFeature.WEB to listOf("html", "htm", "xhtml"),
            DefaultAppFeature.MARKDOWN to listOf("md", "markdown", "mdown", "mkd"),
            DefaultAppFeature.CSV to listOf("csv", "tsv"),
            DefaultAppFeature.EBOOK to listOf("epub", "fb2"),
            DefaultAppFeature.TEXT to listOf("txt", "json", "xml", "yaml", "yml", "toml", "ini", "srt")
        )
        groups.forEach { (feature, extensions) ->
            extensions.forEach { extension ->
                val filename = "/storage/Example.${extension.uppercase(java.util.Locale.ROOT)}"
                val mime = requireNotNull(DefaultAppFeature.fileMimeType(filename))
                assertEquals(feature, DefaultAppFeature.fromMimeType(mime))
                assertTrue(mime in feature.discoveryMimeTypes)
                assertEquals(mime, DocumentMimeTypes.resolveMimeType("application/octet-stream", filename))
            }
        }
    }

    @Test
    fun specializedTextTypesDoNotFallThroughToTheTextEditor() {
        val aliases = mapOf(
            "text/rtf" to DefaultAppFeature.WORD,
            "text/x-markdown" to DefaultAppFeature.MARKDOWN,
            "text/comma-separated-values" to DefaultAppFeature.CSV,
            "application/csv" to DefaultAppFeature.CSV,
            "application/xhtml+xml" to DefaultAppFeature.WEB,
            "application/x-yaml" to DefaultAppFeature.TEXT,
            "application/vnd.hancom.hwpx" to DefaultAppFeature.HANGUL
        )
        aliases.forEach { (mime, feature) ->
            assertEquals(feature, DefaultAppFeature.fromMimeType(" $mime; charset=UTF-8 "))
            assertTrue(mime in feature.discoveryMimeTypes)
        }
    }

    @Test
    fun fileNamesDoNotOverrideSpecificProviderTypesOrMatchParentDirectories() {
        assertEquals("application/pdf", DocumentMimeTypes.resolveMimeType("application/pdf", "report.docx"))
        assertEquals(
            "application/octet-stream", DocumentMimeTypes.resolveMimeType("application/octet-stream", "no-ext")
        )
        assertEquals("text/markdown", DocumentMimeTypes.resolveMimeType("", "readme.md"))
        assertEquals("text/plain", DefaultAppFeature.fileMimeType("readme.md.txt"))
        assertNull(DefaultAppFeature.fileMimeType("/folder.md/no-extension"))
        assertNull(DefaultAppFeature.fileMimeType("/folder/readme.zip"))
    }

    @Test
    fun onlyEditorFunctionsRequestWriteAccessFromViewOnlyApps() {
        val editors = setOf(DefaultAppFeature.TEXT, DefaultAppFeature.MARKDOWN, DefaultAppFeature.CSV)
        DefaultAppFeature.entries.forEach { assertEquals(it in editors, it.editsDocuments) }
        assertFalse(DefaultAppFeature.WEB.editsDocuments)
        assertFalse(DefaultAppFeature.PDF.editsDocuments)
    }
}
