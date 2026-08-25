package ai.alaser.core.filesystem

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class WorkspaceFileSystemTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun rejectsParentTraversal() {
        val filesystem = WorkspaceFileSystem(temporary.newFolder("workspace"))
        assertThrows(WorkspaceAccessException::class.java) { filesystem.resolve("../secret") }
    }

    @Test
    fun rejectsAbsolutePaths() {
        val filesystem = WorkspaceFileSystem(temporary.newFolder("workspace"))
        assertThrows(WorkspaceAccessException::class.java) { filesystem.resolve("/etc/passwd") }
    }

    @Test
    fun rejectsSensitiveFilesWithoutApproval() {
        val filesystem = WorkspaceFileSystem(temporary.newFolder("workspace"))
        assertThrows(WorkspaceAccessException::class.java) { filesystem.resolve(".env") }
    }

    @Test
    fun rejectsEscapingSymbolicLinks() {
        val root = temporary.newFolder("workspace")
        val external = temporary.newFolder("external")
        Files.createSymbolicLink(File(root, "escape").toPath(), external.toPath())
        val filesystem = WorkspaceFileSystem(root)
        assertThrows(WorkspaceAccessException::class.java) { filesystem.resolve("escape/secret.txt") }
    }

    @Test
    fun readsAndWritesUnicodeText() = runBlocking {
        val filesystem = WorkspaceFileSystem(temporary.newFolder("workspace"))
        filesystem.writeText("src/hello.txt", "مرحبا Alaser")
        assertEquals("مرحبا Alaser", filesystem.readText("src/hello.txt"))
        assertTrue(filesystem.search("hello").isNotEmpty())
    }

    @Test
    fun blocksZipSlipEntries() = runBlocking {
        val root = temporary.newFolder("workspace")
        val archive = temporary.newFile("escape.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("../../escaped.txt"))
            zip.write("blocked".toByteArray())
            zip.closeEntry()
        }
        val filesystem = WorkspaceFileSystem(root)
        runCatching { filesystem.extractZip(archive, "import") }
            .onSuccess { error("Expected an unsafe archive to be rejected.") }
            .onFailure { assertTrue(it is WorkspaceAccessException) }
        Unit
    }
}
