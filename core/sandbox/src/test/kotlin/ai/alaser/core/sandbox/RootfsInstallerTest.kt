package ai.alaser.core.sandbox

import kotlinx.coroutines.flow.toList
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
            architecture = "x86_64",
            archiveUrl = "asset://linux/alpine.tar.gz",
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
    fun installsOfficialBundledAlpineArchiveWhenAvailable() = runBlocking {
        val archive = System.getenv("ALASER_BUNDLED_ROOTFS")?.let(::File) ?: return@runBlocking
        val descriptor = LinuxEnvironmentDescriptor(
            id = "official-alpine",
            displayName = "Official Alpine Linux",
            architecture = "x86_64",
            archiveUrl = "asset://linux/alpine-x86_64.tar.gz",
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
            architecture = "x86_64",
            archiveUrl = "asset://linux/ubuntu-amd64.rootfs",
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
