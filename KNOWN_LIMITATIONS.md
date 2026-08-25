# Known limitations

## Build environment

Android compilation, unit tests, lint, and APK generation are performed by
GitHub Actions because the local authoring sandbox does not include an Android
SDK or Gradle. The wrapper properties are present, but gradle-wrapper.jar is
not checked in; CI provisions Gradle 8.10.2 directly.

No physical Android device or emulator is available in the current session, so
installation and interactive device behavior cannot be directly exercised.

## Terminal

The application includes a genuine NDK/JNI interactive pseudo-terminal and
basic control keys. Agent tool commands still use process pipes rather than
sharing the interactive PTY. ANSI rendering, alternate-screen display, full
special-key handling, shell tabs, and terminal emulation are not complete.

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
