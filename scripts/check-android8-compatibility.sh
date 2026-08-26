#!/usr/bin/env bash
set -euo pipefail

alaser_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$alaser_root"

# These Java convenience APIs compile with JDK 17 but do not exist in the
# Android 8 (API 26) core library. They caused the verified device crash in
# v0.1.0, so CI must fail closed if either an incompatible API is found or the
# source scan itself cannot run.
forbidden='Path\.of\(|Files\.(writeString|readString)\('

set +e
matches="$(grep -R -n -E --include='*.kt' --include='*.java' \
    "$forbidden" app core agent ai integration native 2>&1)"
scan_status=$?
set -e

if (( scan_status == 0 )); then
    printf '%s\n' "$matches"
    echo "Android 8 incompatible Java API detected." >&2
    exit 1
fi

# GNU grep returns 1 when no lines matched. Any other status means the guard
# itself failed, which must never be reported as a successful compatibility
# check.
if (( scan_status != 1 )); then
    printf '%s\n' "$matches" >&2
    echo "Android 8 compatibility scan failed to execute reliably." >&2
    exit "$scan_status"
fi

echo "Android 8 source compatibility guard passed."
