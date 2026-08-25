#!/usr/bin/env python3
"""Dependency-free structural verification for environments without Android SDK."""

from __future__ import annotations

import argparse
import re
import sys
import tomllib
import xml.etree.ElementTree as ElementTree
from pathlib import Path


class VerificationError(Exception):
    """Raised when a repository contract is not satisfied."""


def fail(message: str) -> None:
    raise VerificationError(message)


def verify_balanced_kotlin(path: Path) -> None:
    source = path.read_text(encoding="utf-8")
    pairs = {"(": ")", "[": "]", "{": "}"}
    stack: list[tuple[str, int]] = []
    index = 0
    line = 1
    mode = "code"
    block_depth = 0

    while index < len(source):
        char = source[index]
        following = source[index : index + 3]

        if char == "\n":
            line += 1
            if mode == "line_comment":
                mode = "code"
            index += 1
            continue

        if mode == "line_comment":
            index += 1
            continue

        if mode == "block_comment":
            if source.startswith("/*", index):
                block_depth += 1
                index += 2
            elif source.startswith("*/", index):
                block_depth -= 1
                index += 2
                if block_depth == 0:
                    mode = "code"
            else:
                index += 1
            continue

        if mode == "triple_string":
            if following == '"""':
                mode = "code"
                index += 3
            else:
                index += 1
            continue

        if mode in {"string", "char"}:
            if char == "\\":
                index += 2
            elif (mode == "string" and char == '"') or (mode == "char" and char == "'"):
                mode = "code"
                index += 1
            else:
                index += 1
            continue

        if source.startswith("//", index):
            mode = "line_comment"
            index += 2
        elif source.startswith("/*", index):
            mode = "block_comment"
            block_depth = 1
            index += 2
        elif following == '"""':
            mode = "triple_string"
            index += 3
        elif char == '"':
            mode = "string"
            index += 1
        elif char == "'":
            mode = "char"
            index += 1
        elif char in pairs:
            stack.append((char, line))
            index += 1
        elif char in pairs.values():
            if not stack:
                fail(f"{path}:{line}: unmatched closing delimiter {char}")
            opening, opening_line = stack.pop()
            if pairs[opening] != char:
                fail(
                    f"{path}:{line}: closing delimiter {char} does not match "
                    f"{opening} from line {opening_line}"
                )
            index += 1
        else:
            index += 1

    if mode not in {"code", "line_comment"}:
        fail(f"{path}:{line}: unterminated {mode}")
    if stack:
        opening, opening_line = stack[-1]
        fail(f"{path}:{opening_line}: unclosed delimiter {opening}")
    if not re.search(r"(?m)^package\s+[A-Za-z_][\w.]*\s*$", source):
        fail(f"{path}: Kotlin source must declare its package")


def verify_modules(root: Path) -> list[str]:
    settings = (root / "settings.gradle.kts").read_text(encoding="utf-8")
    modules = re.findall(r'"(:[a-z][a-z0-9:]*)"', settings)
    if len(modules) != len(set(modules)):
        fail("settings.gradle.kts contains duplicate modules")
    if len(modules) < 10:
        fail("The modular Android project is missing expected modules")

    for module in modules:
        directory = root.joinpath(*module.strip(":").split(":"))
        if not (directory / "build.gradle.kts").is_file():
            fail(f"{module}: missing build.gradle.kts")
        if not (directory / "src" / "main").is_dir():
            fail(f"{module}: missing src/main")
        gradle = (directory / "build.gradle.kts").read_text(encoding="utf-8")
        for dependency in re.findall(r'project\("(:[^"]+)"\)', gradle):
            if dependency not in modules:
                fail(f"{module}: references undeclared module {dependency}")
    return modules


def verify_version_catalog(root: Path) -> None:
    with (root / "gradle" / "libs.versions.toml").open("rb") as stream:
        catalog = tomllib.load(stream)
    for section in ("versions", "libraries", "plugins"):
        if not catalog.get(section):
            fail(f"Version catalog is missing [{section}]")
    for name, dependency in catalog["libraries"].items():
        version = dependency.get("version", {})
        if isinstance(version, dict) and "ref" in version:
            if version["ref"] not in catalog["versions"]:
                fail(f"Library {name} references an unknown version")


