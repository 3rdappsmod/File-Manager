package org.fossify.filemanager.helpers

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DirectoryWatchTreeTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun includesNestedAndHiddenDirectoriesButNotFiles() {
        val root = temporary.newFolder("root")
        val child = File(root, "child").apply { mkdir() }
        val nested = File(child, "nested").apply { mkdir() }
        val hidden = File(root, ".hidden").apply { mkdir() }
        File(child, "track.mp3").writeText("audio")
        assertEquals(setOf(root, child, nested, hidden), directoryWatchTree(root).toSet())
    }

    @Test
    fun tracksCreatedMovedAndRemovedSubtrees() {
        val root = temporary.newFolder("root")
        val old = File(root, "old").apply { mkdir() }
        File(old, "nested").mkdir()
        assertEquals(3, directoryWatchTree(root).size)
        val moved = File(root, "moved")
        check(old.renameTo(moved))
        assertEquals(setOf(root, moved, File(moved, "nested")), directoryWatchTree(root).toSet())
        check(moved.deleteRecursively())
        assertEquals(listOf(root), directoryWatchTree(root))
    }

    @Test
    fun skipsSymlinkCyclesAndExternalSubtrees() {
        val root = temporary.newFolder("root")
        val outside = temporary.newFolder("outside")
        Files.createSymbolicLink(File(root, "loop").toPath(), root.toPath())
        Files.createSymbolicLink(File(root, "external").toPath(), outside.toPath())
        assertEquals(listOf(root), directoryWatchTree(root))
    }

    @Test
    fun acceptsAnAliasedRootAndAnEmptyOrMissingRoot() {
        val root = temporary.newFolder("root")
        val alias = File(temporary.root, "alias")
        Files.createSymbolicLink(alias.toPath(), root.toPath())
        assertEquals(listOf(alias), directoryWatchTree(alias))
        check(root.delete())
        assertEquals(emptyList<File>(), directoryWatchTree(alias))
    }
}
