#!/usr/bin/env bash
set -euo pipefail

alaser_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
alaser_version="3.24.1"
alaser_proot_version="5.4.0-r2"
alaser_assets="$alaser_root/app/src/main/assets/linux"
alaser_staging="$(mktemp -d)"
trap 'rm -rf "$alaser_staging"' EXIT
mkdir -p "$alaser_assets"
: > "$alaser_assets/manifest.properties"

prepare_architecture() {
    local alpine_arch="$1"
    local android_abi="$2"
    local base="https://dl-cdn.alpinelinux.org/alpine"
    local rootfs="alpine-minirootfs-${alaser_version}-${alpine_arch}.tar.gz"
    local rootfs_url="$base/v3.24/releases/$alpine_arch/$rootfs"
    local package_url="$base/v3.24/community/$alpine_arch/proot-static-${alaser_proot_version}.apk"
    # AAPT transparently unpacks assets ending in .gz; keep the verified bytes intact.
    local rootfs_file="$alaser_assets/alpine-$alpine_arch.rootfs"
    local package_file="$alaser_staging/proot-$alpine_arch.apk"
    local extracted="$alaser_staging/$alpine_arch"
    local jni_directory="$alaser_root/app/src/main/jniLibs/$android_abi"

    echo "Preparing verified Alpine Linux and static PRoot for $android_abi"
    curl --fail --silent --show-error --location "$rootfs_url" --output "$rootfs_file"
    curl --fail --silent --show-error --location "$rootfs_url.sha256" --output "$alaser_staging/$rootfs.sha256"
    local expected
    expected="$(awk '{print $1}' "$alaser_staging/$rootfs.sha256")"
    printf '%s  %s\n' "$expected" "$rootfs_file" | sha256sum --check --status

    curl --fail --silent --show-error --location "$package_url" --output "$package_file"
    mkdir -p "$extracted" "$jni_directory"
    tar -xzf "$package_file" -C "$extracted" usr/bin/proot.static
    install -m 0755 "$extracted/usr/bin/proot.static" "$jni_directory/libproot_exec.so"
    file "$jni_directory/libproot_exec.so"

    printf '%s.filename=alpine-%s.rootfs\n' "$android_abi" "$alpine_arch" >> "$alaser_assets/manifest.properties"
    printf '%s.sha256=%s\n' "$android_abi" "$expected" >> "$alaser_assets/manifest.properties"
    printf '%s.version=%s\n' "$android_abi" "$alaser_version" >> "$alaser_assets/manifest.properties"
}

prepare_architecture aarch64 arm64-v8a
prepare_architecture x86_64 x86_64

echo "Prepared offline Linux environments and bundled PRoot executables."
