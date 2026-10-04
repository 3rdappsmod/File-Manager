package org.fossify.filemanager.helpers

import java.util.Locale
import org.fossify.filemanager.R

enum class DefaultAppFeature(val key: String, val title: Int, val mimeType: String) {
    TEXT("text", R.string.feature_default_text, "text/*"),
    IMAGE("image", R.string.feature_default_image, "image/*"),
    PDF("pdf", R.string.feature_default_pdf, "application/pdf"),
    AUDIO("audio", R.string.feature_default_audio, "audio/*"),
    VIDEO("video", R.string.feature_default_video, "video/*");

    val preferenceKey get() = "feature_default_app_$key"

    companion object {
        const val BUILT_IN_AUDIO = "builtin:audio"

        fun fromMimeType(mimeType: String): DefaultAppFeature? {
            val mime = mimeType.substringBefore(';').trim().lowercase(Locale.ROOT)
            return when {
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
