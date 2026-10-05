package org.fossify.filemanager.helpers

import java.util.Locale

/** File names and MIME aliases share one registry for opening files and discovering default apps. */
internal object DocumentMimeTypes {
    private data class Format(val feature: DefaultAppFeature, val mime: String, val extensions: List<String>)

    private fun format(feature: DefaultAppFeature, mime: String, vararg extensions: String) =
        Format(feature, mime, extensions.toList())

    private val formats = listOf(
        format(DefaultAppFeature.PDF, "application/pdf", "pdf"),
        format(DefaultAppFeature.WORD, "application/msword", "doc", "dot"),
        format(
            DefaultAppFeature.WORD,
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"
        ),
        format(
            DefaultAppFeature.WORD,
            "application/vnd.openxmlformats-officedocument.wordprocessingml.template", "dotx"
        ),
        format(DefaultAppFeature.WORD, "application/vnd.ms-word.document.macroenabled.12", "docm"),
        format(DefaultAppFeature.WORD, "application/vnd.ms-word.template.macroenabled.12", "dotm"),
        format(DefaultAppFeature.WORD, "application/vnd.oasis.opendocument.text", "odt"),
        format(DefaultAppFeature.WORD, "application/vnd.oasis.opendocument.text-template", "ott"),
        format(DefaultAppFeature.WORD, "application/rtf", "rtf"),
        format(DefaultAppFeature.WORD, "text/rtf"),
        format(DefaultAppFeature.HANGUL, "application/x-hwp", "hwp"),
        format(DefaultAppFeature.HANGUL, "application/haansofthwp"),
        format(DefaultAppFeature.HANGUL, "application/vnd.hancom.hwp"),
        format(DefaultAppFeature.HANGUL, "application/hwp+zip", "hwpx"),
        format(DefaultAppFeature.HANGUL, "application/vnd.hancom.hwpx"),
        format(DefaultAppFeature.PRESENTATION, "application/vnd.ms-powerpoint", "ppt", "pot", "pps"),
        format(
            DefaultAppFeature.PRESENTATION,
            "application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx"
        ),
        format(
            DefaultAppFeature.PRESENTATION,
            "application/vnd.openxmlformats-officedocument.presentationml.template", "potx"
        ),
        format(
            DefaultAppFeature.PRESENTATION,
            "application/vnd.openxmlformats-officedocument.presentationml.slideshow", "ppsx"
        ),
        format(DefaultAppFeature.PRESENTATION, "application/vnd.ms-powerpoint.presentation.macroenabled.12", "pptm"),
        format(DefaultAppFeature.PRESENTATION, "application/vnd.oasis.opendocument.presentation", "odp"),
        format(DefaultAppFeature.PRESENTATION, "application/vnd.oasis.opendocument.presentation-template", "otp"),
        format(DefaultAppFeature.WEB, "text/html", "html", "htm"),
        format(DefaultAppFeature.WEB, "application/xhtml+xml", "xhtml"),
        format(DefaultAppFeature.MARKDOWN, "text/markdown", "md", "markdown", "mdown", "mkd"),
        format(DefaultAppFeature.MARKDOWN, "text/x-markdown"),
        format(DefaultAppFeature.CSV, "text/csv", "csv"),
        format(DefaultAppFeature.CSV, "text/tab-separated-values", "tsv"),
        format(DefaultAppFeature.CSV, "application/csv"),
        format(DefaultAppFeature.CSV, "text/comma-separated-values"),
        format(DefaultAppFeature.EBOOK, "application/epub+zip", "epub"),
        format(DefaultAppFeature.EBOOK, "application/x-fictionbook+xml", "fb2"),
        format(DefaultAppFeature.TEXT, "application/json", "json"),
        format(DefaultAppFeature.TEXT, "application/xml", "xml"),
        format(DefaultAppFeature.TEXT, "application/yaml", "yaml", "yml"),
        format(DefaultAppFeature.TEXT, "application/x-yaml"),
        format(DefaultAppFeature.TEXT, "text/yaml"),
        format(DefaultAppFeature.TEXT, "text/x-yaml"),
        format(DefaultAppFeature.TEXT, "application/toml", "toml"),
        format(
            DefaultAppFeature.TEXT, "text/plain", "txt", "log", "ini", "conf", "cfg", "properties", "srt"
        ),
        format(DefaultAppFeature.TEXT, "text/calendar", "ics"),
        format(DefaultAppFeature.TEXT, "text/vcard", "vcf"),
        format(DefaultAppFeature.TEXT, "application/x-subrip")
    )

    private val byExtension = formats.flatMap { format -> format.extensions.map { it to format.mime } }.toMap()
    private val byMime = formats.associate { it.mime to it.feature }

    fun fromFilename(path: String): String? =
        byExtension[path.substringAfterLast('/').substringAfterLast('.', "").lowercase(Locale.ROOT)]

    fun featureFor(mime: String): DefaultAppFeature? = byMime[mime]

    fun forFeature(feature: DefaultAppFeature): List<String> = formats.filter { it.feature == feature }.map { it.mime }

    fun resolveMimeType(reported: String, filename: String): String {
        val normalized = DefaultAppFeature.normalizeMimeType(reported)
        return if (normalized in listOf("", "*/*", "application/octet-stream")) {
            DefaultAppFeature.fileMimeType(filename) ?: normalized
        } else {
            normalized
        }
    }
}
