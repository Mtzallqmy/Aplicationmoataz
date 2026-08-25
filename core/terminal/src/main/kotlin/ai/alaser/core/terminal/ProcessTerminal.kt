package ai.alaser.core.terminal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class TerminalStatus { STARTING, RUNNING, EXITED, FAILED }

data class TerminalOutput(val sessionId: String, val text: String, val stderr: Boolean = false)

data class CommandResult(val command: String, val stdout: String, val stderr: String, val exitCode: Int)

class TerminalSession internal constructor(
    val id: String,
    private val process: Process,
    private val scope: CoroutineScope,
) {
    private val statusValue = MutableStateFlow(TerminalStatus.STARTING)
    private val outputValue = MutableSharedFlow<TerminalOutput>(extraBufferCapacity = 128)

    val status: StateFlow<TerminalStatus> = statusValue
    val output: SharedFlow<TerminalOutput> = outputValue
    val isRealPty: Boolean = false

    init {
        statusValue.value = TerminalStatus.RUNNING
        stream(process.inputStream, stderr = false)
        stream(process.errorStream, stderr = true)
        scope.launch(Dispatchers.IO) {
            process.waitFor()
            statusValue.value = TerminalStatus.EXITED
        }
    }

    suspend fun write(text: String) = withContext(Dispatchers.IO) {
        check(process.isAlive) { "The terminal process is no longer running." }
        process.outputStream.write(text.toByteArray(Charsets.UTF_8))
        process.outputStream.flush()
    }

    fun interrupt() {
        if (process.isAlive) process.destroy()
    }

    fun kill() {
        if (process.isAlive) process.destroyForcibly()
    }

    private fun stream(input: java.io.InputStream, stderr: Boolean): Job = scope.launch(Dispatchers.IO) {
        val buffer = ByteArray(4096)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            outputValue.emit(TerminalOutput(id, buffer.decodeToString(endIndex = read), stderr))
        }
    }
}

class ProcessTerminal {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessions = ConcurrentHashMap<String, TerminalSession>()

    fun startSession(workspace: File, shell: String = defaultShell()): TerminalSession {
        require(workspace.isDirectory) { "A valid workspace directory is required." }
        val process = ProcessBuilder(shell)
            .directory(workspace)
            .redirectErrorStream(false)
            .start()
        return TerminalSession(UUID.randomUUID().toString(), process, scope).also { sessions[it.id] = it }
    }

    suspend fun execute(
        command: String,
        workspace: File,
        timeoutSeconds: Long = 60,
        environment: Map<String, String> = emptyMap(),
    ): CommandResult = withTimeout(timeoutSeconds * 1_000) {
        withContext(Dispatchers.IO) {
            val process = ProcessBuilder(defaultShell(), "-c", command)
                .directory(workspace)
                .apply { environment().putAll(environment) }
                .start()
            val stdout = StringBuilder()
            val stderr = StringBuilder()
            val stdoutJob = launch { process.inputStream.bufferedReader().useLines { lines -> lines.forEach { stdout.appendLine(it) } } }
            val stderrJob = launch { process.errorStream.bufferedReader().useLines { lines -> lines.forEach { stderr.appendLine(it) } } }
            try {
                val code = process.waitFor()
                stdoutJob.join()
                stderrJob.join()
                CommandResult(command, stdout.toString(), stderr.toString(), code)
            } finally {
                if (process.isAlive) process.destroyForcibly()
            }
        }
    }

    fun getSession(id: String): TerminalSession? = sessions[id]

    fun closeSession(id: String) {
        sessions.remove(id)?.kill()
    }

    fun close() {
        sessions.values.forEach { it.kill() }
        sessions.clear()
        scope.cancel()
    }

    companion object {
        fun defaultShell(): String = listOf("/system/bin/sh", "/bin/sh")
            .firstOrNull { File(it).canExecute() }
            ?: error("No executable system shell is available.")
    }
}
