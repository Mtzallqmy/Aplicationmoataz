# ADR 004: Distinguish process shell, PTY, and PRoot

Status: accepted.

The application owns a small original Android NDK/JNI PTY implementation for
interactive shell sessions. Agent command execution remains independently
process-backed. Rootfs installation and PRoot invocation are separate
capabilities. Do not bundle GPL-covered upstream artifacts without a deliberate
licensing decision, and do not present unverified PRoot execution as a
completed sandbox.
