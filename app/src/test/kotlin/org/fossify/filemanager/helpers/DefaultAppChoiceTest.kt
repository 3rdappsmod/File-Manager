package org.fossify.filemanager.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultAppChoiceTest {
    @Test
    fun savedEditorStillMatchesWhenAnotherMimeUsesView() {
        val saved = "android.intent.action.EDIT|example.app/example.app.Editor"
        val viewer = "android.intent.action.VIEW|example.app/example.app.Editor"
        assertTrue(DefaultAppChoice.matches(saved, viewer))
        assertTrue(DefaultAppChoice.matches(viewer, saved))
        assertEquals(1, listOf(saved, viewer).distinctBy(DefaultAppChoice::key).size)
    }

    @Test
    fun differentComponentsAndBuiltInAudioRemainIndependent() {
        val first = "android.intent.action.VIEW|example.app/example.app.Reader"
        val second = "android.intent.action.VIEW|example.app/example.app.Editor"
        assertFalse(DefaultAppChoice.matches(first, second))
        assertFalse(DefaultAppChoice.matches(DefaultAppFeature.BUILT_IN_AUDIO, first))
        assertTrue(DefaultAppChoice.matches(DefaultAppFeature.BUILT_IN_AUDIO, DefaultAppFeature.BUILT_IN_AUDIO))
    }

    @Test
    fun resetNeverSelectsATargetEvenIfItsIdIsEmpty() {
        assertFalse(DefaultAppChoice.matches("", ""))
        assertFalse(DefaultAppChoice.matches("", "android.intent.action.VIEW|example.app/example.app.Reader"))
    }
}
