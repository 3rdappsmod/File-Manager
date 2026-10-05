package org.fossify.filemanager.helpers

import java.util.Locale
import org.fossify.filemanager.R

enum class DefaultAppFeature(val key: String, val title: Int, val mimeType: String) {
    TEXT("text", R.string.feature_default_text, "text/*"),
    IMAGE("image", R.string.feature_default_image, "image/*"),
    PDF("pdf", R.string.feature_default_pdf, "application/pdf"),
    SPREADSHEET("spreadsheet", R.string.feature_default_spreadsheet, "application/vnd.ms-excel"),
    APK("apk", R.string.feature_default_apk, "application/vnd.android.package-archive"),
    AUDIO("audio", R.string.feature_default_audio, "audio/*"),
    VIDEO("video", R.string.feature_default_video, "video/*");

    val discoveryMimeTypes: List<String>
        get() = if (this == SPREADSHEET) spreadsheetTypes.values.distinct() else listOf(mimeType)

    val preferenceKey get() = "feature_default_app_$key"

    companion object {
        const val BUILT_IN_AUDIO = "builtin:audio"

        private val spreadsheetTypes = mapOf(
            "xls" to "application/vnd.ms-excel",
            "xlt" to "application/vnd.ms-excel",
            "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "xltx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.template",
            "xlsm" to "application/vnd.ms-excel.sheet.macroenabled.12",
            "xlsb" to "application/vnd.ms-excel.sheet.binary.macroenabled.12",
            "xltm" to "application/vnd.ms-excel.template.macroenabled.12",
            "ods" to "application/vnd.oasis.opendocument.spreadsheet",
            "ots" to "application/vnd.oasis.opendocument.spreadsheet-template",
            "csv" to "text/csv",
            "tsv" to "text/tab-separated-values"
        )

        fun fileMimeType(path: String): String? =
            if (path.endsWith(".apk", ignoreCase = true)) APK.mimeType else spreadsheetMimeType(path)

        fun spreadsheetMimeType(path: String): String? =
            spreadsheetTypes[path.substringAfterLast('/').substringAfterLast('.', "").lowercase(Locale.ROOT)]

        fun fromMimeType(mimeType: String): DefaultAppFeature? {
            val mime = mimeType.substringBefore(';').trim().lowercase(Locale.ROOT)
            return when {
                mime == APK.mimeType -> APK
                mime in spreadsheetTypes.values || mime == "application/csv" -> SPREADSHEET
                mime.startsWith("text/") || mime == "application/json" || mime == "application/xml" -> TEXT
                mime.startsWith("image/") -> IMAGE
                mime == "application/pdf" -> PDF
                mime.startsWith("audio/") -> AUDIO
                mime.startsWith("video/") -> VIDEO
                else -> null
            }
        }

        fun savedChoice(feature: DefaultAppFeature, stored: String?, legacyBuiltInAudio: Boolean): String =
            stored ?: if (feature == AUDIO && legacyBuiltInAudio) BUILT_IN_AUDIO else ""
    }
}
