# Development

## Android requirements

Install a full JDK 17, Android SDK platform 35, build-tools 35, and Gradle
8.10.2. Set ANDROID_HOME to the SDK location or create a local.properties file
that points to the SDK. Do not commit local.properties.

    gradle wrapper --gradle-version 8.10.2
    ./gradlew clean
    ./gradlew :app:assembleDebug
    ./gradlew test
    ./gradlew :app:lintDebug

For a release artifact, configure a protected signing key and use:

    ./gradlew :app:assembleRelease

## Rust

With a Rust toolchain installed:

    cd native/rust-core
    cargo fmt --check
    cargo clippy --all-targets -- -D warnings
    cargo test

The Rust crate is not currently linked through JNI and is not required for the
Android application.

## CI

The Android workflow uses GitHub-hosted JDK and SDK tooling, installs Gradle
8.10.2 through the official Gradle setup action, builds the debug APK, runs
tests and lint, and uploads the APK as a workflow artifact.

The Rust workflow independently runs formatting, Clippy, and unit tests.

## Important execution limitation

The environment where the repository was authored contains a Java runtime and
Java source-launcher compiler modules, but does not include a standalone javac
executable, Gradle, Android SDK, adb, kotlinc, cargo, or rustc. Project
structure and Java-only source contracts can be checked there, but claiming an
Android build or APK exists would be inaccurate.

The available fallback checks are:

    python3 scripts/verify_repository.py
    python3 scripts/test_verify_repository.py
    python3 scripts/verify_jvm_contracts.py

The JVM contract script compiles a temporary Java source file and tests the
actual regular expressions extracted from CommandRiskAnalyzer.kt. These checks
are not a replacement for compiling the Kotlin application or running its
JUnit suite.
