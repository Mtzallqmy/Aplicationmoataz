package ai.alaser.agent.runtime

import ai.alaser.core.model.RiskLevel

data class CommandRiskAssessment(
    val level: RiskLevel,
    val reasons: List<String>,
)

class CommandRiskAnalyzer {
    fun assess(command: String): CommandRiskAssessment {
        val normalized = command.trim()
        require(normalized.isNotEmpty()) { "An empty command cannot be executed." }
        val reasons = mutableListOf<String>()
        var level = RiskLevel.SAFE

        fun raise(next: RiskLevel, reason: String) {
            if (next.ordinal > level.ordinal) level = next
            reasons += reason
        }

        if (SECRET_PATTERN.containsMatchIn(normalized)) raise(RiskLevel.CRITICAL, "The command may access credentials.")
        if (ROOT_DELETE_PATTERN.containsMatchIn(normalized)) raise(RiskLevel.CRITICAL, "The command may delete protected paths.")
        if (REMOTE_SHELL_PATTERN.containsMatchIn(normalized)) raise(RiskLevel.CRITICAL, "Remote content is piped into a shell.")
        if (DIRECTORY_ESCAPE_PATTERN.containsMatchIn(normalized)) raise(RiskLevel.CRITICAL, "The command references a parent path.")
        if (DESTRUCTIVE_PATTERN.containsMatchIn(normalized)) raise(RiskLevel.DANGEROUS, "The command can delete or overwrite data.")
        if (PACKAGE_PATTERN.containsMatchIn(normalized)) raise(RiskLevel.DANGEROUS, "Installing packages can execute untrusted code.")
        if (NETWORK_PATTERN.containsMatchIn(normalized)) raise(RiskLevel.DANGEROUS, "The command accesses the network.")
        if (GIT_DESTRUCTIVE_PATTERN.containsMatchIn(normalized)) raise(RiskLevel.DANGEROUS, "Git history or uncommitted work may change.")
        if (WRITE_PATTERN.containsMatchIn(normalized)) raise(RiskLevel.WRITE, "The command may modify workspace files.")
        return CommandRiskAssessment(level, reasons)
    }

    companion object {
        private val SECRET_PATTERN = Regex("""(^|[\s/])(\.env(\.\w+)?|id_rsa|id_ed25519|credentials|\.aws|\.ssh)(\s|/|$)""")
        private val ROOT_DELETE_PATTERN = Regex("""\brm\s+.*(?:-[a-zA-Z]*r[a-zA-Z]*\s+)?(?:/|\x24HOME|~)(?:\s|$)""")
        private val REMOTE_SHELL_PATTERN = Regex("""\b(curl|wget)\b[^|]*\|\s*(sh|bash|zsh)\b""")
        private val DIRECTORY_ESCAPE_PATTERN = Regex("""(^|[\s"'=/])\.\.(/|\\|$)""")
        private val DESTRUCTIVE_PATTERN = Regex("""\b(rm|rmdir|shred|truncate|chmod|chown|kill|pkill)\b""")
        private val PACKAGE_PATTERN = Regex("""\b(apt|apt-get|pkg|npm|pnpm|yarn|bun|pip|pip3|uv|cargo)\s+(install|add|remove|update|upgrade)\b""")
        private val NETWORK_PATTERN = Regex("""\b(curl|wget|scp|ssh|rsync)\b""")
        private val GIT_DESTRUCTIVE_PATTERN = Regex("""\bgit\s+(push|reset|clean|rebase|checkout|restore|commit)\b""")
        private val WRITE_PATTERN = Regex("""(^|[\s;&|])(touch|mkdir|mv|cp|sed|tee)\b|(?<![<&])>{1,2}""")
    }
}
