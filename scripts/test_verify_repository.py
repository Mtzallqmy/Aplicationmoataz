from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

import verify_repository


class KotlinDelimiterScannerTest(unittest.TestCase):
    def check_source(self, source: str) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory, "Example.kt")
            path.write_text(source, encoding="utf-8")
            verify_repository.verify_balanced_kotlin(path)

    def test_accepts_strings_comments_and_nested_delimiters(self) -> None:
        self.check_source(
            'package example\n'
            '/* { ignored /* nested */ } */\n'
            'fun example() { val value = "{ not a brace }"; println(value) }\n'
        )

    def test_accepts_triple_quoted_strings(self) -> None:
        self.check_source(
            'package example\n'
            'fun example() { val value = """{"json":true}"""; println(value) }\n'
        )

    def test_rejects_unclosed_braces(self) -> None:
        with self.assertRaises(verify_repository.VerificationError):
            self.check_source("package example\nfun example() {\n")

    def test_rejects_mismatched_delimiters(self) -> None:
        with self.assertRaises(verify_repository.VerificationError):
            self.check_source("package example\nfun example() { listOf(1, 2] }\n")

    def test_requires_package_declaration(self) -> None:
        with self.assertRaises(verify_repository.VerificationError):
            self.check_source("fun example() = 1\n")


class RepositoryContractTest(unittest.TestCase):
    def test_generated_repository_contract(self) -> None:
        root = Path(__file__).resolve().parents[1]
        result = verify_repository.verify(root)
        self.assertGreaterEqual(result["modules"], 10)
        self.assertGreaterEqual(result["kotlin_test_files"], 7)


if __name__ == "__main__":
    unittest.main(verbosity=2)
