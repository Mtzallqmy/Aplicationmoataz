# Third-party notices

Alaser AI does not copy source code from OpenCode, Cline, OpenHands, Termux,
or PRoot-Distro.

The architecture was informed by the official upstream projects:

- OpenCode: https://github.com/anomalyco/opencode
- Cline: https://github.com/cline/cline
- OpenHands: https://github.com/OpenHands/OpenHands
- Termux Android application: https://github.com/termux/termux-app
- PRoot-Distro: https://github.com/termux/proot-distro
- RikkaHub: https://github.com/rikkahub/rikkahub (AGPL-3.0; architecture reference only)
- GPT Mobile: https://github.com/Taewan-P/gpt_mobile (GPL-3.0; architecture reference only)
- AIOPE: https://github.com/XNet-NGO/aiope (Business Source License 1.1;
  modification and redistribution are prohibited; no code or binaries copied)

## Incorporated MIT-licensed components

Agora, https://github.com/newo-ether/Agora, Copyright (c) 2026 newo-ether.
`agent/runtime/.../ToolArgumentAccumulator.kt` adapts Agora's defensive tool
argument accumulator. It correctly accepts incremental streaming, duplicate
snapshots, growing snapshots, and empty provider fragments. The complete MIT
license is distributed inside the APK at `assets/licenses/AGORA-MIT.txt`.

Lociant, https://github.com/lhxll07/Lociant, Copyright (c) 2026 Lociant
Contributors. `agent/runtime/.../ToolExecutionPolicy.kt` adapts Lociant's
explicit local/remote, side-effect, destructive, and open-world execution
policy. The complete MIT license is distributed inside the APK at
`assets/licenses/LOCIANT-MIT.txt`.

Runtime and build dependencies are declared in gradle/libs.versions.toml.
Their licenses are summarized in docs/licenses/dependency-audit.md and must be
revalidated against resolved dependency trees before distribution.

The Termux application repository is GPLv3-only, with explicit Apache-2.0
exceptions for terminal-view and terminal-emulator. No Termux or PRoot-Distro
source code is copied or bundled.

The APK bundles Alpine Linux's unmodified `proot-static` 5.4.0-r2 executables,
licensed GPL-2.0-or-later, as separate runnable native artifacts. Upstream source
is available at https://github.com/proot-me/proot and the exact Alpine packaging
recipe is available at https://gitlab.alpinelinux.org/alpine/aports/-/tree/3.24-stable/community/proot.
The corresponding official package metadata is available at
https://pkgs.alpinelinux.org/package/v3.24/community/aarch64/proot-static.

The APK also bundles unmodified Alpine Linux 3.24.1 minirootfs archives for
arm64 and x86_64. Each contained package retains its own upstream license.
Official image downloads and published SHA-256 checksums are available at
https://dl-cdn.alpinelinux.org/alpine/v3.24/releases/.

The APK additionally bundles Ubuntu 24.04 LTS developer root filesystems built
from Canonical's official `ubuntu:24.04` OCI image for arm64 and amd64.
Python, Git, Node.js, npm, OpenJDK, Go, Rust, GCC, and other tools are installed
from official Ubuntu package repositories without source modifications. Every
package retains its individual license and copyright files under
`/usr/share/doc/*/copyright`; source packages are available from Ubuntu's
official repositories and https://launchpad.net/ubuntu.
