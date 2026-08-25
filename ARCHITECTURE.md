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

ProcessTerminal executes real operating-system processes and can maintain a
stdin/stdout/stderr session. It is not a pseudoterminal. Alternate screens,
terminal resize, raw-mode programs, and terminal emulation require a future
audited native PTY implementation and terminal renderer.

## Linux distinction

RootfsInstaller performs HTTPS download, SHA-256 verification, bounded
destination checks, archive extraction, and Linux shell validation.
ProotBackend constructs a rootless PRoot invocation only when an independently
obtained executable and extracted filesystem are present.

Neither a PRoot executable nor a redistribution-approved Debian image is
bundled. Therefore rootless Linux is not an out-of-the-box completed feature.

## Integrations

TelegramClient implements getMe, getUpdates long polling, user/chat allowlists,
message-size limits, and sendMessage. MCP implements initialization, tool
inspection, trust-gated execution, and JSON-RPC HTTP transport. These modules
are not yet connected to complete settings or foreground-service flows.