def verify_android_resources(root: Path) -> None:
    for path in root.rglob("*.xml"):
        if ".git" in path.parts:
            continue
        try:
            ElementTree.parse(path)
        except ElementTree.ParseError as exc:
            fail(f"{path}: invalid XML: {exc}")

    manifest = ElementTree.parse(root / "app/src/main/AndroidManifest.xml").getroot()
    android = "{http://schemas.android.com/apk/res/android}"
    application = manifest.find("application")
    if application is None:
        fail("AndroidManifest.xml is missing its application")
    if application.get(android + "allowBackup") != "false":
        fail("Android backup must be explicitly disabled")
    if application.get(android + "supportsRtl") != "true":
        fail("RTL support must be explicitly enabled")

    security = ElementTree.parse(
        root / "app/src/main/res/xml/network_security_config.xml"
    ).getroot()
    base = security.find("base-config")
    if base is None or base.get("cleartextTrafficPermitted") != "false":
        fail("Remote cleartext HTTP must be disabled by default")


def require_contains(root: Path, relative: str, *snippets: str) -> None:
    source = (root / relative).read_text(encoding="utf-8")
    for snippet in snippets:
        if snippet not in source:
            fail(f"{relative}: missing required source contract: {snippet}")


def verify_security_contracts(root: Path) -> None:
    require_contains(
        root,
        "core/filesystem/src/main/kotlin/ai/alaser/core/filesystem/WorkspaceFileSystem.kt",
        "!candidate.startsWith(rootPath)",
        "Files.isSymbolicLink",
        '"Sensitive files require explicit approval."',
        "!destinationPath.startsWith(targetRoot)",
    )
    require_contains(
        root,
        "core/security/src/main/kotlin/ai/alaser/core/security/AndroidSecretStore.kt",
        '"AndroidKeyStore"',
        '"AES/GCM/NoPadding"',
        ".setKeySize(256)",
    )
    require_contains(
        root,
        "core/model/src/main/kotlin/ai/alaser/core/model/Models.kt",
        "allowedUserIds.isNotEmpty()",
        "userId in allowedUserIds",
    )
    require_contains(
        root,
        "agent/runtime/src/main/kotlin/ai/alaser/agent/runtime/ApprovalEngine.kt",
        "mode == AgentMode.PLAN && risk != RiskLevel.SAFE",
        "risk == RiskLevel.CRITICAL",
    )
    require_contains(
        root,
        "KNOWN_LIMITATIONS.md",
        "GitHub Actions",
        "Agent tool commands still use process pipes",
        "does not ship PRoot",
        "No physical Android device or emulator",
    )


def verify_workflows(root: Path) -> None:
    expected = {
        "android-build.yml": [":app:assembleDebug", "gradle test", ":app:lintDebug", "app-debug.apk"],
        "rust.yml": ["cargo fmt --check", "cargo clippy", "cargo test"],
    }
    for filename, required in expected.items():
        require_contains(root, ".github/workflows/" + filename, *required)


def verify(root: Path) -> dict[str, int]:
    modules = verify_modules(root)
    verify_version_catalog(root)
    verify_android_resources(root)
    verify_security_contracts(root)
    verify_workflows(root)

    kotlin_sources = [
        path
        for path in root.rglob("*.kt")
        if ".git" not in path.parts and "build" not in path.parts
    ]
    for source in kotlin_sources:
        verify_balanced_kotlin(source)

    tests = [path for path in kotlin_sources if "/src/test/" in path.as_posix()]
    if len(tests) < 7:
        fail("Expected JVM tests for filesystem, providers, terminal, approvals, and agent runtime")

    return {
        "modules": len(modules),
        "kotlin_sources": len(kotlin_sources),
        "kotlin_test_files": len(tests),
        "xml_files": len(list(root.rglob("*.xml"))),
        "adr_files": len(list((root / "docs/adr").glob("*.md"))),
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    arguments = parser.parse_args()
    try:
        summary = verify(arguments.root.resolve())
    except (OSError, VerificationError, tomllib.TOMLDecodeError) as exc:
        print("FAIL:", exc, file=sys.stderr)
        return 1
    print("Repository structural checks passed.")
    for key, value in summary.items():
        print(f"  {key}: {value}")
    print("These checks do not compile Kotlin, execute Android tests, or produce an APK.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
