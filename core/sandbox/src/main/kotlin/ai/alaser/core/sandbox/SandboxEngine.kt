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
        if (usableEnvironment(destination)) return destination
        if (destination.exists()) {
            check(destination.deleteRecursively()) { "The incomplete Linux environment could not be removed." }
        }
        File(environmentsDirectory, descriptor.id + ".installing").takeIf(File::exists)?.let {
            check(it.deleteRecursively()) { "The stale Linux installation directory could not be removed." }
        }
        File(environmentsDirectory, descriptor.id + ".download").delete()
        return null
    }

    private fun usableEnvironment(directory: File): Boolean =
        directory.isDirectory &&
            (File(directory, "bin/sh").isFile || File(directory, "usr/bin/sh").isFile)

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
        check(usableEnvironment(staging)) { "The extracted image does not contain a usable Linux shell." }
        val destination = File(environmentsDirectory, descriptor.id)
        check(!destination.exists()) { "A completed Linux environment already exists." }
        check(staging.renameTo(destination)) { "The verified Linux environment could not be activated atomically." }
    }

    private fun extract(archive: InputStream, destination: Path, url: String): Int =
        if (url.substringBefore('?').endsWith(".zip")) extractZip(archive, destination)
        else extractTarGzip(archive, destination)

    private fun extractZip(archive: InputStream, destination: Path): Int {
        var count = 0
        ZipInputStream(archive.buffered()).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                val path = safeDestination(destination, entry.name)
                if (entry.isDirectory) {
                    Files.createDirectories(path)
                } else {
                    Files.createDirectories(path.parent)
                    Files.newOutputStream(path).use { input.copyTo(it) }
                }
                count++
            }
        }
        return count
    }

    private fun extractTarGzip(archive: InputStream, destination: Path): Int {
        var count = 0
        TarArchiveInputStream(GZIPInputStream(archive.buffered())).use { input ->
            while (true) {
                val entry = input.nextTarEntry ?: break
                val path = safeDestination(destination, entry.name)
                when {
                    entry.isDirectory -> Files.createDirectories(path)
                    entry.isSymbolicLink || entry.isLink -> {
                        val linkName = Paths.get(entry.linkName)
                        val target = when {
                            linkName.isAbsolute -> destination.resolve(entry.linkName.removePrefix("/")).normalize()
                            entry.isLink -> destination.resolve(entry.linkName.removePrefix("./")).normalize()
                            else -> path.parent.resolve(linkName).normalize()
                        }
                        require(target.startsWith(destination)) { "Archive link escapes the Linux environment." }
                        Files.createDirectories(path.parent)
                        if (entry.isSymbolicLink) {
                            Files.createSymbolicLink(path, path.parent.relativize(target))
                        } else {
                            Files.createLink(path, target)
                        }
                    }
                    entry.isFile -> {
                        Files.createDirectories(path.parent)
                        Files.newOutputStream(path).use { input.copyTo(it) }
                        if ((entry.mode and 0b001001001) != 0) path.toFile().setExecutable(true, false)
                    }
                }
                count++
            }
        }
        return count
    }

    private fun safeDestination(root: Path, entryName: String): Path {
        val target = root.resolve(entryName.removePrefix("./")).normalize()
        require(target.startsWith(root)) { "An archive entry attempted to escape its destination." }
        var ancestor = target.parent
        while (ancestor != null && ancestor != root) {
            require(!Files.isSymbolicLink(ancestor)) {
                "An archive entry attempted to write through a symbolic-link directory."
            }
            ancestor = ancestor.parent
        }
        return target
    }

    companion object {
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
