# ADR 002: Kotlin and Jetpack Compose

Status: accepted.

Use Kotlin for product and agent orchestration and Compose for native Android
UI. Keep domain libraries independently testable on the JVM. Support Android
API 26 and newer to retain the platform filesystem and cryptographic APIs used
by the initial implementation.
