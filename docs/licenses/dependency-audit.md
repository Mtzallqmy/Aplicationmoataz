# Dependency and architecture-reference audit

Audit date: 2026-08-25.

| Component | Source | Declared version | License | Use | Copied code |
| --- | --- | --- | --- | --- | --- |
| Android Gradle Plugin | developer.android.com/build | 8.8.2 | Android/Apache terms; verify artifact | Android build | No |
| Kotlin | github.com/JetBrains/kotlin | 2.1.10 | Apache-2.0 | Kotlin compiler/plugins | No |
| Jetpack Compose BOM | developer.android.com/develop/ui/compose/bom | 2025.02.00 | Apache-2.0 | Android UI dependencies | No |
| AndroidX Activity | developer.android.com/jetpack/androidx/releases/activity | 1.10.1 | Apache-2.0 | Compose activity | No |
| AndroidX Lifecycle | developer.android.com/jetpack/androidx/releases/lifecycle | 2.8.7 | Apache-2.0 | ViewModel/lifecycle | No |
| AndroidX Navigation | developer.android.com/jetpack/androidx/releases/navigation | 2.8.8 | Apache-2.0 | Compose navigation | No |
| kotlinx.coroutines | github.com/Kotlin/kotlinx.coroutines | 1.10.1 | Apache-2.0 | Coroutine execution | No |
| kotlinx.serialization | github.com/Kotlin/kotlinx.serialization | 1.8.0 | Apache-2.0 | JSON/domain serialization | No |
| OkHttp | github.com/square/okhttp | 4.12.0 | Apache-2.0 | HTTP/SSE clients | No |
| Commons Compress | commons.apache.org/proper/commons-compress | 1.27.1 | Apache-2.0 | Safe tar archive reading | No |
| PRoot static executable | pkgs.alpinelinux.org/package/v3.24/community/aarch64/proot-static | 5.4.0-r2 | GPL-2.0-or-later | Bundled rootless Linux execution, arm64 and x86_64 | Official unmodified binary |
| Alpine Linux minirootfs | dl-cdn.alpinelinux.org/alpine/v3.24/releases | 3.24.1 | Per-package open-source licenses | Bundled offline Linux root filesystem | Official unmodified archive |
| Ubuntu base image | hub.docker.com/_/ubuntu | 24.04 LTS | Per-package open-source licenses | Bundled Ubuntu developer rootfs, arm64 and amd64 | Official image plus Ubuntu package-manager installs |
| Ubuntu developer packages | archive.ubuntu.com / ports.ubuntu.com | Ubuntu 24.04 package versions | GPL/LGPL/Apache/MIT and other per-package licenses | Offline Python, Git, Node, Java, Go, Rust, GCC and utilities | Official unmodified packages |
| JUnit 4 | github.com/junit-team/junit4 | 4.13.2 | EPL-1.0 | Unit tests only | No |
| OpenCode | github.com/anomalyco/opencode | Reference only | MIT | Agent architecture reference | No |
| Agora | github.com/newo-ether/Agora | Reviewed 2026-08-25 | MIT, Copyright (c) 2026 newo-ether | Defensive streamed tool-argument accumulator | Yes; adapted with full bundled MIT notice |
| Lociant | github.com/lhxll07/Lociant | Reviewed 2026-08-25 | MIT, Copyright (c) 2026 Lociant Contributors | Explicit local execution and side-effect policy | Yes; adapted with full bundled MIT notice |
| RikkaHub | github.com/rikkahub/rikkahub | Reference only | AGPL-3.0 | Multi-provider Android architecture reference | No; no copyleft-covered code copied |
| GPT Mobile | github.com/Taewan-P/gpt_mobile | Reference only | GPL-3.0 | Provider and agent persistence architecture reference | No; no copyleft-covered code copied |
| AIOPE | github.com/XNet-NGO/aiope | Reference only | Business Source License 1.1; modification and redistribution prohibited | Android terminal/tool architecture reference | No; copying and redistribution prohibited |
| Cline | github.com/cline/cline | Reference only | Apache-2.0 | Approval/tool architecture reference | No |
| Termux app | github.com/termux/termux-app | Reference only | GPL-3.0-only; terminal-view and terminal-emulator exceptions Apache-2.0 | Android terminal reference | No |
| PRoot-Distro | github.com/termux/proot-distro | Reference only | GPL terms; review source tree before use | Rootfs lifecycle reference | No |
| OpenHands | github.com/OpenHands/OpenHands | Reference only | Verify before any code reuse | Sandbox architecture reference | No |

GitHub Actions resolves, builds, tests, and lint-checks the declared dependencies.
The official root filesystem SHA-256 is verified before packaging. PRoot remains
an unmodified, separately executable GPL-2.0-or-later component; upstream source:
https://github.com/proot-me/proot. Alpine package build definitions:
https://gitlab.alpinelinux.org/alpine/aports/-/tree/3.24-stable/community/proot.
