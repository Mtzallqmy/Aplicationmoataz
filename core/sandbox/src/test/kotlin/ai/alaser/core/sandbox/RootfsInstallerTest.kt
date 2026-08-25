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
    }

    private fun rootfsArchive(): ByteArray {
        val output = ByteArrayOutputStream()
        TarArchiveOutputStream(GZIPOutputStream(output)).use { tar ->
            writeEntry(tar, "bin/sh", "#!/bin/sh\n", 0b111101101)
            writeEntry(tar, "etc/os-release", "Alaser Linux", 0b110100100)
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
