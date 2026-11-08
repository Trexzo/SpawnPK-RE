#!/usr/bin/env python3
"""Synthetic Java-class fixtures for R22 (no copyrighted JAR in CI)."""
from __future__ import annotations

import hashlib
from pathlib import Path
import struct
import sys
import tempfile
import unittest
from unittest import mock
from zipfile import ZipFile, ZIP_STORED

sys.path.insert(0, str(Path(__file__).resolve().parent))
import r22_v308_classfile_native_egress_inventory as r22


def u2(value: int) -> bytes:
    return struct.pack(">H", value)


def u4(value: int) -> bytes:
    return struct.pack(">I", value)


def class_fixture(
    calls: tuple[tuple[str, str, str], ...] = (),
    native: bool = False,
    magic: int = 0xCAFEBABE,
) -> bytes:
    cp: list[bytes] = []

    def utf8(value: str) -> int:
        data = value.encode("utf-8")
        cp.append(b"\x01" + u2(len(data)) + data)
        return len(cp)

    def item(tag: int, *indices: int) -> int:
        cp.append(bytes([tag]) + b"".join(u2(i) for i in indices))
        return len(cp)

    cls_name = utf8("rs/s/n/c")
    this_class = item(7, cls_name)
    object_name = utf8("java/lang/Object")
    object_class = item(7, object_name)
    for owner, method, descriptor in calls:
        owner_index = item(7, utf8(owner))
        type_index = item(12, utf8(method), utf8(descriptor))
        item(10, owner_index, type_index)
    methods = b""
    if native:
        method_index = utf8("nativeEntry")
        sig_index = utf8("()V")
        methods = u2(0x0101) + u2(method_index) + u2(sig_index) + u2(0)
    return (
        u4(magic) + u2(0) + u2(55) + u2(len(cp) + 1) +
        b"".join(cp) + u2(0x0021) + u2(this_class) +
        u2(object_class) + u2(0) + u2(0) +
        u2(1 if native else 0) + methods + u2(0)
    )


class R22StaticInventoryTests(unittest.TestCase):
    PROC = ("java/lang/ProcessBuilder", "start", "()Ljava/lang/Process;")
    EXEC = (
        "java/lang/Runtime", "exec",
        "([Ljava/lang/String;)Ljava/lang/Process;")
    LOAD = ("java/lang/System", "loadLibrary", "(Ljava/lang/String;)V")
    HTTP = ("java/net/URL", "openConnection", "()Ljava/net/URLConnection;")

    def test_exact_methodrefs_are_structurally_parsed(self):
        refs, native = r22.parse_class(
            class_fixture((self.PROC, self.EXEC, self.LOAD, self.HTTP),
                          native=True))
        self.assertEqual(refs, {self.PROC, self.EXEC, self.LOAD, self.HTTP})
        self.assertEqual(native, 1)

    def test_reject_truncated_invalid_and_unknown_tags(self):
        baseline = class_fixture((self.PROC,))
        with self.assertRaises(r22.UnsafeInventory):
            r22.parse_class(baseline[:-1])
        with self.assertRaises(r22.UnsafeInventory):
            r22.parse_class(class_fixture(magic=0xDEADBEEF))
        bad = bytearray(baseline)
        bad[10] = 0xFF
        with self.assertRaises(r22.UnsafeInventory):
            r22.parse_class(bytes(bad))

    def test_network_process_and_native_categories_are_distinct(self):
        self.assertEqual(r22.risk_category(*self.PROC[:2]),
                         "process_builder_launch")
        self.assertEqual(r22.risk_category(*self.EXEC[:2]),
                         "runtime_exec")
        self.assertEqual(r22.risk_category(*self.LOAD[:2]),
                         "native_library_loader")
        self.assertEqual(r22.risk_category(*self.HTTP[:2]),
                         "network_api_method")
        self.assertIsNone(r22.risk_category("java/util/List", "get"))

    def test_pin_rejects_wrong_client_and_duplicate_jar_entries(self):
        with tempfile.TemporaryDirectory() as folder:
            target = Path(folder) / "fixture.jar"
            with ZipFile(target, "w", ZIP_STORED) as jar:
                jar.writestr("rs/s/n/c.class", class_fixture((self.PROC,)))
            sha = hashlib.sha256(target.read_bytes()).hexdigest()
            with self.assertRaises(r22.UnsafeInventory):
                r22.inspect_jar(target, "f" * 64)
            with mock.patch.object(r22, "sha256_file", return_value=sha):
                report = r22.inspect_jar(target, sha)
            self.assertEqual(report["class_count"], 1)
            self.assertEqual(
                len(report["sensitive"]["process_builder_launch"]), 1)

    def test_no_false_complete_egress_claim_when_process_launch_present(self):
        def inventory(include_launch: bool) -> dict:
            procs = set()
            execs = set()
            if include_launch:
                procs.add(("rs/s/n/c.class", *self.PROC))
                execs.add(("rs/s/n/c.class", *self.EXEC))
            return {
                "class_names": ("rs/s/n/c.class",),
                "class_count": 1,
                "native_methods_declared": 0,
                "embedded_native_library_entries": [],
                "sensitive": {
                    "process_builder_launch": procs,
                    "runtime_exec": execs,
                    "native_library_loader": set(),
                    "network_api_method": set(),
                    "nio_network_channel_method": set(),
                },
            }
        safe = r22.assess(inventory(True), inventory(True))
        self.assertEqual(
            safe["result"], "R22_STATIC_PROCESS_NATIVE_SURFACE_INVENTORY_PASS")
        self.assertTrue(safe["known_process_launch_surface_still_present"])
        self.assertFalse(safe["complete_process_tree_network_isolation_proven"])
        with self.assertRaises(r22.UnsafeInventory):
            r22.assess(inventory(False), inventory(True))
        with self.assertRaises(r22.UnsafeInventory):
            r22.assess(inventory(True), inventory(False))

    def test_static_inspection_never_invokes_java_or_processes(self):
        source = Path(r22.__file__).read_text(encoding="utf-8")
        for token in ("subprocess.", "Popen(", "os.system(", "shell=True"):
            self.assertNotIn(token, source)


if __name__ == "__main__":
    unittest.main()
