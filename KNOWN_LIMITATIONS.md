# Known limitations

## Build verification

No APK was produced in the current authoring environment. It lacks a standalone
javac executable, Gradle, Android SDK, Kotlin compiler, Rust compiler, and
Android emulator. Java source-launcher compiler modules are available only for
small Java contract checks. Gradle wrapper properties are present, but
gradle-wrapper.jar must be generated on a machine where Gradle 8.10.2 is
available.

The Kotlin and Android source has not been compiled in this environment.
Until GitHub Actions or a correctly provisioned development machine executes
the build, compilation and runtime correctness remain unverified.

## Terminal

The terminal executes actual shell commands, but its backend uses ordinary
stdin/stdout/stderr pipes. It is not an actual PTY. ANSI terminal emulation,
alternate-screen applications, PTY resize, special-key handling, shell tabs,
and persistent interactive Compose terminal sessions are not complete.

## Linux sandbox

The project does not ship PRoot, a Debian root filesystem, a verified image
catalog, a Linux installer UI, or Android-native executable packaging. The
installer supports ZIP and tar.gz archives, not tar.xz or OCI layers.

PRoot invocation exists as an adapter but has not been run or verified on an
Android device. Node, Python, Git, Rust, and package managers are not supplied.

## Integrations

Telegram long polling and HTTP MCP are implemented as modules, but bot/server
management UI, secure persisted integration configuration, session routing,
foreground service lifecycle, Telegram approval policies, MCP stdio, and
streamable-SSE MCP responses are not implemented.

## Product functionality

The editor is a real editable text field but has no syntax highlighting, line
numbers, undo history, diff UI, or language-server integration. Project import,
Storage Access Framework, clone, commit, push, checkpoints, context compaction,
local models, notifications, crash recovery, optional cloud adapters, cloud
deployment, and full Arabic UI translation are not implemented.

Rust primitives are standalone and are not integrated through JNI or UniFFI.

These limitations are intentional disclosures; none should be represented as
finished or device-tested features.
