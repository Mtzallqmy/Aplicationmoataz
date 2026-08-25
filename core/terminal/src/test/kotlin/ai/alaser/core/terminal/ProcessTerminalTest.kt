package ai.alaser.core.terminal

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProcessTerminalTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun executesCommandsAndPreservesUnicode() = runBlocking {
        val terminal = ProcessTerminal()
        val result = terminal.execute("printf 'مرحبا'", temporary.newFolder("workspace"))
        assertEquals(0, result.exitCode)
        assertEquals("مرحبا\n", result.stdout)
        terminal.close()
    }

    @Test
    fun reportsStandardError() = runBlocking {
        val terminal = ProcessTerminal()
        val result = terminal.execute("printf 'error' >&2; exit 7", temporary.newFolder("workspace"))
        assertEquals(7, result.exitCode)
        assertTrue(result.stderr.contains("error"))
        terminal.close()
    }

    @Test
    fun honorsSelectedShellPrefixAndEnvironment() = runBlocking {
        val terminal = ProcessTerminal()
        val result = terminal.execute(
            "printf '%s' \"\u0024ALASER_SELECTED_ENVIRONMENT\"",
            temporary.newFolder("sandbox-workspace"),
            environment = mapOf("ALASER_SELECTED_ENVIRONMENT" to "linux"),
            commandPrefix = listOf(ProcessTerminal.defaultShell()),
        )
        assertEquals(0, result.exitCode)
        assertEquals("linux\n", result.stdout)
        terminal.close()
    }
}
