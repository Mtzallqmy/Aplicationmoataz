# Integrations

## OpenAI-compatible providers

Create an AI provider in Settings and provide a full API base URL, explicit
model ID, and API key. HTTPS is required for remote providers. HTTP is accepted
only for localhost or 127.0.0.1 endpoints.

The application calls the provider directly. The test-connection action requests
the provider's models endpoint and shows latency and result details.

## Telegram

TelegramClient supports getMe, getUpdates, and sendMessage. A configuration must
contain at least one explicitly allowed Telegram user ID. Unknown users are
always denied.

Telegram UI setup, encrypted bot-token persistence, Android foreground
execution, agent session routing, and cloud webhook deployment are not
implemented. Therefore a Telegram bot cannot yet be fully configured or run
through the shipped Compose application.

## Model Context Protocol

McpHttpClient supports JSON-RPC initialization, tool listing, trust-gated tool
calls, and an MCP session header. Remote endpoints must use HTTPS; loopback
HTTP is permitted.

Server-management UI, persisted server state, stdio transport, and streaming
responses have not been implemented.

## Linux environments

RootfsInstaller accepts a caller-supplied HTTPS archive URL and an expected
SHA-256 checksum. ZIP and tar.gz images are supported. A valid environment must
contain a Linux shell. ProotBackend additionally requires a separately supplied
executable suitable for the device ABI and Android executable restrictions.

There is currently no image catalog, installer UI, or bundled PRoot executable.

## Cloud

No cloud service is configured, provisioned, or required. There are no cloud
deployment instructions because no Cloudflare or Supabase backend exists in
this source tree.
