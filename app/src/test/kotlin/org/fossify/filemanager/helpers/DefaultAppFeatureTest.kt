package org.fossify.filemanager.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultAppFeatureTest {
    @Test
    fun recognizesEachFeatureWithoutDependingOnFilenameCase() {
        val types = mapOf(
            " Text/Plain; charset=UTF-8 " to DefaultAppFeature.TEXT,
            "application/json" to DefaultAppFeature.TEXT,
            "application/xml" to DefaultAppFeature.TEXT,
            "IMAGE/PNG" to DefaultAppFeature.IMAGE,
            "application/pdf" to DefaultAppFeature.PDF,
            "audio/ogg" to DefaultAppFeature.AUDIO,
            "video/mp4" to DefaultAppFeature.VIDEO
        )
        types.forEach { (mime, feature) -> assertEquals(feature, DefaultAppFeature.fromMimeType(mime)) }
    }

    @Test
    fun unsupportedFilesAndOpenAsOtherKeepTheSystemFlow() {
        listOf("", "*/*", "application/zip", "application/octet-stream")
            .forEach { assertNull(DefaultAppFeature.fromMimeType(it)) }
    }

    @Test
    fun oldBuiltInPlayerChoiceMigratesOnlyForAudio() {
        DefaultAppFeature.entries.forEach { feature ->
            val expected = if (feature == DefaultAppFeature.AUDIO) DefaultAppFeature.BUILT_IN_AUDIO else ""
            assertEquals(expected, DefaultAppFeature.savedChoice(feature, null, true))
        }
    }

    @Test
    fun resetDoesNotResurrectTheLegacyBuiltInPlayer() {
        assertEquals("", DefaultAppFeature.savedChoice(DefaultAppFeature.AUDIO, "", true))
    }

    @Test
    fun explicitNewChoiceOverridesTheLegacyPlayer() {
        assertEquals("external", DefaultAppFeature.savedChoice(DefaultAppFeature.AUDIO, "external", true))
    }

    @Test
    fun oldSystemPlayerChoiceKeepsDefaultsUnset() {
        DefaultAppFeature.entries.forEach { feature ->
            assertEquals("", DefaultAppFeature.savedChoice(feature, null, false))
        }
    }
    @Test
    fun spreadsheetFormatsUseTheirOwnDefaultIncludingCsvAndTsv() {
        val extensions = listOf("xls", "xlsx", "xlsm", "xlsb", "xlt", "xltx", "xltm", "ods", "ots", "csv", "tsv")
        extensions.forEach { extension ->
            val mime = requireNotNull(DefaultAppFeature.spreadsheetMimeType("/storage/Report.$extension"))
            assertEquals(DefaultAppFeature.SPREADSHEET, DefaultAppFeature.fromMimeType(mime))
            org.junit.Assert.assertTrue(mime in DefaultAppFeature.SPREADSHEET.discoveryMimeTypes)
        }
        assertEquals(DefaultAppFeature.SPREADSHEET, DefaultAppFeature.fromMimeType(" Text/CSV; charset=UTF-8 "))
        assertEquals(DefaultAppFeature.SPREADSHEET, DefaultAppFeature.fromMimeType("application/csv"))
        assertEquals(DefaultAppFeature.TEXT, DefaultAppFeature.fromMimeType("text/*"))
        assertEquals(DefaultAppFeature.TEXT, DefaultAppFeature.fromMimeType("text/plain"))
    }

    @Test
    fun spreadsheetExtensionMatchingIsCaseInsensitiveAndDoesNotMatchParentNames() {
        assertEquals(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            DefaultAppFeature.spreadsheetMimeType("/storage/REPORT.XLSX")
        )
        assertNull(DefaultAppFeature.spreadsheetMimeType("/storage/folder.xlsx/readme"))
        assertNull(DefaultAppFeature.spreadsheetMimeType("/storage/report.xlsx.pdf"))
    }
    @Test
    fun apkUsesAnIndependentDefaultAndCaseInsensitiveExtension() {
        assertEquals(DefaultAppFeature.APK, DefaultAppFeature.fromMimeType("application/vnd.android.package-archive"))
        assertEquals(DefaultAppFeature.APK.mimeType, DefaultAppFeature.fileMimeType("/storage/App.APK"))
        assertNull(DefaultAppFeature.fileMimeType("/storage/App.apk.txt"))
        assertEquals(DefaultAppFeature.TEXT, DefaultAppFeature.fromMimeType("text/*"))
        assertNull(DefaultAppFeature.fromMimeType("*/*"))
    }
    @Test
    fun handlerQueriesReceiveTheSameNormalizedMimeUsedForClassification() {
        val incoming = " Application/Vnd.Android.Package-Archive; charset=UTF-8 "
        assertEquals(DefaultAppFeature.APK.mimeType, DefaultAppFeature.normalizeMimeType(incoming))
        assertEquals("text/csv", DefaultAppFeature.normalizeMimeType(" Text/CSV; charset=UTF-8 "))
        assertEquals("image/*", DefaultAppFeature.normalizeMimeType("IMAGE/*"))
    }

    @Test
    fun recognizedAliasesAreAlsoIncludedInSettingsDiscovery() {
        val aliases = mapOf(
            "application/csv" to DefaultAppFeature.SPREADSHEET,
            "application/ogg" to DefaultAppFeature.AUDIO,
            "application/x-ogg" to DefaultAppFeature.AUDIO,
            "application/flac" to DefaultAppFeature.AUDIO
        )
        aliases.forEach { (mime, feature) ->
            assertEquals(feature, DefaultAppFeature.fromMimeType(mime))
            org.junit.Assert.assertTrue(mime in feature.discoveryMimeTypes)
        }
    }
}
