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

Release preparation bundles Ubuntu 24.04 Developer, Alpine Linux 3.24.1, and
an unmodified statically linked PRoot 5.4.0-r2 executable for ARM64.
Ubuntu contains preinstalled Python/pip, Git, Node.js/npm, Java 17, Go, Rust,
Cargo, GCC/G++, Make, CMake, SSH, SQLite, ripgrep, and related developer tools.
The application begins installing Ubuntu automatically and selects it for new
projects. Both the real PTY and agent shell tools use the selected environment.
GitHub Actions verifies the ARM64 executable architecture, launches both ARM64
root filesystems under QEMU, checks all major Ubuntu toolchains, and tests the
application's rootfs installer. Android-device behavior
cannot be verified without a physical device or emulator.

Debian can be imported as a verified ZIP or tar.gz image; tar.xz and OCI layer
imports are unsupported. Android SDK, Flutter SDK, and local AI model weights
are not bundled and can be installed later within Ubuntu.

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
push are available. Manual and automatic project checkpoints can be restored.
Sessions and messages are restored after restart; interrupted jobs are marked
honestly. Importing arbitrary directory trees, repository clone, context compaction,
local models, notifications, crash recovery, optional cloud adapters, cloud
deployment, and language-server integration are not implemented. Core screens
support Arabic and English; provider errors and developer command output remain
in their original language.

Rust primitives are standalone and are not integrated through JNI or UniFFI.

These limitations are intentional disclosures; none should be represented as
finished or device-tested features.
