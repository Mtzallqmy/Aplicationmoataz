package ai.alaser.core.sandbox

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
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
    fun install(descriptor: LinuxEnvironmentDescriptor): Flow<EnvironmentInstallEvent> = flow {
        require(descriptor.archiveUrl.startsWith("https://")) {
            "Linux images must be downloaded over HTTPS."
        }
        validateDescriptor(descriptor)
        client.newCall(Request.Builder().url(descriptor.archiveUrl).build()).execute().use { response ->
            check(response.isSuccessful) { "Root filesystem download failed with HTTP " + response.code + "." }
            val body = requireNotNull(response.body) { "The image download response was empty." }
            body.byteStream().use { input ->
                installFromStream(descriptor, input, body.contentLength().takeIf { it >= 0 })
            }
        }
    }.flowOn(Dispatchers.IO)

    fun installBundled(
        descriptor: LinuxEnvironmentDescriptor,
        openArchive: () -> InputStream,
    ): Flow<EnvironmentInstallEvent> = flow {
        validateDescriptor(descriptor)
        openArchive().use { input ->
            installFromStream(descriptor, input, null)
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

    private suspend fun FlowCollector<EnvironmentInstallEvent>.installFromStream(
        descriptor: LinuxEnvironmentDescriptor,
        input: InputStream,
        total: Long?,
    ) {
        Files.createDirectories(environmentsDirectory.toPath())
        val temporaryArchive = File(environmentsDirectory, descriptor.id + ".download")
        val destination = File(environmentsDirectory, descriptor.id)

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
            check(!destination.exists()) { "A Linux environment with this identifier already exists." }
            Files.createDirectories(destination.toPath())
            val extracted = extract(temporaryArchive, destination.toPath(), descriptor.archiveUrl)
            emit(EnvironmentInstallEvent.Extracting(extracted))
            check(File(destination, "bin/sh").exists() || File(destination, "usr/bin/sh").exists()) {
                "The extracted image does not contain a usable Linux shell."
            }
            emit(EnvironmentInstallEvent.Installed(destination))
        } catch (exception: Exception) {
            destination.deleteRecursively()
            throw exception
        } finally {
            temporaryArchive.delete()
        }
    }

    private fun extract(archive: File, destination: Path, url: String): Int =
        if (url.substringBefore('?').endsWith(".zip")) extractZip(archive, destination)
        else extractTarGzip(archive, destination)

    private fun extractZip(archive: File, destination: Path): Int {
        var count = 0
        ZipInputStream(archive.inputStream().buffered()).use { input ->
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

    private fun extractTarGzip(archive: File, destination: Path): Int {
        var count = 0
        TarArchiveInputStream(GZIPInputStream(archive.inputStream().buffered())).use { input ->
            while (true) {
                val entry = input.nextTarEntry ?: break
                val path = safeDestination(destination, entry.name)
                when {
                    entry.isDirectory -> Files.createDirectories(path)
                    entry.isSymbolicLink || entry.isLink -> {
                        val linkName = Path.of(entry.linkName)
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
    }
}
