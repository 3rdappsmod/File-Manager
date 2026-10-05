package org.fossify.filemanager.helpers

import java.util.Locale
import org.fossify.filemanager.R

enum class DefaultAppFeature(val key: String, val title: Int, val mimeType: String) {
    TEXT("text", R.string.feature_default_text, "text/*"),
    IMAGE("image", R.string.feature_default_image, "image/*"),
    PDF("pdf", R.string.feature_default_pdf, "application/pdf"),
    SPREADSHEET("spreadsheet", R.string.feature_default_spreadsheet, "application/vnd.ms-excel"),
    WORD(
        "word", R.string.feature_default_word,
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    ),
    HANGUL("hangul", R.string.feature_default_hangul, "application/x-hwp"),
    PRESENTATION("presentation", R.string.feature_default_presentation, "application/vnd.ms-powerpoint"),
    WEB("web", R.string.feature_default_web, "text/html"),
    MARKDOWN("markdown", R.string.feature_default_markdown, "text/markdown"),
    CSV("csv", R.string.feature_default_csv, "text/csv"),
    EBOOK("ebook", R.string.feature_default_ebook, "application/epub+zip"),
    APK("apk", R.string.feature_default_apk, "application/vnd.android.package-archive"),
    AUDIO("audio", R.string.feature_default_audio, "audio/*"),
    VIDEO("video", R.string.feature_default_video, "video/*");

    val discoveryMimeTypes: List<String>
        get() = when (this) {
            SPREADSHEET -> spreadsheetTypes.values.distinct()
            AUDIO -> listOf(mimeType) + audioAliases
            else -> (listOf(mimeType) + DocumentMimeTypes.forFeature(this)).distinct()
        }

    // These functions are editors even when the receiving app exposes only ACTION_VIEW.
    val editsDocuments get() = this == TEXT || this == MARKDOWN || this == CSV

    val preferenceKey get() = "feature_default_app_$key"

    companion object {
        const val BUILT_IN_AUDIO = "builtin:audio"

        private val audioAliases = listOf("application/ogg", "application/x-ogg", "application/flac")

        private val spreadsheetTypes = mapOf(
            "xls" to "application/vnd.ms-excel",
            "xlt" to "application/vnd.ms-excel",
            "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "xltx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.template",
            "xlsm" to "application/vnd.ms-excel.sheet.macroenabled.12",
            "xlsb" to "application/vnd.ms-excel.sheet.binary.macroenabled.12",
            "xltm" to "application/vnd.ms-excel.template.macroenabled.12",
            "ods" to "application/vnd.oasis.opendocument.spreadsheet",
            "ots" to "application/vnd.oasis.opendocument.spreadsheet-template"
        )

        fun fileMimeType(path: String): String? =
            if (path.endsWith(".apk", ignoreCase = true)) APK.mimeType
            else spreadsheetMimeType(path) ?: DocumentMimeTypes.fromFilename(path)

        fun spreadsheetMimeType(path: String): String? =
            spreadsheetTypes[path.substringAfterLast('/').substringAfterLast('.', "").lowercase(Locale.ROOT)]

        fun normalizeMimeType(mimeType: String): String =
            mimeType.substringBefore(';').trim().lowercase(Locale.ROOT)

        fun fromMimeType(mimeType: String): DefaultAppFeature? {
            val mime = normalizeMimeType(mimeType)
            return DocumentMimeTypes.featureFor(mime) ?: when {
                mime == APK.mimeType -> APK
                mime in spreadsheetTypes.values -> SPREADSHEET
                mime.startsWith("text/") || mime == "application/json" || mime == "application/xml" -> TEXT
                mime.startsWith("image/") -> IMAGE
                mime == "application/pdf" -> PDF
                mime.startsWith("audio/") || mime in audioAliases -> AUDIO
                mime.startsWith("video/") -> VIDEO
                else -> null
            }
        }

        fun savedChoice(feature: DefaultAppFeature, stored: String?, legacyBuiltInAudio: Boolean): String =
            stored ?: if (feature == AUDIO && legacyBuiltInAudio) BUILT_IN_AUDIO else ""
    }
}
