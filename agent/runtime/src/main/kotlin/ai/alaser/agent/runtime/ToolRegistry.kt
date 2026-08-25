package ai.alaser.agent.runtime

import ai.alaser.core.filesystem.WorkspaceFileSystem
import ai.alaser.core.model.RiskLevel
import ai.alaser.core.model.ToolDescriptor
import ai.alaser.core.model.ToolExecutionResult
import ai.alaser.core.model.ToolInvocation
import ai.alaser.core.terminal.ProcessTerminal
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class ToolRegistry(
    private val filesystem: WorkspaceFileSystem,
    private val terminal: ProcessTerminal,
    private val commandPrefix: List<String>? = null,
    private val environment: Map<String, String> = emptyMap(),
    private val beforeMutation: (suspend (String) -> Unit)? = null,
) {
    private val riskAnalyzer = CommandRiskAnalyzer()
    private var checkpointCreated = false

    val tools: List<ToolDescriptor> = listOf(
        descriptor("read_file", "Read a UTF-8 text file inside the active workspace.", RiskLevel.SAFE, "path"),
        descriptor("create_file", "Create a new UTF-8 file without overwriting an existing file.", RiskLevel.WRITE, "path", "content"),
        descriptor("write_file", "Create or replace a UTF-8 file inside the active workspace.", RiskLevel.WRITE, "path", "content"),
        descriptor("replace_text", "Replace matching text in an existing workspace file.", RiskLevel.WRITE, "path", "old_text", "new_text"),
        descriptor("move_file", "Rename or move a file within the active workspace.", RiskLevel.WRITE, "source", "destination"),
        descriptor("copy_file", "Copy a regular file within the active workspace.", RiskLevel.WRITE, "source", "destination"),
        descriptor("list_directory", "List files and folders inside the active workspace.", RiskLevel.SAFE, "path"),
        descriptor("create_directory", "Create a directory inside the active workspace.", RiskLevel.WRITE, "path"),
        descriptor("delete_file", "Delete one empty directory or one file inside the active workspace.", RiskLevel.DANGEROUS, "path"),
        descriptor("search_files", "Search workspace filenames while ignoring generated directories.", RiskLevel.SAFE, "query"),
        descriptor("grep", "Search file contents using a case-insensitive regular expression.", RiskLevel.SAFE, "query"),
        descriptor("project_search", "Search project file contents using a regular expression.", RiskLevel.SAFE, "query"),
        descriptor("project_tree", "List the project tree while ignoring generated and sensitive paths.", RiskLevel.SAFE, "path"),
        descriptor("stat_file", "Read metadata for a workspace path.", RiskLevel.SAFE, "path"),
        descriptor("shell_exec", "Execute a shell command with the active workspace as its working directory.", RiskLevel.DANGEROUS, "command"),
        descriptor("list_processes", "List processes visible in the selected execution environment.", RiskLevel.SAFE),
        descriptor("git_status", "Read the short Git working-tree status.", RiskLevel.SAFE),
        descriptor("git_diff", "Read the Git diff for the active workspace.", RiskLevel.SAFE),
        descriptor("git_log", "Read the latest Git commit history.", RiskLevel.SAFE),
        descriptor("git_branch", "Read Git branches in the current workspace.", RiskLevel.SAFE),
    )

    fun assess(invocation: ToolInvocation): RiskLevel {
        val descriptor = tools.firstOrNull { it.name == invocation.name }
            ?: throw IllegalArgumentException("Unknown tool: " + invocation.name)
        val executionPolicy = policy(invocation.name)
        val declaredRisk = when {
            executionPolicy.destructive || executionPolicy.openWorld -> maxOf(descriptor.riskLevel, RiskLevel.DANGEROUS)
            executionPolicy.sideEffect -> maxOf(descriptor.riskLevel, RiskLevel.WRITE)
            else -> descriptor.riskLevel
        }
        return if (invocation.name == "shell_exec") {
            val commandRisk = riskAnalyzer.assess(requireArgument(invocation.arguments, "command")).level
            maxOf(declaredRisk, commandRisk)
        } else {
            declaredRisk
        }
    }

    fun policy(toolName: String): ToolExecutionPolicy = when (toolName) {
        "delete_file" -> ToolExecutionPolicy(sideEffect = true, destructive = true)
        "shell_exec" -> ToolExecutionPolicy(sideEffect = true, openWorld = true)
        in MUTATING_TOOLS -> ToolExecutionPolicy(sideEffect = true)
        else -> ToolExecutionPolicy()
    }

    suspend fun execute(invocation: ToolInvocation): ToolExecutionResult = try {
        if (!checkpointCreated && invocation.name in MUTATING_TOOLS) {
            beforeMutation?.invoke("Before " + invocation.name)
            checkpointCreated = true
        }
        when (invocation.name) {
            "read_file" -> result(invocation, filesystem.readText(requireArgument(invocation.arguments, "path")))
            "create_file" -> {
                val path = requireArgument(invocation.arguments, "path")
                require(!java.nio.file.Files.exists(filesystem.resolve(path))) { "The file already exists." }
                val entry = filesystem.writeText(path, requireArgument(invocation.arguments, "content"))
                result(invocation, "Created " + entry.path + " (" + entry.size + " bytes).")
            }
            "write_file" -> {
                val entry = filesystem.writeText(
                    requireArgument(invocation.arguments, "path"),
                    requireArgument(invocation.arguments, "content"),
                )
                result(invocation, "Saved " + entry.path + " (" + entry.size + " bytes).")
            }
            "replace_text" -> result(
                invocation,
                "Updated " + filesystem.replaceText(
                    requireArgument(invocation.arguments, "path"),
                    requireArgument(invocation.arguments, "old_text"),
                    requireArgument(invocation.arguments, "new_text"),
                ).path,
            )
            "move_file" -> result(
                invocation,
                "Moved to " + filesystem.move(
                    requireArgument(invocation.arguments, "source"),
                    requireArgument(invocation.arguments, "destination"),
                ).path,
            )
            "copy_file" -> result(
                invocation,
                "Copied to " + filesystem.copy(
                    requireArgument(invocation.arguments, "source"),
                    requireArgument(invocation.arguments, "destination"),
                ).path,
            )
            "list_directory" -> result(
                invocation,
                filesystem.list(optionalArgument(invocation.arguments, "path") ?: ".")
                    .joinToString("\n") { (if (it.directory) "DIR  " else "FILE ") + it.path },
            )
            "create_directory" -> result(
                invocation,
                "Created " + filesystem.createDirectory(requireArgument(invocation.arguments, "path")).path,
            )
            "delete_file" -> result(
                invocation,
                if (filesystem.delete(requireArgument(invocation.arguments, "path"))) "Deleted." else "File not found.",
            )
            "search_files" -> result(
                invocation,
                filesystem.search(requireArgument(invocation.arguments, "query")).joinToString("\n") { it.path },
            )
            "grep", "project_search" -> result(
                invocation,
                filesystem.searchContent(requireArgument(invocation.arguments, "query"))
                    .joinToString("\n") { it.path + ":" + it.lineNumber + ": " + it.content },
            )
            "project_tree" -> result(
                invocation,
                filesystem.tree(optionalArgument(invocation.arguments, "path") ?: ".")
                    .joinToString("\n") { (if (it.directory) "DIR  " else "FILE ") + it.path },
            )
            "stat_file" -> {
                val stat = filesystem.stat(requireArgument(invocation.arguments, "path"))
                result(invocation, "path=${stat.path}\ntype=${if (stat.directory) "directory" else "file"}\nsize=${stat.size}\nmodified=${stat.modifiedAt}")
            }
            "shell_exec" -> command(invocation, requireArgument(invocation.arguments, "command"))
            "list_processes" -> command(invocation, "ps -A -o pid,ppid,state,comm,args 2>/dev/null || ps -A")
            "git_status" -> command(invocation, "git status --short")
            "git_diff" -> command(invocation, "git diff --no-ext-diff")
            "git_log" -> command(invocation, "git log --oneline -30")
            "git_branch" -> command(invocation, "git branch --all --no-color")
            else -> error("Unknown tool: " + invocation.name)
        }
    } catch (exception: Exception) {
        ToolExecutionResult(invocation.id, invocation.name, exception.message ?: "Tool execution failed.", isError = true)
    }

    private suspend fun command(invocation: ToolInvocation, command: String): ToolExecutionResult {
        val output = terminal.execute(
            command,
            filesystem.root(),
            timeoutSeconds = 120,
            environment = environment,
            commandPrefix = commandPrefix,
        )
        val combined = buildString {
            append(output.stdout)
            if (output.stderr.isNotBlank()) append(output.stderr)
        }.take(MAX_OUTPUT_CHARS)
        return ToolExecutionResult(
            invocationId = invocation.id,
            toolName = invocation.name,
            output = combined.ifBlank { "Exit code: " + output.exitCode },
            isError = output.exitCode != 0,
            exitCode = output.exitCode,
        )
    }

    private fun result(invocation: ToolInvocation, output: String): ToolExecutionResult =
        ToolExecutionResult(invocation.id, invocation.name, output.take(MAX_OUTPUT_CHARS))

    companion object {
        private const val MAX_OUTPUT_CHARS = 100_000
        private val MUTATING_TOOLS = setOf(
            "create_file", "write_file", "replace_text", "move_file", "copy_file", "create_directory", "delete_file", "shell_exec",
        )

        private fun descriptor(
            name: String,
            description: String,
            risk: RiskLevel,
            vararg properties: String,
        ): ToolDescriptor = ToolDescriptor(
            name = name,
            description = description,
            riskLevel = risk,
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    properties.forEach { property ->
                        put(property, buildJsonObject { put("type", "string") })
                    }
                })
                put("required", buildJsonArray { properties.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
                put("additionalProperties", false)
            },
        )

        private fun requireArgument(arguments: JsonObject, key: String): String =
            optionalArgument(arguments, key) ?: throw IllegalArgumentException("Missing tool argument: " + key)

        private fun optionalArgument(arguments: JsonObject, key: String): String? =
            arguments[key]?.jsonPrimitive?.contentOrNull
    }
}
