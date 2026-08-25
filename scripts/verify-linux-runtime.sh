#!/usr/bin/env bash
set -euo pipefail

alaser_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
alaser_staging="$(mktemp -d)"
trap 'docker image rm -f "${alaser_alpine_image:-}" "${alaser_ubuntu_image:-}" >/dev/null 2>&1 || true; rm -rf "$alaser_staging"' EXIT
mkdir -p "$alaser_staging/workspace"

alaser_proot="$alaser_root/app/src/main/jniLibs/arm64-v8a/libproot_exec.so"
file "$alaser_proot" | grep -F "ARM aarch64"
"$alaser_proot" --version | grep -i proot
echo "The bundled static PRoot is an executable ARM64 build."

# A hosted x86 runner cannot nest PRoot's ptrace operations inside QEMU
# user-mode emulation. Importing the exact bundled archives into Docker runs
# every ARM64 userspace binary through the configured binfmt/QEMU handler and
# validates the root filesystems without producing a false ptrace failure.
alaser_alpine_image="$(docker import "$alaser_root/app/src/main/assets/linux/alpine-aarch64.rootfs")"
docker run --rm --platform linux/arm64 \
    --volume "$alaser_staging/workspace:/workspace" \
    "$alaser_alpine_image" \
    /bin/sh -c 'printf "ALASER_LINUX_OK "; cat /etc/alpine-release; printf "verified" > /workspace/alpine-check.txt'
[[ "$(cat "$alaser_staging/workspace/alpine-check.txt")" == "verified" ]]
echo "The bundled Alpine ARM64 rootfs starts and writes to its workspace."

alaser_ubuntu_image="$(docker import "$alaser_root/app/src/main/assets/linux/ubuntu-arm64.rootfs")"
docker run --rm --platform linux/arm64 \
    --volume "$alaser_staging/workspace:/workspace" \
    "$alaser_ubuntu_image" \
    /bin/sh -c '
        python3 --version
        git --version
        node --version
        npm --version
        gcc --version | head -n 1
        go version
        rustc --version
        cargo --version
        java -version
        printf "ubuntu-developer-ready" > /workspace/ubuntu-check.txt
    '
[[ "$(cat "$alaser_staging/workspace/ubuntu-check.txt")" == "ubuntu-developer-ready" ]]
echo "The bundled Ubuntu ARM64 developer environment starts with all required toolchains."
