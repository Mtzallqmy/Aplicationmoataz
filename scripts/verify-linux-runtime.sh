#!/usr/bin/env bash
set -euo pipefail

alaser_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
alaser_staging="$(mktemp -d)"
trap 'rm -rf "$alaser_staging"' EXIT
mkdir -p "$alaser_staging/rootfs" "$alaser_staging/workspace" "$alaser_staging/tmp"
tar -xzf "$alaser_root/app/src/main/assets/linux/alpine-x86_64.tar.gz" -C "$alaser_staging/rootfs"

output="$(
    PROOT_TMP_DIR="$alaser_staging/tmp" \
    "$alaser_root/app/src/main/jniLibs/x86_64/libproot_exec.so" \
        --rootfs="$alaser_staging/rootfs" \
        --bind="$alaser_staging/workspace:/workspace" \
        --bind=/dev \
        --bind=/proc \
        --cwd=/workspace \
        --link2symlink \
        /bin/sh -c 'printf "ALASER_LINUX_OK "; cat /etc/alpine-release; printf "verified" > /workspace/proot-check.txt'
)"
echo "$output"
[[ "$output" == ALASER_LINUX_OK* ]]
[[ "$(cat "$alaser_staging/workspace/proot-check.txt")" == "verified" ]]
echo "The bundled static PRoot starts Alpine Linux and writes to its bound workspace."
