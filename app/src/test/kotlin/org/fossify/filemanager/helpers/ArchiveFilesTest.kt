package org.fossify.filemanager.helpers

import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ArchiveFilesTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun acceptsNestedEntriesAndDirectoryMarkers() {
        val root = temporary.newFolder("archive")
        assertEquals(File(root, "nested/file.txt"), resolveArchiveEntry(root, "nested/file.txt"))
        assertEquals(File(root, "nested"), resolveArchiveEntry(root, "nested/"))
    }

    @Test
    fun rejectsTraversalIncludingSiblingPrefixBypass() {
        val root = temporary.newFolder("archive")
        listOf("../escape.txt", "../archive_other/file.txt", "a/../../file.txt", "..\\escape.txt").forEach { name ->
            assertThrows(IOException::class.java) { resolveArchiveEntry(root, name) }
        }
    }

    @Test
    fun rejectsAbsoluteAndEmptyTargets() {
        val root = temporary.newFolder("archive")
        listOf("/tmp/file", "C:/file", "", ".", "./").forEach { name ->
            assertThrows(IOException::class.java) { resolveArchiveEntry(root, name) }
        }
    }

    @Test
    fun rejectsExistingSymlinkOutsideDestination() {
        val root = temporary.newFolder("archive")
        val outside = temporary.newFolder("outside")
        Files.createSymbolicLink(File(root, "link").toPath(), outside.toPath())
        assertThrows(IOException::class.java) { resolveArchiveEntry(root, "link/file.txt") }
    }

    @Test
    fun acceptsDestinationReachedThroughAnAlias() {
        val root = temporary.newFolder("archive")
        val alias = File(temporary.root, "alias")
        Files.createSymbolicLink(alias.toPath(), root.toPath())
        assertEquals(File(root, "file.txt"), resolveArchiveEntry(alias, "file.txt").canonicalFile)
    }
    @Test
    fun acceptsAnEmptyDirectory() {
        assertEquals(0, listFilesForArchive(temporary.newFolder("empty")).size)
    }

    @Test
    fun rejectsMissingDirectoryInsteadOfSilentlySkippingIt() {
        assertThrows(IOException::class.java) { listFilesForArchive(File(temporary.root, "missing")) }
    }

    @Test
    fun rejectsDirectoryReplacedByAFile() {
        assertThrows(IOException::class.java) { listFilesForArchive(temporary.newFile("replaced")) }
    }
}
