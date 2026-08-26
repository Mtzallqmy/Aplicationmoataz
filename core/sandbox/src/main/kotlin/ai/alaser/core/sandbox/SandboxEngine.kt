package ai.alaser.core.sandbox

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

enum class SandboxType { NATIVE_ANDROID, PROOT }

data class LinuxEnvironmentDescriptor(
    val id: String,
    val displayName: String,
    val architecture: String,
    val archiveUrl: String,
    val sha256: String,
)

sealed interface EnvironmentInstallEvent {
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long?) : EnvironmentInstallEvent
    data class Verifying(val expectedSha256: String) : EnvironmentInstallEvent
    data class Extracting(val filesExtracted: Int) : EnvironmentInstallEvent
    data class Installed(val directory: File) : EnvironmentInstallEvent
}

interface SandboxBackend {
    val type: SandboxType
    fun available(): Boolean
    fun commandPrefix(workspace: File): List<String>
}

class NativeAndroidShellBackend : SandboxBackend {
    override val type: SandboxType = SandboxType.NATIVE_ANDROID

    override fun available(): Boolean = File("/system/bin/sh").canExecute() || File("/bin/sh").canExecute()

    override fun commandPrefix(workspace: File): List<String> = listOf(
        if (File("/system/bin/sh").canExecute()) "/system/bin/sh" else "/bin/sh",
    )
}

class ProotBackend(
    private val executable: File,
    private val rootfs: File,
) : SandboxBackend {
    override val type: SandboxType = SandboxType.PROOT

    override fun available(): Boolean =
        executable.isFile && executable.canExecute() && File(rootfs, "bin/sh").exists()

    override fun commandPrefix(workspace: File): List<String> {
        check(available()) {
            "A compatible PRoot executable and an installed Linux root filesystem are required."
        }
        return listOf(
            executable.absolutePath,
            "-0",
            "--rootfs=" + rootfs.absolutePath,
            "--bind=" + workspace.absolutePath + ":/workspace",
            "--bind=/dev",
            "--bind=/proc",
            "--bind=/sys",
            "--cwd=/workspace",
            "--link2symlink",
            "/bin/sh",
        )
    }
}

