package ai.alaser.core.filesystem

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.Properties
import java.util.UUID

data class WorkspaceCheckpoint(
    val id: String,
    val label: String,
    val createdAt: Long,
    val fileCount: Int,
)

class WorkspaceCheckpointStore(private val workspace: File, private val snapshots: File) {
    suspend fun create(label: String): WorkspaceCheckpoint = withContext(Dispatchers.IO) {
        require(label.isNotBlank()) { "A checkpoint description is required." }
        val createdAt = System.currentTimeMillis()
        val identifier = createdAt.toString() + "-" + UUID.randomUUID().toString().take(8)
        val directory = File(snapshots, identifier).apply { mkdirs() }
        val files = File(directory, "files").apply { mkdirs() }
        val root = workspace.toPath().toRealPath()
        var count = 0
        Files.walk(root).use { entries ->
            entries.filter { include(root, it) }.forEach { source ->
                val target = files.toPath().resolve(root.relativize(source).toString())
                Files.createDirectories(target.parent)
                Files.copy(source, target)
                count++
            }
        }
        Properties().apply {
            setProperty("id", identifier)
            setProperty("label", label)
            setProperty("createdAt", createdAt.toString())
            setProperty("fileCount", count.toString())
        }.also { metadata -> File(directory, "checkpoint.properties").outputStream().use { metadata.store(it, null) } }
        WorkspaceCheckpoint(identifier, label, createdAt, count)
    }

    suspend fun list(): List<WorkspaceCheckpoint> = withContext(Dispatchers.IO) {
        snapshots.listFiles().orEmpty().filter { it.isDirectory }.mapNotNull { directory ->
            val metadata = File(directory, "checkpoint.properties")
            if (!metadata.isFile) return@mapNotNull null
            val values = Properties().apply { metadata.inputStream().use { load(it) } }
            WorkspaceCheckpoint(
                id = values.getProperty("id"),
                label = values.getProperty("label"),
                createdAt = values.getProperty("createdAt").toLong(),
                fileCount = values.getProperty("fileCount").toInt(),
            )
        }.sortedByDescending { it.createdAt }
    }

    suspend fun restore(identifier: String) = withContext(Dispatchers.IO) {
        require(identifier.matches(Regex("[0-9]+-[a-f0-9]+"))) { "Invalid checkpoint identifier." }
        val checkpoint = File(snapshots, identifier)
        val storedRoot = File(checkpoint, "files")
        require(storedRoot.isDirectory) { "The requested checkpoint does not exist." }
        val root = workspace.toPath().toRealPath()

        Files.walk(root).use { entries ->
            entries.filter { include(root, it) }
                .filter { !Files.exists(storedRoot.toPath().resolve(root.relativize(it).toString())) }
                .forEach { Files.delete(it) }
        }
        Files.walk(storedRoot.toPath()).use { entries ->
            entries.filter { Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) }.forEach { source ->
                val destination = root.resolve(storedRoot.toPath().relativize(source).toString()).normalize()
                require(destination.startsWith(root)) { "Checkpoint restoration escaped the workspace." }
                Files.createDirectories(destination.parent)
                Files.copy(source, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    private fun include(root: Path, candidate: Path): Boolean {
        if (!Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) return false
        val relative = root.relativize(candidate)
        if (relative.any { it.toString() in WorkspaceFileSystem.IGNORED_DIRECTORIES }) return false
        return !WorkspaceFileSystem.isSensitive(candidate.fileName.toString())
    }
}
