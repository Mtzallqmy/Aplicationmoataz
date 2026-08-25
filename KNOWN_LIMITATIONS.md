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

Release preparation bundles official Alpine Linux 3.24.1 root filesystems and
unmodified statically linked PRoot 5.4.0-r2 executables for ARM64 and x86_64.
Users can install Alpine offline; both the real PTY and agent shell tools use
the Linux environment selected for their workspace. GitHub Actions exercises
the actual x86_64 PRoot binary and rootfs together. Android-device behavior
cannot be verified without a physical device or emulator.

Alpine includes its `apk` package manager; Node.js, Python, Git, and Rust are
not preinstalled and must be installed in the selected environment when needed.
Debian and Ubuntu can be imported as SHA-256-verified user-supplied ZIP or
tar.gz archives, but are not bundled. tar.xz and OCI layers are unsupported.

## Integrations

Telegram and MCP have functional management screens, persisted settings,
encrypted bot tokens, allowlists, tool inspection, and trust controls.
Agent work and Telegram polling use a visible Android foreground service while
the application process is alive; they cannot survive Android process death or
restore jobs after reboot. Telegram-specific approval policies, MCP stdio,
streamable-SSE MCP responses, and automatic MCP-tool registration in the agent
loop are not implemented.

## Product functionality

The editor is a real editable text field but has no syntax highlighting, line
numbers, undo history, diff UI, or language-server integration. ZIP project
import uses Android's Storage Access Framework; Git status, diff, history,
branches, initialization, staging, commits, pull, and explicitly confirmed
push are available when Git is installed. Importing arbitrary directory trees,
repository clone, checkpoints, context compaction,
local models, notifications, crash recovery, optional cloud adapters, cloud
deployment, and full Arabic UI translation are not implemented.

Rust primitives are standalone and are not integrated through JNI or UniFFI.

These limitations are intentional disclosures; none should be represented as
finished or device-tested features.
