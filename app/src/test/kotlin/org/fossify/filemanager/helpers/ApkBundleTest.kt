package org.fossify.filemanager.helpers

import org.fossify.filemanager.helpers.packages.ApkBundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class ApkBundleTest {
    @Test fun readsBaseAndSplitsInInstallationOrder() {
        archive("apk+.json", "icon.png", "split_config.arm64_v8a.apk", "base.apk") { zip ->
            assertEquals(listOf("base.apk", "split_config.arm64_v8a.apk"), ApkBundle.entries(zip).map { it.name })
        }
    }

    @Test fun rejectsTraversalAndMissingBase() {
        val invalid = listOf(arrayOf("base.apk", "../evil.apk"), arrayOf("split.apk"), arrayOf("base.apk", "/evil.apk"))
        for (names in invalid) {
            archive(*names) { zip ->
                assertTrue(runCatching { ApkBundle.entries(zip) }.exceptionOrNull() is IllegalArgumentException)
            }
        }
    }

    @Test fun rejectsTooManyEntries() {
        val names = arrayOf("base.apk") + (1..512).map { "split_$it.apk" }
        archive(*names) { zip ->
            assertTrue(runCatching { ApkBundle.entries(zip) }.exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test fun boundedCopyDoesNotWriteBeyondLimit() {
        val output = ByteArrayOutputStream()
        val result = runCatching { ApkBundle.copy(ByteArrayInputStream(ByteArray(32)), output, 8) }
        assertTrue(result.exceptionOrNull() is IOException)
        assertTrue(output.size() <= 8)
    }

    @Test fun copiesAllBytesAtExactLimit() {
        val input = byteArrayOf(1, 2, 3)
        val output = ByteArrayOutputStream()
        assertEquals(3L, ApkBundle.copy(ByteArrayInputStream(input), output, 3))
        assertTrue(input.contentEquals(output.toByteArray()))
    }

    @Test fun sanitizesBackupNamesAndPreservesExtension() {
        val name = ApkBundle.fileName("a/b\\c", "1.0", true)
        assertFalse(name.contains('/'))
        assertFalse(name.contains('\\'))
        assertTrue(name.endsWith(".apk+"))
        assertEquals("App 1.apk", ApkBundle.fileName("App", "1", false))
    }

    @Test fun limitsKoreanAndEmojiNamesByUtf8Bytes() {
        for (label in listOf("한".repeat(120), "😀".repeat(120), "a" + "😀".repeat(120))) {
            val name = ApkBundle.fileName(label, "1.0", true)
            assertTrue(name.toByteArray(Charsets.UTF_8).size <= 185)
            assertTrue(name.endsWith(".apk+"))
            assertFalse(name.contains('\uFFFD'))
            assertEquals(name, String(name.toByteArray(Charsets.UTF_8), Charsets.UTF_8))
        }
    }

    private fun archive(vararg names: String, check: (ZipFile) -> Unit) {
        val file = File.createTempFile("apk-bundle-test", ".zip")
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                names.forEach { name ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(byteArrayOf(1, 2, 3))
                    zip.closeEntry()
                }
            }
            ZipFile(file).use(check)
        } finally {
            file.delete()
        }
    }
}
