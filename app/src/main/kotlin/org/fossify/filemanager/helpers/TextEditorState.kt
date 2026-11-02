package org.fossify.filemanager.helpers

/** A recovered draft is editable, but an unread original must never be overwritten implicitly. */
internal class TextEditorState {
    var isReady = false
        private set
    private var original: String? = null
    val canOverwriteOriginal get() = isReady && original != null

    fun loaded(text: String) {
        original = text
        isReady = true
    }

    fun recoverDraft() {
        original = null
        isReady = true
    }

    fun hasChanges(text: String): Boolean = isReady && (original == null || original != text)

    fun saved(text: String) {
        original = text
    }
}
