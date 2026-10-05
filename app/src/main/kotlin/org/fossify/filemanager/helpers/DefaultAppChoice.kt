package org.fossify.filemanager.helpers

/** A default selects a component, not an action that may differ between supported MIME types. */
internal object DefaultAppChoice {
    fun key(id: String): String = id.substringAfter('|')

    fun matches(saved: String, candidate: String): Boolean = saved.isNotEmpty() && key(saved) == key(candidate)
}
