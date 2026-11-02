package org.fossify.filemanager.helpers

/** Only distinct, year-first date styles are offered, regardless of the device locale. */
object LocalDateFormats {
    const val DEFAULT = "yyyy-MM-dd"
    val patterns = listOf(DEFAULT, "yyyy.MM.dd", "yyyy'년' M'월' d'일'")

    fun normalize(pattern: String): String = if (pattern in patterns) pattern else DEFAULT
}
