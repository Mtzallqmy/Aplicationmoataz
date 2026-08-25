#!/usr/bin/env bash
set -euo pipefail

alaser_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$alaser_root"

# These Java convenience APIs compile with JDK 17 but do not exist in the
# Android 8 (API 26) core library. They caused the verified device crash in
# v0.1.0, so CI rejects them before the APK is assembled.
forbidden='Path\.of\(|Files\.(writeString|readString)\('
if rg --glob '*.kt' --glob '*.java' "$forbidden" app core agent ai integration native; then
    echo "Android 8 incompatible Java API detected." >&2
    exit 1
fi

echo "Android 8 source compatibility guard passed."
