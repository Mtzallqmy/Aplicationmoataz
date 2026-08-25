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

Configure a bot under Settings → Telegram Bots, enter its token and at least
one allowed user ID, test the connection, and start local polling. Accepted
messages are routed into the selected project agent and receive a completion
summary. Polling works only while the application process is alive; foreground
execution and cloud webhook deployment are not implemented.

## Model Context Protocol

McpHttpClient supports JSON-RPC initialization, tool listing, trust-gated tool
calls, and an MCP session header. Remote endpoints must use HTTPS; loopback
HTTP is permitted.

Add and inspect servers under Settings → MCP Servers. Trust must be granted
explicitly before a server may execute tools. Stdio transport and streaming
responses have not been implemented.

## Linux environments

RootfsInstaller accepts a caller-supplied HTTPS archive URL and an expected
SHA-256 checksum. ZIP and tar.gz images are supported. A valid environment must
contain a Linux shell. ProotBackend additionally requires a separately supplied
executable suitable for the device ABI and Android executable restrictions.

Install a trusted archive under Settings → Linux Environments by entering its
HTTPS URL and verified SHA-256 checksum. There is no built-in image catalog or
bundled PRoot executable.

## Cloud

No cloud service is configured, provisioned, or required. There are no cloud
deployment instructions because no Cloudflare or Supabase backend exists in
this source tree.