class RootfsInstaller(
    private val environmentsDirectory: File,
    private val client: OkHttpClient = OkHttpClient(),
) {
    // A rootfs is shared process-wide. Serializing installation prevents a
    // second tap or startup job from deleting a directory while it is being
    // extracted by the first job.
    private val installationMutex = Mutex()

    fun install(descriptor: LinuxEnvironmentDescriptor): Flow<EnvironmentInstallEvent> = flow {
        require(descriptor.archiveUrl.startsWith("https://")) {
            "Linux images must be downloaded over HTTPS."
        }
        validateDescriptor(descriptor)
        installationMutex.withLock {
            existingEnvironment(descriptor)?.let {
                emit(EnvironmentInstallEvent.Installed(it))
                return@withLock
            }
            client.newCall(Request.Builder().url(descriptor.archiveUrl).build()).execute().use { response ->
                check(response.isSuccessful) { "Root filesystem download failed with HTTP " + response.code + "." }
                val body = requireNotNull(response.body) { "The image download response was empty." }
                body.byteStream().use { input ->
                    installDownloadedStream(descriptor, input, body.contentLength().takeIf { it >= 0 })
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    fun installBundled(
        descriptor: LinuxEnvironmentDescriptor,
        openArchive: () -> InputStream,
    ): Flow<EnvironmentInstallEvent> = flow {
        validateDescriptor(descriptor)
        installationMutex.withLock {
            existingEnvironment(descriptor)?.let {
                emit(EnvironmentInstallEvent.Installed(it))
                return@withLock
            }
            installBundledArchive(descriptor, openArchive)
        }
    }.flowOn(Dispatchers.IO)

    private fun validateDescriptor(descriptor: LinuxEnvironmentDescriptor) {
        require(descriptor.sha256.matches(Regex("[a-fA-F0-9]{64}"))) {
            "A verified SHA-256 checksum is required before installing a Linux image."
        }
        require(descriptor.id.matches(Regex("[a-zA-Z0-9][a-zA-Z0-9_-]*"))) {
            "The Linux environment identifier contains unsafe characters."
        }
    }

    private fun existingEnvironment(descriptor: LinuxEnvironmentDescriptor): File? {
        val destination = File(environmentsDirectory, descriptor.id)
        if (usableEnvironment(destination, descriptor)) return destination
        if (destination.exists()) {
            check(destination.deleteRecursively()) { "The incomplete Linux environment could not be removed." }
        }
        File(environmentsDirectory, descriptor.id + ".installing").takeIf(File::exists)?.let {
            check(it.deleteRecursively()) { "The stale Linux installation directory could not be removed." }
        }
        File(environmentsDirectory, descriptor.id + ".download").delete()
        return null
    }

    private fun containsShell(directory: File): Boolean =
        directory.isDirectory &&
            (File(directory, "bin/sh").isFile || File(directory, "usr/bin/sh").isFile)

    private fun usableEnvironment(directory: File, descriptor: LinuxEnvironmentDescriptor): Boolean =
        containsShell(directory) &&
            File(directory, COMPLETION_MARKER).takeIf(File::isFile)
                ?.readText()
                ?.trim()
                ?.equals(descriptor.sha256, ignoreCase = true) == true

    private suspend fun FlowCollector<EnvironmentInstallEvent>.installBundledArchive(
        descriptor: LinuxEnvironmentDescriptor,
        openArchive: () -> InputStream,
    ) {
        Files.createDirectories(environmentsDirectory.toPath())
        emit(EnvironmentInstallEvent.Verifying(descriptor.sha256))
        val actual = openArchive().use(::checksum)
        check(actual.equals(descriptor.sha256, ignoreCase = true)) {
            "The bundled Linux image checksum did not match its signed release manifest."
        }
        val staging = File(environmentsDirectory, descriptor.id + ".installing")
        try {
            Files.createDirectories(staging.toPath())
            emit(EnvironmentInstallEvent.Extracting(0))
            val extracted = openArchive().use { extract(it, staging.toPath(), descriptor.archiveUrl) }
            finishInstallation(descriptor, staging)
            emit(EnvironmentInstallEvent.Extracting(extracted))
            emit(EnvironmentInstallEvent.Installed(File(environmentsDirectory, descriptor.id)))
        } catch (exception: Exception) {
            staging.deleteRecursively()
            throw exception
        }
    }

    private suspend fun FlowCollector<EnvironmentInstallEvent>.installDownloadedStream(
        descriptor: LinuxEnvironmentDescriptor,
        input: InputStream,
        total: Long?,
    ) {
        Files.createDirectories(environmentsDirectory.toPath())
        val temporaryArchive = File(environmentsDirectory, descriptor.id + ".download")
        val staging = File(environmentsDirectory, descriptor.id + ".installing")

        try {
            temporaryArchive.outputStream().buffered().use { output ->
                val buffer = ByteArray(64 * 1024)
                var downloaded = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    emit(EnvironmentInstallEvent.Downloading(downloaded, total))
                }
            }

            emit(EnvironmentInstallEvent.Verifying(descriptor.sha256))
            val actual = checksum(temporaryArchive)
            check(actual.equals(descriptor.sha256, ignoreCase = true)) {
                "The Linux image checksum did not match the expected SHA-256."
            }
            Files.createDirectories(staging.toPath())
            emit(EnvironmentInstallEvent.Extracting(0))
            val extracted = temporaryArchive.inputStream().buffered().use {
                extract(it, staging.toPath(), descriptor.archiveUrl)
            }
            finishInstallation(descriptor, staging)
            emit(EnvironmentInstallEvent.Extracting(extracted))
            emit(EnvironmentInstallEvent.Installed(File(environmentsDirectory, descriptor.id)))
        } catch (exception: Exception) {
            staging.deleteRecursively()
            throw exception
        } finally {
            temporaryArchive.delete()
        }
    }

    private fun finishInstallation(descriptor: LinuxEnvironmentDescriptor, staging: File) {
        check(containsShell(staging)) { "The extracted image does not contain a usable Linux shell." }
        File(staging, COMPLETION_MARKER).writeText(descriptor.sha256.lowercase())
        val destination = File(environmentsDirectory, descriptor.id)
        check(!destination.exists()) { "A completed Linux environment already exists." }
        check(staging.renameTo(destination)) { "The verified Linux environment could not be activated atomically." }
    }

    private fun extract(archive: InputStream, destination: Path, url: String): Int =
        if (url.substringBefore('?').endsWith(".zip")) extractZip(archive, destination)
        else extractTarGzip(archive, destination)

    private fun extractZip(archive: InputStream, destination: Path): Int {
        var count = 0
        val root = destination.toAbsolutePath().normalize()
        ZipInputStream(archive.buffered()).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                val path = lexicalDestination(root, entry.name)
                if (entry.isDirectory) {
                    Files.createDirectories(path)
                } else {
                    Files.createDirectories(path.parent)
                    removeExistingLeaf(path)
                    Files.newOutputStream(path).use { input.copyTo(it) }
                }
                count++
            }
        }
        return count
    }

    private data class PendingHardLink(val path: Path, val target: Path)

    private fun extractTarGzip(archive: InputStream, destination: Path): Int {
        var count = 0
        val root = destination.toAbsolutePath().normalize()
        val pendingHardLinks = mutableListOf<PendingHardLink>()

        TarArchiveInputStream(GZIPInputStream(archive.buffered())).use { input ->
            while (true) {
                val entry = input.nextTarEntry ?: break
                val lexicalPath = lexicalDestination(root, entry.name)
                val path = resolveTarDestination(root, lexicalPath)
                when {
                    entry.isDirectory -> {
                        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) &&
                            !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                        ) {
                            removeExistingLeaf(path)
                        }
                        Files.createDirectories(path)
                    }
                    entry.isSymbolicLink -> {
                        val logicalTarget = symbolicLinkTarget(root, lexicalPath, entry.linkName)
                        Files.createDirectories(path.parent)
                        removeExistingLeaf(path)
                        val relativeTarget = path.parent.relativize(logicalTarget)
                        Files.createSymbolicLink(path, relativeTarget)
                    }
                    entry.isLink -> {
                        val lexicalTarget = hardLinkTarget(root, entry.linkName)
                        Files.createDirectories(path.parent)
                        if (!createHardLinkIfReady(root, path, lexicalTarget)) {
                            pendingHardLinks += PendingHardLink(path, lexicalTarget)
                        }
                    }
                    entry.isFile -> {
                        Files.createDirectories(path.parent)
                        removeExistingLeaf(path)
                        Files.newOutputStream(path).use { input.copyTo(it) }
                        if ((entry.mode and 0b001001001) != 0) {
                            check(path.toFile().setExecutable(true, false) || path.toFile().canExecute()) {
                                "An executable Linux file could not be marked executable: " + entry.name
                            }
                        }
                    }
                }
                count++
            }
        }

        resolvePendingHardLinks(root, pendingHardLinks)
        return count
    }

    /**
     * Returns the lexical in-root location for an archive entry. This rejects
     * `..` traversal and absolute host paths before any filesystem operation.
     */
    private fun lexicalDestination(root: Path, entryName: String): Path {
        require(entryName.isNotBlank()) { "An archive entry had an empty path." }
        val cleanName = entryName.removePrefix("./")
        val raw = Paths.get(cleanName)
        require(!raw.isAbsolute) { "An archive entry attempted to use an absolute host path." }
        val target = root.resolve(raw).normalize()
        require(target.startsWith(root) && target != root) {
            "An archive entry attempted to escape its destination."
        }
        return target
    }

    /**
     * Linux root filesystems legitimately contain directory aliases such as
     * /bin -> usr/bin and /lib -> usr/lib. A later tar entry may then address
     * bin/sh. Instead of following a host symlink blindly (unsafe) or rejecting
     * the archive (the previous bug), resolve every existing ancestor symlink
     * explicitly and require each resolved target to remain inside the staged
     * rootfs.
     */
    private fun resolveTarDestination(root: Path, lexicalPath: Path): Path {
        val parent = lexicalPath.parent ?: root
        val relativeParent = root.relativize(parent)
        var current = root
        for (component in relativeParent) {
            current = current.resolve(component.toString()).normalize()
            require(current.startsWith(root)) { "An archive path escaped the Linux environment." }
            current = resolveRootfsSymlink(root, current)
        }
        val resolved = current.resolve(lexicalPath.fileName.toString()).normalize()
        require(resolved.startsWith(root) && resolved != root) {
            "An archive path escaped the Linux environment."
        }
        return resolved
    }

    private fun resolveRootfsSymlink(root: Path, initial: Path): Path {
        var current = initial
        var hops = 0
        while (Files.isSymbolicLink(current)) {
            check(++hops <= MAX_SYMLINK_HOPS) { "A Linux archive contains a symbolic-link cycle." }
            val link = Files.readSymbolicLink(current)
            current = if (link.isAbsolute) {
                root.resolve(link.toString().trimStart('/')).normalize()
            } else {
                current.parent.resolve(link).normalize()
            }
            require(current.startsWith(root)) {
                "A Linux archive symbolic link attempted to escape the staged root filesystem."
            }
        }
        return current
    }

    private fun symbolicLinkTarget(root: Path, lexicalPath: Path, linkName: String): Path {
        require(linkName.isNotBlank()) { "A Linux archive symbolic link had an empty target." }
        val raw = Paths.get(linkName)
        val target = if (raw.isAbsolute) {
            root.resolve(linkName.trimStart('/')).normalize()
        } else {
            lexicalPath.parent.resolve(raw).normalize()
        }
        require(target.startsWith(root)) {
            "A Linux archive symbolic link attempted to escape the staged root filesystem."
        }
        return target
    }

    private fun hardLinkTarget(root: Path, linkName: String): Path {
        require(linkName.isNotBlank()) { "A Linux archive hard link had an empty target." }
        val cleanName = linkName.removePrefix("./").trimStart('/')
        val target = root.resolve(cleanName).normalize()
        require(target.startsWith(root) && target != root) {
            "A Linux archive hard link attempted to escape the staged root filesystem."
        }
        return target
    }

    private fun createHardLinkIfReady(root: Path, path: Path, lexicalTarget: Path): Boolean {
        val target = resolveTarDestination(root, lexicalTarget)
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return false
        removeExistingLeaf(path)
        Files.createLink(path, target)
        return true
    }

    private fun resolvePendingHardLinks(root: Path, pending: List<PendingHardLink>) {
        var remaining = pending.toMutableList()
        while (remaining.isNotEmpty()) {
            var progress = false
            val unresolved = mutableListOf<PendingHardLink>()
            for (link in remaining) {
                if (createHardLinkIfReady(root, link.path, link.target)) {
                    progress = true
                } else {
                    unresolved += link
                }
            }
            if (!progress) {
                error(
                    "A Linux archive hard link referenced a target that was never extracted: " +
                        unresolved.first().target,
                )
            }
            remaining = unresolved
        }
    }

    private fun removeExistingLeaf(path: Path) {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            check(path.toFile().deleteRecursively()) {
                "An existing archive directory could not be replaced: " + path
            }
        } else {
            Files.delete(path)
        }
    }

    companion object {
        const val COMPLETION_MARKER = ".alaser-installed"
        private const val MAX_SYMLINK_HOPS = 64

        fun checksum(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        }

        fun checksum(input: InputStream): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
            return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        }
    }
}
