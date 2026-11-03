package org.fossify.filemanager.helpers.packages

import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/** The APK+ container used by backups: base.apk, split APKs, apk+.json and optional icon.png. */
object ApkBundle {
    const val MAX_BYTES = 8L * 1024 * 1024 * 1024
    private const val MAX_ENTRIES = 512
    private val apkName = Regex("[A-Za-z0-9_][A-Za-z0-9_.-]*\\.apk")

    fun entries(zip: ZipFile): List<ZipEntry> {
        val all = zip.entries().asSequence().take(MAX_ENTRIES + 1).toList()
        require(all.size <= MAX_ENTRIES) { "Too many entries in APK+ archive" }
        require(all.map { it.name }.distinct().size == all.size) { "Duplicate archive entries" }
        require(all.all {
            !it.isDirectory && (apkName.matches(it.name) || it.name in setOf("apk+.json", "icon.png"))
        }) {
            "Invalid APK+ archive entry"
        }
        val apks = all.filter { apkName.matches(it.name) }
        require(apks.any { it.name == "base.apk" }) { "APK+ archive has no base.apk" }
        var total = 0L
        apks.forEach {
            require(it.size in 1..MAX_BYTES) { "Invalid APK size" }
            require(it.size <= MAX_BYTES - total) { "APK+ archive is too large" }
            total += it.size
        }
        return apks.sortedBy { if (it.name == "base.apk") "" else it.name }
    }

    fun copy(input: InputStream, output: OutputStream, limit: Long = MAX_BYTES): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Operation cancelled")
            val count = input.read(buffer)
            if (count < 0) return total
            if (count.toLong() > limit - total) throw IOException("APK exceeds the size limit")
            output.write(buffer, 0, count)
            total += count
        }
    }

    fun fileName(label: String, version: String, split: Boolean): String {
        val name = "$label $version".replace(Regex("[\\p{Cntrl}/\\\\:*?\"<>|]"), "_").trim()
        val bounded = StringBuilder()
        var bytes = 0
        for (point in name.codePoints().toArray()) {
            val text = String(Character.toChars(point))
            val size = text.toByteArray(Charsets.UTF_8).size
            if (bytes + size > NAME_BYTES) break
            bounded.append(text)
            bytes += size
        }
        return bounded.toString().ifBlank { "application" } + if (split) ".apk+" else ".apk"
    }

    private const val NAME_BYTES = 180
}
