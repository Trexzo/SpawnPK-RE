#!/usr/bin/env python3
"""R20 pure/Python-fixture regression; no proprietary client JAR is required."""
from __future__ import annotations

from pathlib import Path
import hashlib
import json
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent))
import r20_original_v308_offline_suite as r20


class OriginalV308OfflineSuiteTest(unittest.TestCase):
    def test_sealed_manifest_has_179_exact_ordered_unique_test_classes(self):
        repo = Path(__file__).resolve().parents[2]
        text = (repo / "RUN_V5185_FULL_SELFTEST.ps1").read_text(encoding="utf-8")
        names = r20.sealed_manifest(text)
        self.assertEqual(len(names), 179)
        self.assertEqual(len(set(names)), 179)
        self.assertTrue(all(repr(name).find("Test") > 0 for name in names))

    def test_manifest_mutation_and_unrecognized_class_rejected(self):
        repo = Path(__file__).resolve().parents[2]
        source = (repo / "RUN_V5185_FULL_SELFTEST.ps1").read_text(encoding="utf-8")
        names = r20.sealed_manifest(source)
        modified = source.replace("'" + names[1] + "'",
                                  "'" + names[0] + "'", 1)
        with self.assertRaises(r20.AdmissionDenied):
            r20.sealed_manifest(modified)
        with self.assertRaises(r20.AdmissionDenied):
            r20.sealed_manifest(source.replace(
                "'" + names[-1] + "'", "'NonSealedTest'", 1))
        with self.assertRaises(r20.AdmissionDenied):
            r20.sealed_manifest("$tests=@(\n'BadTest'\n)\n")

    def test_private_parent_accepts_ancestor_temp_not_inside_repo(self):
        with tempfile.TemporaryDirectory(prefix="spk-r20-unit-") as root:
            folder = Path(root)
            repo = folder / "private-source-checkout"
            repo.mkdir()
            self.assertEqual(r20.safe_private_parent(folder, repo),
                             folder.resolve())
            with self.assertRaises(r20.AdmissionDenied):
                r20.safe_private_parent(repo, repo)
            descendant = repo / "bad-private-parent"
            descendant.mkdir()
            with self.assertRaises(r20.AdmissionDenied):
                r20.safe_private_parent(descendant, repo)

    def test_input_hash_is_binary_exact(self):
        with tempfile.TemporaryDirectory(prefix="spk-r20-digest-") as root:
            p = Path(root) / "original"
            p.write_bytes(b"v308-not-exact")
            self.assertEqual(r20.sha256_file(p),
                             hashlib.sha256(b"v308-not-exact").hexdigest())
            self.assertNotEqual(r20.sha256_file(p),
                                r20.ORIGINAL_V308_SHA256)

    def run_mock_suite(self, count: int, exit_code: int = 0):
        with tempfile.TemporaryDirectory(prefix="spk-r20-unit-") as folder:
            root = Path(folder)
            repo = root / "repo"
            repo.mkdir()
            original = root / "v308.jar"
            original.write_bytes(b"synthetic fixture; never run as client")
            jar = root / "server.jar"
            jar.write_bytes(b"synthetic server")
            java = root / ("java.exe" if sys.platform == "win32" else "java")
            java.write_bytes(b"not invoked during mocked run")
            names = tuple("Fixture%dTest" % i for i in range(179))
            jar_sha = hashlib.sha256(jar.read_bytes()).hexdigest()
            parent = root
            proc_calls = []

            def fake_run(args, **kwargs):
                proc_calls.append((args, kwargs))
                return subprocess.CompletedProcess(args, exit_code)

            def fake_sha(path):
                if path == original:
                    return r20.ORIGINAL_V308_SHA256
                if path == jar:
                    return jar_sha
                self.fail("unexpected file hash")
            with mock.patch.object(r20.subprocess, "run",
                                   side_effect=fake_run), mock.patch.object(
                                       r20, "sha256_file", side_effect=fake_sha):
                if exit_code != 0:
                    with self.assertRaises(r20.AdmissionDenied):
                        r20.run_suite(repo, jar, jar_sha, original, java,
                                      parent, names, count, 10)
                else:
                    result = r20.run_suite(
                        repo, jar, jar_sha, original, java,
                        parent, names, count, 10)
                    self.assertEqual(result["tests_passed"], count)
                    self.assertEqual(
                        result["result"],
                        "R20_ORIGINAL_V308_OFFLINE_179_PASS"
                        if count == 179 else
                        "R20_ORIGINAL_V308_OFFLINE_PARTIAL_PASS")
                    self.assertTrue(result["input_unchanged"])
            self.assertEqual(len(proc_calls),
                             1 if exit_code != 0 else count)
            self.assertTrue(all(
                "spk.local.Fixture" in args[-1] and
                args[args.index("-cp") + 1].find(str(original)) >= 0 and
                kwargs["stdin"] == subprocess.DEVNULL and
                kwargs["env"]["HOME"] != str(Path.home())
                for args, kwargs in proc_calls
            ))
            receipts = list(parent.glob(
                "spawnpk-r20-offline-*/r20-offline-parity-receipt.json"))
            self.assertEqual(len(receipts), 1)
            data = json.loads(receipts[0].read_text(encoding="utf-8"))
            if exit_code != 0:
                self.assertEqual(
                    data["result"], "R20_ORIGINAL_V308_OFFLINE_FAILED")
                self.assertEqual(data["tests_passed"], 0)

    def test_partial_never_claims_full_pass(self):
        self.run_mock_suite(3)

    def test_full_manifest_claim_needs_all_179_separate_jvms(self):
        self.run_mock_suite(179)

    def test_failed_child_fails_closed_and_writes_failure_receipt(self):
        self.run_mock_suite(3, exit_code=7)


if __name__ == "__main__":
    unittest.main()
