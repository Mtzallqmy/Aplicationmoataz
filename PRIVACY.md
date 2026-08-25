# Privacy

Alaser AI stores workspaces, chats, sessions, and provider metadata locally.
Using the application does not require an Alaser account or a hosted backend.

Remote AI requests are sent directly to the provider configured by the user.
Prompts, selected file contents, and tool output may therefore reach that
provider during an agent task. The provider has its own privacy policy.

Provider API keys are encrypted with Android Keystore. Android backup is
disabled for the application. Telemetry and analytics are not implemented.

Telegram and MCP integrations are optional and do not run unless a user-facing
integration is added and explicitly enabled.

Encrypted sync, account login, hosted remote agents, and provider proxies are
not implemented.
