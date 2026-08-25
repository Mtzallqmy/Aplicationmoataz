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
| JUnit 4 | github.com/junit-team/junit4 | 4.13.2 | EPL-1.0 | Unit tests only | No |
| OpenCode | github.com/anomalyco/opencode | Reference only | MIT | Agent architecture reference | No |
| Cline | github.com/cline/cline | Reference only | Apache-2.0 | Approval/tool architecture reference | No |
| Termux app | github.com/termux/termux-app | Reference only | GPL-3.0-only; terminal-view and terminal-emulator exceptions Apache-2.0 | Android terminal reference | No |
| PRoot-Distro | github.com/termux/proot-distro | Reference only | GPL terms; review source tree before use | Rootfs lifecycle reference | No |
| OpenHands | github.com/OpenHands/OpenHands | Reference only | Verify before any code reuse | Sandbox architecture reference | No |

The build environment could not resolve Gradle dependencies. Versions,
transitive dependencies, current advisories, and distribution notices must be
rechecked after a real Gradle build.
