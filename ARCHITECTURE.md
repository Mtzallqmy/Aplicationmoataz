# Architecture

## Runtime ownership

The Android application owns local workspaces, SQLite, encrypted credentials,
the selected provider, and the active agent coroutine. Core and agent modules
do not require an account, proprietary backend, Supabase, or Cloudflare.

The agent request flow is:

    User task
      -> persisted workspace and session
      -> direct provider SSE request
      -> streamed assistant text or function calls
      -> risk assessment
      -> interactive approval when required
      -> workspace-safe file or shell tool
      -> persisted tool result
      -> subsequent provider request
      -> final response

## Boundaries

The model module contains application-independent domain types. Filesystem,
terminal, provider, agent, Telegram, and MCP modules are JVM Kotlin libraries.
The database and security modules are Android libraries because SQLiteOpenHelper
and Android Keystore are Android platform APIs.

The app module wires dependencies manually. There is no dependency injection
framework, cloud SDK, or opaque service locator.

## Terminal distinction

NativePtySession creates a real pseudoterminal through a project-owned JNI/C
implementation using posix_openpt, grantpt, unlockpt, fork, setsid,
TIOCSCTTY, and terminal-size ioctls. Compose can start a persistent interactive
shell, write user input, and send control characters.

ProcessTerminal remains a separate ordinary process-pipe executor for agent
commands. Full ANSI terminal rendering and alternate-screen emulation are not
yet implemented.

## Linux distinction

RootfsInstaller performs HTTPS download, SHA-256 verification, bounded
destination checks, archive extraction, and Linux shell validation.
ProotBackend constructs a rootless PRoot invocation only when an independently
obtained executable and extracted filesystem are present.

Neither a PRoot executable nor a redistribution-approved Debian image is
bundled. Therefore rootless Linux is not an out-of-the-box completed feature.

## Integrations

TelegramClient implements getMe, getUpdates long polling, user/chat allowlists,
message-size limits, sendMessage, encrypted bot-token persistence, settings UI,
and app-lifetime agent task routing. MCP implements initialization, tool
inspection, trust-gated execution, JSON-RPC HTTP transport, persisted server
configuration, and settings UI. Android foreground-service execution and cloud
webhooks are not implemented.
