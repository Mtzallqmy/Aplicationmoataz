package ai.alaser.core.filesystem

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.stream.Collectors
import java.util.zip.ZipInputStream

data class WorkspaceFileEntry(
    val path: String,
    val name: String,
    val directory: Boolean,
    val size: Long,
    val modifiedAt: Long,
)

class WorkspaceAccessException(message: String) : SecurityException(message)

class WorkspaceFileSystem(root: File) {
    private val rootPath: Path = root.toPath().toAbsolutePath().normalize().also {
        Files.createDirectories(it)
    }.toRealPath()

    fun root(): File = rootPath.toFile()

    fun resolve(relativePath: String, allowSensitive: Boolean = false): Path {
        if (relativePath.indexOf('\u0000') >= 0) {
            throw WorkspaceAccessException("NUL bytes are not valid file paths.")
        }
        val requested = Path.of(relativePath)
        if (requested.isAbsolute) {
            throw WorkspaceAccessException("Absolute paths are not allowed.")
        }
        val candidate = rootPath.resolve(requested).normalize()
        if (!candidate.startsWith(rootPath)) {
            throw WorkspaceAccessException("The requested path escapes its workspace.")
        }
        validateExistingAncestors(candidate)
        if (!allowSensitive && isSensitive(candidate.fileName?.toString().orEmpty())) {
            throw WorkspaceAccessException("Sensitive files require explicit approval.")
        }
        return candidate
    }

    suspend fun readText(path: String, maxBytes: Long = 1_000_000): String = withContext(Dispatchers.IO) {
        val resolved = resolve(path)
        val size = Files.size(resolved)
        require(size <= maxBytes) { "The file exceeds the maximum inline size." }
        val bytes = Files.readAllBytes(resolved)
        require(bytes.take(4096).none { it == 0.toByte() }) { "Binary files cannot be read as text." }
        bytes.toString(Charsets.UTF_8)
    }

    suspend fun writeText(path: String, content: String): WorkspaceFileEntry = withContext(Dispatchers.IO) {
        val resolved = resolve(path)
        Files.createDirectories(resolved.parent)
        validateExistingAncestors(resolved)
        Files.writeString(resolved, content)
        entry(resolved)
    }

    suspend fun list(path: String = "."): List<WorkspaceFileEntry> = withContext(Dispatchers.IO) {
        Files.list(resolve(path)).use { stream ->
            stream.map(::entry).sorted(
                compareBy<WorkspaceFileEntry> { !it.directory }.thenBy { it.name.lowercase() },
            ).collect(Collectors.toList())
        }
    }

    suspend fun createDirectory(path: String): WorkspaceFileEntry = withContext(Dispatchers.IO) {
        val resolved = resolve(path)
        Files.createDirectories(resolved)
        entry(resolved)
    }

    suspend fun delete(path: String): Boolean = withContext(Dispatchers.IO) {
        val resolved = resolve(path)
        require(resolved != rootPath) { "Deleting the workspace root is not allowed." }
        Files.deleteIfExists(resolved)
    }

    suspend fun move(source: String, destination: String): WorkspaceFileEntry = withContext(Dispatchers.IO) {
        val original = resolve(source)
        val target = resolve(destination)
        require(original != rootPath && target != rootPath) { "The workspace root cannot be moved." }
        require(!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) { "The destination already exists." }
        Files.createDirectories(target.parent)
        validateExistingAncestors(target)
        Files.move(original, target)
        entry(target)
    }

    suspend fun copy(source: String, destination: String): WorkspaceFileEntry = withContext(Dispatchers.IO) {
        val original = resolve(source)
        val target = resolve(destination)
        require(Files.isRegularFile(original, LinkOption.NOFOLLOW_LINKS)) { "Only regular files can be copied." }
        require(!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) { "The destination already exists." }
        Files.createDirectories(target.parent)
        validateExistingAncestors(target)
        Files.copy(original, target)
        entry(target)
    }

    suspend fun replaceText(path: String, oldText: String, newText: String): WorkspaceFileEntry {
        require(oldText.isNotEmpty()) { "Replacement search text cannot be empty." }
        val original = readText(path)
        require(original.contains(oldText)) { "The requested text was not found in the file." }
        return writeText(path, original.replace(oldText, newText))
    }

    suspend fun search(query: String, limit: Int = 100): List<WorkspaceFileEntry> = withContext(Dispatchers.IO) {
        require(query.isNotBlank()) { "A search term is required." }
        Files.walk(rootPath).use { stream ->
            stream.filter { path ->
                path != rootPath &&
                    rootPath.relativize(path).none { segment -> segment.toString() in IGNORED_DIRECTORIES } &&
                    path.fileName.toString().contains(query, ignoreCase = true)
            }.limit(limit.toLong()).map(::entry).collect(Collectors.toList())
        }
    }

    suspend fun extractZip(archive: File, destination: String): Int = withContext(Dispatchers.IO) {
        val targetRoot = resolve(destination)
        Files.createDirectories(targetRoot)
        var count = 0
        ZipInputStream(archive.inputStream().buffered()).use { input ->
            while (true) {
                val zipEntry = input.nextEntry ?: break
                val destinationPath = targetRoot.resolve(zipEntry.name).normalize()
                if (!destinationPath.startsWith(targetRoot)) {
                    throw WorkspaceAccessException("Archive entry escapes the extraction directory.")
                }
                validateExistingAncestors(destinationPath)
                if (zipEntry.isDirectory) {
                    Files.createDirectories(destinationPath)
                } else {
                    Files.createDirectories(destinationPath.parent)
                    validateExistingAncestors(destinationPath)
                    Files.newOutputStream(destinationPath).use { output -> input.copyTo(output) }
                }
                count++
                input.closeEntry()
            }
        }
        count
    }

    private fun validateExistingAncestors(candidate: Path) {
        var cursor = candidate
        while (cursor.startsWith(rootPath) && cursor != rootPath) {
            if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(cursor)) {
                val target = cursor.toRealPath()
                if (!target.startsWith(rootPath)) {
                    throw WorkspaceAccessException("A symbolic link escapes the workspace.")
                }
            }
            cursor = cursor.parent ?: break
        }
    }

    private fun entry(path: Path): WorkspaceFileEntry = WorkspaceFileEntry(
        path = rootPath.relativize(path).toString(),
        name = path.fileName.toString(),
        directory = Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS),
        size = if (Files.isRegularFile(path)) Files.size(path) else 0,
        modifiedAt = Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toMillis(),
    )

    companion object {
        val IGNORED_DIRECTORIES = setOf(
            ".git", "node_modules", "build", "dist", ".gradle", ".idea", ".next", "target", "vendor",
        )

        fun isSensitive(name: String): Boolean =
            name == ".env" ||
                name.startsWith(".env.") ||
                name.endsWith(".pem") ||
                name.endsWith(".key") ||
                name == "id_rsa" ||
                name == "id_ed25519"
    }
}
