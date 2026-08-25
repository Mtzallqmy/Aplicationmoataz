# Third-party notices

Alaser AI does not copy source code from OpenCode, Cline, OpenHands, Termux,
or PRoot-Distro.

The architecture was informed by the official upstream projects:

- OpenCode: https://github.com/anomalyco/opencode
- Cline: https://github.com/cline/cline
- OpenHands: https://github.com/OpenHands/OpenHands
- Termux Android application: https://github.com/termux/termux-app
- PRoot-Distro: https://github.com/termux/proot-distro

Runtime and build dependencies are declared in gradle/libs.versions.toml.
Their licenses are summarized in docs/licenses/dependency-audit.md and must be
revalidated against resolved dependency trees before distribution.

The Termux application repository is GPLv3-only, with explicit Apache-2.0
exceptions for terminal-view and terminal-emulator. PRoot-Distro includes GPL
license terms. Neither project's source or executables are bundled here.
