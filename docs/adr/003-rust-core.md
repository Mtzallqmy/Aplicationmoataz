# ADR 003: Rust is an optional audited native core

Status: accepted.

Keep small security-sensitive or performance-oriented native primitives in an
isolated Rust crate. Do not introduce JNI, native packaging, or cross-compiling
until there is a concrete product need and an Android toolchain to verify it.
