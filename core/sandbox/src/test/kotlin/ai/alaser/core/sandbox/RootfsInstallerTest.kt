package ai.alaser.core.sandbox

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPOutputStream

class RootfsInstallerTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun installsVerifiedBundledRootfsAndPreservesExecutableShell() = runBlocking {
        val archive = rootfsArchive()
        val source = File(temporary.root, "rootfs.tar.gz").apply { writeBytes(archive) }
        val descriptor = LinuxEnvironmentDescriptor(
            id = "alpine",
            displayName = "Alpine Linux",
            architecture = "arm64-v8a",
            archiveUrl = "asset://linux/alpine-aarch64.rootfs",
            sha256 = RootfsInstaller.checksum(source),
        )

        val events = RootfsInstaller(temporary.newFolder("environments"))
            .installBundled(descriptor) { archive.inputStream() }
            .toList()

        val installed = events.last() as EnvironmentInstallEvent.Installed
        assertTrue(File(installed.directory, "bin/sh").canExecute())
        assertEquals("Alaser Linux", File(installed.directory, "etc/os-release").readText())
        val guestLink = File(installed.directory, "usr/bin/sh").toPath()
        assertTrue(Files.isSymbolicLink(guestLink))
        assertTrue("Guest absolute links must stay inside the host rootfs.", Files.exists(guestLink))
    }

    @Test
    fun installsMergedUsrRootfsAndAllowsEntriesThroughRootfsLocalSymlinkDirectories() = runBlocking {
        val archive = mergedUsrArchive()
        val source = File(temporary.root, "merged-usr.tar.gz").apply { writeBytes(archive) }
        val descriptor = LinuxEnvironmentDescriptor(
            id = "merged-usr",
            displayName = "Merged usr Linux",
            architecture = "arm64-v8a",
            archiveUrl = "asset://linux/merged-usr.rootfs",
            sha256 = RootfsInstaller.checksum(source),
        )

        val installed = RootfsInstaller(temporary.newFolder("merged-usr-environments"))
            .installBundled(descriptor) { archive.inputStream() }
            .toList()
            .last() as EnvironmentInstallEvent.Installed

        val bin = File(installed.directory, "bin").toPath()
        assertTrue("/bin must remain a rootfs-local symbolic link.", Files.isSymbolicLink(bin))
        assertTrue("A file addressed through /bin -> usr/bin must be extracted.", File(installed.directory, "bin/sh").canExecute())
        val bzcat = File(installed.directory, "usr/bin/bzcat").toPath()
        assertTrue("A duplicate archive leaf must be replaceable by its final symlink entry.", Files.isSymbolicLink(bzcat))
        assertTrue("The final bzcat symlink must resolve inside the rootfs.", Files.exists(bzcat))
    }

    @Test
    fun rejectsRootfsSymlinkThatEscapesStagingDirectory() = runBlocking {
        val archive = escapingSymlinkArchive()
        val source = File(temporary.root, "escape.tar.gz").apply { writeBytes(archive) }
        val environments = temporary.newFolder("escape-environments")
        val descriptor = LinuxEnvironmentDescriptor(
            id = "escape",
            displayName = "Unsafe rootfs",
            architecture = "arm64-v8a",
            archiveUrl = "asset://linux/escape.rootfs",
            sha256 = RootfsInstaller.checksum(source),
        )

        val failure = runCatching {
            RootfsInstaller(environments).installBundled(descriptor) { archive.inputStream() }.toList()
        }.exceptionOrNull()

        assertTrue("An escaping archive symlink must be rejected.", failure != null)
        assertTrue("Failed staging data must be removed.", !File(environments, "escape.installing").exists())
        assertTrue("The archive must never write outside its environment.", !File(temporary.root, "outside/pwned").exists())
    }

    @Test
    fun resolvesHardLinkWhoseTargetAppearsLaterInArchive() = runBlocking {
        val archive = deferredHardLinkArchive()
        val source = File(temporary.root, "hard-link.tar.gz").apply { writeBytes(archive) }
        val descriptor = LinuxEnvironmentDescriptor(
            id = "hard-link",
            displayName = "Hard link Linux",
            architecture = "arm64-v8a",
            archiveUrl = "asset://linux/hard-link.rootfs",
            sha256 = RootfsInstaller.checksum(source),
        )

        val installed = RootfsInstaller(temporary.newFolder("hard-link-environments"))
            .installBundled(descriptor) { archive.inputStream() }
            .toList()
            .last() as EnvironmentInstallEvent.Installed

        val shell = File(installed.directory, "bin/sh").toPath()
        val target = File(installed.directory, "usr/bin/sh").toPath()
        assertTrue(Files.exists(shell))
        assertTrue(Files.isSameFile(shell, target))
    }

    @Test
    fun prootRunsGuestAsRootAndBindsWorkspace() {
        val executable = File(temporary.root, "proot").apply {
            writeText("binary")
            setExecutable(true)
        }
        val rootfs = temporary.newFolder("proot-rootfs")
        File(rootfs, "bin").mkdirs()
        File(rootfs, "bin/sh").apply {
            writeText("#!/bin/sh\n")
            setExecutable(true)
        }
        val workspace = temporary.newFolder("workspace")

        val command = ProotBackend(executable, rootfs).commandPrefix(workspace)

        assertTrue("PRoot must emulate root so package managers are usable.", "-0" in command)
        assertTrue(command.any { it == "--bind=" + workspace.absolutePath + ":/workspace" })
        assertEquals("/bin/sh", command.last())
    }

    @Test
    fun concurrentBundledInstallIsSerializedAndIdempotent() = runBlocking {
        val archive = rootfsArchive()
        val source = File(temporary.root, "concurrent.tar.gz").apply { writeBytes(archive) }
        val descriptor = LinuxEnvironmentDescriptor(
            id = "ubuntu",
            displayName = "Ubuntu",
            architecture = "arm64-v8a",
            archiveUrl = "asset://linux/ubuntu-arm64.rootfs",
            sha256 = RootfsInstaller.checksum(source),
        )
        val opens = AtomicInteger()
        val installer = RootfsInstaller(temporary.newFolder("concurrent-environments"))

        val results = listOf(1, 2).map {
            async {
                installer.installBundled(descriptor) {
                    opens.incrementAndGet()
                    archive.inputStream()
                }.toList().last() as EnvironmentInstallEvent.Installed
            }
        }.awaitAll()

        assertEquals(results[0].directory.canonicalPath, results[1].directory.canonicalPath)
        assertEquals("Only checksum and extraction should open the bundled archive.", 2, opens.get())
        assertTrue(File(results[0].directory, "bin/sh").canExecute())
    }

    @Test
    fun replacesIncompleteEnvironmentLeftByAnInterruptedInstall() = runBlocking {
        val archive = rootfsArchive()
        val source = File(temporary.root, "repair.tar.gz").apply { writeBytes(archive) }
        val environments = temporary.newFolder("repair-environments")
        File(environments, "ubuntu").apply {
            resolve("bin").mkdirs()
            resolve("bin/sh").writeText("incomplete shell")
            resolve("partial").writeText("broken")
        }
        val descriptor = LinuxEnvironmentDescriptor(
            id = "ubuntu",
            displayName = "Ubuntu",
            architecture = "arm64-v8a",
            archiveUrl = "asset://linux/ubuntu-arm64.rootfs",
            sha256 = RootfsInstaller.checksum(source),
        )

        val installed = RootfsInstaller(environments).installBundled(descriptor) { archive.inputStream() }
            .toList().last() as EnvironmentInstallEvent.Installed

        assertTrue(File(installed.directory, "bin/sh").canExecute())
        assertTrue(!File(installed.directory, "partial").exists())
        assertEquals(descriptor.sha256, File(installed.directory, RootfsInstaller.COMPLETION_MARKER).readText())
    }

    @Test
    fun installsOfficialBundledAlpineArchiveWhenAvailable() = runBlocking {
        val archive = System.getenv("ALASER_BUNDLED_ROOTFS")?.let(::File) ?: return@runBlocking
        val descriptor = LinuxEnvironmentDescriptor(
            id = "official-alpine",
            displayName = "Official Alpine Linux",
            architecture = "arm64-v8a",
            archiveUrl = "asset://linux/alpine-aarch64.rootfs",
            sha256 = RootfsInstaller.checksum(archive),
        )
        val installed = RootfsInstaller(temporary.newFolder("official-environments"))
            .installBundled(descriptor) { archive.inputStream() }
            .toList()
            .last() as EnvironmentInstallEvent.Installed

        assertTrue("The official Linux shell must be executable.", File(installed.directory, "bin/sh").canExecute())
        assertTrue("The package manager must exist.", File(installed.directory, "sbin/apk").exists())
    }

    @Test
    fun installsBundledUbuntuDeveloperEnvironmentWhenAvailable() = runBlocking {
        val archive = System.getenv("ALASER_BUNDLED_UBUNTU")?.let(::File) ?: return@runBlocking
        val descriptor = LinuxEnvironmentDescriptor(
            id = "ubuntu",
            displayName = "Ubuntu Developer",
            architecture = "arm64-v8a",
            archiveUrl = "asset://linux/ubuntu-arm64.rootfs",
            sha256 = RootfsInstaller.checksum(archive),
        )
        val installed = RootfsInstaller(temporary.newFolder("ubuntu-environments"))
            .installBundled(descriptor) { archive.inputStream() }
            .toList()
            .last() as EnvironmentInstallEvent.Installed

        for (tool in listOf("bin/sh", "usr/bin/python3", "usr/bin/git", "usr/bin/node", "usr/bin/npm", "usr/bin/gcc", "usr/bin/rustc", "usr/bin/cargo")) {
            assertTrue("The developer tool is missing: " + tool, File(installed.directory, tool).exists())
        }
    }

    private fun rootfsArchive(): ByteArray = tarGzip {
        writeEntry(it, "bin/sh", "#!/bin/sh\n", 0b111101101)
        writeEntry(it, "etc/os-release", "Alaser Linux", 0b110100100)
        writeSymlink(it, "usr/bin/sh", "/bin/sh")
    }

    private fun mergedUsrArchive(): ByteArray = tarGzip { tar ->
        writeDirectory(tar, "usr/")
        writeDirectory(tar, "usr/bin/")
        writeSymlink(tar, "bin", "usr/bin")
        writeEntry(tar, "bin/sh", "#!/bin/sh\necho merged-usr\n", 0b111101101)
        writeEntry(tar, "usr/bin/bunzip2", "#!/bin/sh\n", 0b111101101)
        writeEntry(tar, "usr/bin/bzcat", "temporary", 0b110100100)
        writeSymlink(tar, "usr/bin/bzcat", "bunzip2")
        writeEntry(tar, "etc/os-release", "Merged usr Linux", 0b110100100)
    }

    private fun escapingSymlinkArchive(): ByteArray = tarGzip { tar ->
        writeSymlink(tar, "escape", "../../outside")
        writeEntry(tar, "escape/pwned", "must-not-exist", 0b110100100)
        writeEntry(tar, "bin/sh", "#!/bin/sh\n", 0b111101101)
    }

    private fun deferredHardLinkArchive(): ByteArray = tarGzip { tar ->
        writeHardLink(tar, "bin/sh", "usr/bin/sh")
        writeEntry(tar, "usr/bin/sh", "#!/bin/sh\n", 0b111101101)
    }

    private fun tarGzip(write: (TarArchiveOutputStream) -> Unit): ByteArray {
        val output = ByteArrayOutputStream()
        TarArchiveOutputStream(GZIPOutputStream(output)).use { tar ->
            write(tar)
        }
        return output.toByteArray()
    }

    private fun writeDirectory(tar: TarArchiveOutputStream, name: String) {
        val entry = TarArchiveEntry(name).apply {
            size = 0
            mode = 0b111101101
        }
        tar.putArchiveEntry(entry)
        tar.closeArchiveEntry()
    }

    private fun writeSymlink(tar: TarArchiveOutputStream, name: String, target: String) {
        val entry = TarArchiveEntry(name, TarConstants.LF_SYMLINK).apply {
            linkName = target
        }
        tar.putArchiveEntry(entry)
        tar.closeArchiveEntry()
    }

    private fun writeHardLink(tar: TarArchiveOutputStream, name: String, target: String) {
        val entry = TarArchiveEntry(name, TarConstants.LF_LINK).apply {
            linkName = target
        }
        tar.putArchiveEntry(entry)
        tar.closeArchiveEntry()
    }

    private fun writeEntry(tar: TarArchiveOutputStream, name: String, value: String, mode: Int) {
        val bytes = value.toByteArray()
        val entry = TarArchiveEntry(name).apply {
            size = bytes.size.toLong()
            this.mode = mode
        }
        tar.putArchiveEntry(entry)
        tar.write(bytes)
        tar.closeArchiveEntry()
    }
}
