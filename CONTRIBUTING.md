# Contributing

1. Preserve local-first operation and explicit user approval boundaries.
2. Add or update tests when changing agent, filesystem, command, or secret code.
3. Record significant architecture decisions under docs/adr.
4. Audit every added dependency and update docs/licenses/dependency-audit.md.
5. Do not copy GPL-covered Termux or PRoot-Distro code without an explicit
   repository-wide licensing decision.
6. Run Android build, unit tests, lint, and Rust checks where their toolchains
   are available.
7. Never commit credentials, bot tokens, keystores, generated APKs, rootfs
   images, or local model files.
