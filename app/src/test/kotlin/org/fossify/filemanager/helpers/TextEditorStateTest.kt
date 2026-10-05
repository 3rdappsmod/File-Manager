package org.fossify.filemanager.helpers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextEditorStateTest {
    @Test
    fun loadingCannotOverwriteOrTreatTheBlankViewAsAnEdit() {
        val state = TextEditorState()
        assertFalse(state.isReady)
        assertFalse(state.canOverwriteOriginal)
        assertFalse(state.hasChanges(""))
    }

    @Test
    fun successfullyLoadedEmptyFileIsDifferentFromReadFailure() {
        val state = TextEditorState()
        state.loaded("")
        assertTrue(state.canOverwriteOriginal)
        assertFalse(state.hasChanges(""))
        assertTrue(state.hasChanges("new text"))
    }

    @Test
    fun recoveredEmptyDraftMustBeSavedEvenWithoutAReadableOriginal() {
        val state = TextEditorState()
        state.recoverDraft()
        assertTrue(state.isReady)
        assertTrue(state.hasChanges(""))
        assertTrue(state.hasChanges("recovered edit"))
        assertFalse(state.canOverwriteOriginal)
    }

    @Test
    fun savingASnapshotDoesNotClearEditsMadeDuringTheWrite() {
        val state = TextEditorState()
        state.loaded("original")
        state.saved("first edit")
        assertFalse(state.hasChanges("first edit"))
        assertTrue(state.hasChanges("second edit"))
    }

    @Test
    fun returningToOriginalContentClearsTheDirtyState() {
        val state = TextEditorState()
        state.loaded("original")
        assertTrue(state.hasChanges("edited"))
        assertFalse(state.hasChanges("original"))
    }
}
