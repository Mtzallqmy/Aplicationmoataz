package ai.alaser.core.sandbox

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.GZIPOutputStream
import java.util.concurrent.atomic.AtomicInteger

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
        File(environments, "ubuntu").apply { mkdirs(); resolve("partial").writeText("broken") }
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

    private fun rootfsArchive(): ByteArray {
        val output = ByteArrayOutputStream()
        TarArchiveOutputStream(GZIPOutputStream(output)).use { tar ->
            writeEntry(tar, "bin/sh", "#!/bin/sh\n", 0b111101101)
            writeEntry(tar, "etc/os-release", "Alaser Linux", 0b110100100)
            val guestLink = TarArchiveEntry("usr/bin/sh", org.apache.commons.compress.archivers.tar.TarConstants.LF_SYMLINK)
            guestLink.linkName = "/bin/sh"
            tar.putArchiveEntry(guestLink)
            tar.closeArchiveEntry()
        }
        return output.toByteArray()
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
