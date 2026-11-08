#!/usr/bin/env python3
"""R27 synthetic GUI bootstrapping and native binary contract tests."""
from __future__ import annotations

from contextlib import contextmanager
import hashlib
from pathlib import Path
import struct
import sys
import tempfile
import unittest
from unittest import mock
from zipfile import ZipFile, ZIP_STORED

sys.path.insert(0, str(Path(__file__).resolve().parent))
import r27_v308_gui_bootstrap_audit as r27
from test_r22_v308_classfile_native_egress_inventory import class_fixture


def fixture_class(*calls) -> bytes:
    data = bytearray(class_fixture(tuple(calls)))
    data[6:8] = struct.pack(">H", r27.EXPECTED_CLASS_MAJOR)
    return bytes(data)


MAIN_CALLS = (
    ("rs/gui/M", "a", "()V"),
    ("rs/p/a", "a", "()V"),
)
GUICE_CALLS = (
    ("com/google/inject/Guice", "createInjector",
     "([Lcom/google/inject/Module;)Lcom/google/inject/Injector;"),
    ("com/google/inject/Injector", "getInstance",
     "(Ljava/lang/Class;)Ljava/lang/Object;"),
)
UI_CALLS = (
    ("javax/swing/SwingUtilities", "invokeLater",
     "(Ljava/lang/Runnable;)V"),
)


@contextmanager
def fake_jar(main="rs.gui.Launcher", no_guice=False,
             bad_native=False, bad_major=False):
    with tempfile.TemporaryDirectory(prefix="r27-fixture-") as td:
        path = Path(td) / "client.jar"
        entries = {
            "META-INF/MANIFEST.MF":
                ("Manifest-Version: 1.0\r\nMain-Class: "
                 + main + "\r\n\r\n").encode("utf-8"),
            r27.MAIN_PATH: fixture_class(*MAIN_CALLS),
            "rs/p/a.class": fixture_class(*(()
                                          if no_guice else GUICE_CALLS)),
            "rs/ui/f.class": fixture_class(*UI_CALLS),
            "rs/Client.class": fixture_class(),
        }
        if bad_major:
            change = bytearray(entries["rs/ui/f.class"])
            change[6:8] = struct.pack(">H", 55)
            entries["rs/ui/f.class"] = bytes(change)
        for name in r27.WINDOWS_AMD64_NATIVE:
            entries[name] = b"x" * 12_000
        if bad_native:
            entries[r27.WINDOWS_AMD64_NATIVE[0]] = b"changed" * 1_800
        with ZipFile(path, "w", ZIP_STORED) as archive:
            for name, data in entries.items():
                archive.writestr(name, data)
        with mock.patch.object(r27, "EXPECTED_CLASSES", 4):
            yield path


class R27GuiBootstrapTests(unittest.TestCase):
    def test_exact_manifest_guice_swing_and_windows_native_contract(self):
        with fake_jar() as path:
            sha = r27.sha256_file(path)
            result = r27.inspect(path, sha)
            self.assertTrue(result["bootstrap_references_present"])
            self.assertEqual(result["main_class"], "rs.gui.Launcher")
            self.assertEqual(result["class_count"], 4)
            self.assertEqual(result["class_major"]["rs/gui/Launcher.class"],
                             53)
            self.assertEqual(len(result["windows_amd64_native"]), 4)
            final = r27.compare(result, result)
            self.assertEqual(final["result"],
                             "R27_PINNED_V308_REAL_GUI_BOOTSTRAP_STATIC_AUDIT_PASS")
            self.assertFalse(final["client_gui_started"])
            self.assertFalse(final["network_isolation_verified"])

    def test_wrong_jar_digest_refused(self):
        with fake_jar() as path:
            with self.assertRaises(r27.BootstrapDenied):
                r27.inspect(path, "0" * 64)

    def test_wrong_manifest_refused(self):
        with fake_jar(main="rs.Client") as path:
            with self.assertRaises(r27.BootstrapDenied):
                r27.inspect(path, r27.sha256_file(path))

    def test_missing_guice_injected_gui_refused(self):
        with fake_jar(no_guice=True) as path:
            with self.assertRaises(r27.BootstrapDenied):
                r27.inspect(path, r27.sha256_file(path))

    def test_wrong_class_major_refused(self):
        with fake_jar(bad_major=True) as path:
            with self.assertRaises(r27.BootstrapDenied):
                r27.inspect(path, r27.sha256_file(path))

    def test_changed_native_binary_comparison_refused(self):
        with fake_jar() as path:
            original = r27.inspect(path, r27.sha256_file(path))
            with fake_jar(bad_native=True) as changed:
                modified = r27.inspect(changed, r27.sha256_file(changed))
                with self.assertRaises(r27.BootstrapDenied):
                    r27.compare(original, modified)

    def test_no_launch_or_java_subprocess_in_inspector(self):
        text = Path(r27.__file__).read_text(encoding="utf-8")
        for token in (
            "subprocess.", "Popen(", "Start-Process", "os.system(",
            "java -jar", "WindowsSandbox.exe", "shell=True",
        ):
            self.assertNotIn(token, text)


if __name__ == "__main__":
    unittest.main()
