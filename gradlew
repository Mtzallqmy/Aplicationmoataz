#!/usr/bin/env sh
set -eu

PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
WRAPPER_JAR="$PROJECT_DIR/gradle/wrapper/gradle-wrapper.jar"

if [ -f "$WRAPPER_JAR" ]; then
    exec java -classpath "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
fi

if command -v gradle >/dev/null 2>&1; then
    exec gradle "$@"
fi

printf '%s\n' \
    "Gradle is unavailable and gradle-wrapper.jar is not present." \
    "Install Gradle 8.10.2, then run: gradle wrapper --gradle-version 8.10.2" \
    "Android builds also require JDK 17 and Android SDK platform 35." >&2
exit 1
