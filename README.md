# Alaser AI

Alaser AI is a local-first Android coding-agent workspace written in Kotlin and
Jetpack Compose. Its intended GitHub repository is
https://github.com/Mtzallqmy/Aplicationmoataz.

## Current implementation

- Native Compose screens for agent chat, projects, files, editing, providers,
  settings, and command execution.
- Direct OpenAI-compatible provider requests with server-sent-event streaming,
  streamed tool calls, provider testing, and manually configured model IDs.
- An iterative agent loop with explicit states, file tools, command tools,
  Git status/diff, command-risk classification, and interactive approvals.
- Workspace-scoped file access with parent-traversal, sensitive-file,
  escaping-symlink, binary-file, and ZIP-slip protections.
- Persistent projects, sessions, messages, and provider metadata in SQLite.
- AES-256-GCM provider-secret encryption with Android Keystore.
- A real Android NDK/JNI pseudo-terminal with interactive shell I/O, terminal
  resize support, CTRL+C/TAB/ESC controls, and ARM64/x86_64 native builds.
- A separate process-backed command executor for agent tool calls.
- A checksum-verified root filesystem installer and a PRoot backend that works
  only when a compatible executable and valid root filesystem are supplied.
- Deny-by-default Telegram long-polling and an explicitly trusted MCP HTTP
  client as reusable integration modules.
- A small Rust crate containing independently testable filesystem primitives.

This is source code, not a claim that every product requirement has already
been completed. Read KNOWN_LIMITATIONS.md before evaluating the project.

## Build prerequisites

- Full JDK 17, including javac.
- Android SDK platform 35 and compatible Android build tools.
- Android NDK and CMake for the native pseudo-terminal.
- Gradle 8.10.2.
- Network access to Google Maven, Maven Central, and the Gradle distribution.

The wrapper properties are committed, but gradle-wrapper.jar is not bundled.
On a machine with Gradle installed:

    gradle wrapper --gradle-version 8.10.2
    ./gradlew :app:assembleDebug
    ./gradlew test
    ./gradlew :app:lintDebug

When the Android build succeeds, the debug APK is located at:

    app/build/outputs/apk/debug/app-debug.apk

The checked-in GitHub Actions workflow provisions the required Android build
environment, builds an installable signed debug APK, runs unit tests and lint,
and uploads the APK as a downloadable workflow artifact.

## First functional flow

1. Create a project under Projects.
2. Add an OpenAI-compatible provider under Settings → AI Providers.
3. Enter its base URL, API key, and an explicit model identifier.
4. Start a task from Agent.
5. Approve file writes and shell commands when requested.
6. Review generated content under Workspace Files.

Android's native shell does not include Python, Node.js, or Git by default.
Those tools require a functioning separately provisioned Linux environment or
another backend.

## Repository layout

    app/                    Android application and Compose UI
    core/model/             Domain models
    core/database/          Versioned SQLite persistence
    core/security/          Android Keystore secret encryption
    core/filesystem/        Workspace-safe file operations
    core/terminal/          Process-backed shell sessions
    core/sandbox/           Rootfs installer and sandbox backends
    ai/providers/           OpenAI-compatible streaming client
    agent/runtime/          Agent loop, tools, approvals, risk policy
    integration/telegram/   Telegram Bot API long polling
    integration/mcp/        MCP JSON-RPC over HTTP
    native/rust-core/       Optional isolated Rust primitives
    docs/adr/               Architecture decisions
    docs/licenses/          Dependency and reference audit

See ARCHITECTURE.md, DEVELOPMENT.md, SECURITY.md, PRIVACY.md, and
KNOWN_LIMITATIONS.md for implementation details.
