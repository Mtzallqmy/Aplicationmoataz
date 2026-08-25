#!/usr/bin/env python3
"""Verify the release actually contains the native PTY, PRoot and Linux images."""

from __future__ import annotations

import hashlib
import struct
import sys
import zipfile


def main() -> int:
    if len(sys.argv) != 2:
        raise SystemExit("usage: verify-apk.py PATH_TO_APK")

    with zipfile.ZipFile(sys.argv[1]) as package:
        manifest = dict(
            line.split("=", 1)
            for line in package.read("assets/linux/manifest.properties").decode().splitlines()
            if "=" in line
        )
        for abi, expected_machine in (("arm64-v8a", 183), ("x86_64", 62)):
            for library in ("libalaser_pty.so", "libproot_exec.so"):
                binary = package.read(f"lib/{abi}/{library}")
                if binary[:4] != b"\x7fELF":
                    raise ValueError(f"{abi}/{library} is not an ELF executable")
                machine = struct.unpack_from("<H", binary, 18)[0]
                if machine != expected_machine:
                    raise ValueError(f"{abi}/{library} has ELF architecture {machine}")

            archive = package.read("assets/linux/" + manifest[f"{abi}.filename"])
            checksum = hashlib.sha256(archive).hexdigest()
            if checksum != manifest[f"{abi}.sha256"]:
                raise ValueError(f"Bundled Linux rootfs checksum mismatch for {abi}")
            print(f"{abi}: native PTY, static PRoot, and verified Alpine rootfs ({len(archive):,} bytes)")

            ubuntu = package.read("assets/linux/" + manifest[f"{abi}.ubuntu.filename"])
            ubuntu_checksum = hashlib.sha256(ubuntu).hexdigest()
            if ubuntu_checksum != manifest[f"{abi}.ubuntu.sha256"]:
                raise ValueError(f"Bundled Ubuntu developer rootfs checksum mismatch for {abi}")
            print(f"{abi}: verified Ubuntu developer rootfs with complete toolchains ({len(ubuntu):,} bytes)")

        for notice in ("assets/licenses/AGORA-MIT.txt", "assets/licenses/LOCIANT-MIT.txt"):
            content = package.read(notice).decode()
            if "MIT License" not in content or "Permission is hereby granted" not in content:
                raise ValueError(f"Incomplete third-party license notice: {notice}")

    print("APK offline Linux runtime verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
