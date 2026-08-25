package ai.alaser.app.terminal

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
import java.io.File

internal object NativePtyBridge {
    init {
        System.loadLibrary("alaser_pty")
    }

    external fun nativeOpen(
        workingDirectory: String,
        arguments: Array<String>,
        temporaryDirectory: String,
        rows: Int,
        columns: Int,
    ): IntArray
    external fun nativeRead(descriptor: Int): ByteArray?
    external fun nativeWrite(descriptor: Int, input: ByteArray)
    external fun nativeResize(descriptor: Int, rows: Int, columns: Int)
    external fun nativeClose(descriptor: Int, processId: Int)
}

class NativePtySession private constructor(
    private val descriptor: Int,
    val processId: Int,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val outputValue = MutableSharedFlow<String>(extraBufferCapacity = 128)
    private val runningValue = MutableStateFlow(true)
    private var reader: Job? = null

    val output: SharedFlow<String> = outputValue
    val running: StateFlow<Boolean> = runningValue

    init {
        reader = scope.launch {
            try {
                while (runningValue.value) {
                    val bytes = NativePtyBridge.nativeRead(descriptor) ?: break
                    outputValue.emit(bytes.toString(Charsets.UTF_8))
                }
            } finally {
                runningValue.value = false
            }
        }
    }

    suspend fun write(value: String) = withContext(Dispatchers.IO) {
        check(runningValue.value) { "The interactive terminal session is no longer running." }
        NativePtyBridge.nativeWrite(descriptor, value.toByteArray(Charsets.UTF_8))
    }

    suspend fun control(code: Int) = withContext(Dispatchers.IO) {
        require(code in 0..31) { "Terminal control characters must be ASCII control codes." }
        NativePtyBridge.nativeWrite(descriptor, byteArrayOf(code.toByte()))
    }

    fun resize(rows: Int, columns: Int) {
        require(rows > 0 && columns > 0) { "Terminal dimensions must be positive." }
        NativePtyBridge.nativeResize(descriptor, rows, columns)
    }

    fun close() {
        if (runningValue.compareAndSet(true, false)) {
            NativePtyBridge.nativeClose(descriptor, processId)
        }
        reader?.cancel()
        scope.cancel()
    }

    companion object {
        fun open(
            workspace: File,
            command: List<String> = listOf("/system/bin/sh", "-i"),
            temporaryDirectory: File = workspace,
            rows: Int = 30,
            columns: Int = 100,
        ): NativePtySession {
            require(workspace.isDirectory) { "A valid workspace is required for an interactive terminal." }
            require(command.isNotEmpty()) { "An executable command is required for an interactive terminal." }
            val result = NativePtyBridge.nativeOpen(
                workspace.absolutePath,
                command.toTypedArray(),
                temporaryDirectory.absolutePath,
                rows,
                columns,
            )
            require(result.size == 2) { "The native PTY did not return a descriptor and process identifier." }
            return NativePtySession(result[0], result[1])
        }
    }
}
