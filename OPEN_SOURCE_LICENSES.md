# Open-source licenses

## Intended direct dependency licenses

- Agora tool-call argument accumulator: MIT, Copyright (c) 2026 newo-ether.
  Full notice bundled at `assets/licenses/AGORA-MIT.txt`.
- Lociant explicit tool execution policy: MIT, Copyright (c) 2026 Lociant
  Contributors. Full notice bundled at `assets/licenses/LOCIANT-MIT.txt`.
- RikkaHub (AGPL-3.0), GPT Mobile (GPL-3.0), and AIOPE (Business Source
  License 1.1) are architecture references only; none of their code or
  binaries are copied, linked, or distributed.
- Bundled PRoot 5.4.0-r2: GPL-2.0-or-later, distributed unmodified as a
  separate executable. Upstream corresponding source: https://github.com/proot-me/proot.
- Bundled Alpine Linux 3.24.1: each included Alpine package retains its own
  license and attribution. Image source: https://dl-cdn.alpinelinux.org/alpine/v3.24/releases/.

- Kotlin, kotlinx.coroutines, and kotlinx.serialization: Apache License 2.0.
- AndroidX, Jetpack Compose, Material 3, and Android Gradle Plugin: Apache
  License 2.0 or the applicable Android SDK/build-tool terms.
- OkHttp: Apache License 2.0.
- Apache Commons Compress: Apache License 2.0.
- JUnit 4: Eclipse Public License 1.0.

This file is an initial source audit, not a generated software bill of
materials. Exact transitive licenses and notices must be checked after Gradle
dependency resolution and before publishing an APK.

The repository does not include copied GPL-covered Termux or PRoot-Distro
source. Separately distributing a PRoot executable or distribution rootfs
requires its own redistribution and compliance review.
