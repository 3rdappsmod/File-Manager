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
        listOf("", "*/*", "application/zip", "application/octet-stream", "application/vnd.android.package-archive")
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
}
