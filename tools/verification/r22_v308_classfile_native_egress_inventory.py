#!/usr/bin/env python3
"""R22 read-only Java classfile inventory of process/native/network risk.

The exact proprietary original v308 and R11 certified airgap v308 JARs
are never executed, extracted or modified. Inspect CONSTANT_Methodref /
CONSTANT_InterfaceMethodref and ACC_NATIVE declarations in their bytecode.
This is a STATIC potential-call-site inventory, not reachability analysis,
network isolation or approval to launch a graphical client.

A dedicated Java firewall block for java.exe/javaw.exe is not a process-tree
sandbox. Detect ProcessBuilder/Runtime.exec exposure in either client before
any future GUI admission proposal.
"""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import struct
import sys
import zipfile

ORIGINAL_SHA = "854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6"
AIRGAP_SHA = "024fad774453430bb964076b98d460a6dae821ee31100322d104e30d9a9c97a7"
MAX_CLASSES = 20000
MAX_CLASS_BYTES = 16 * 1024 * 1024


class UnsafeInventory(RuntimeError):
    pass


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for part in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(part)
    return digest.hexdigest()


class Reader:
    def __init__(self, data: bytes):
        self.data = memoryview(data)
        self.pos = 0

    def take(self, count: int) -> memoryview:
        if count < 0 or self.pos + count > len(self.data):
            raise UnsafeInventory("R22 truncated classfile")
        value = self.data[self.pos:self.pos + count]
        self.pos += count
        return value

    def u1(self) -> int:
        return self.take(1)[0]

    def u2(self) -> int:
        return struct.unpack(">H", self.take(2))[0]

    def u4(self) -> int:
        return struct.unpack(">I", self.take(4))[0]


def parse_class(data: bytes) -> tuple[set[tuple[str, str, str]], int]:
    if len(data) > MAX_CLASS_BYTES:
        raise UnsafeInventory("R22 class exceeds 16 MiB limit")
    reader = Reader(data)
    if reader.u4() != 0xCAFEBABE:
        raise UnsafeInventory("R22 invalid Java class magic")
    reader.u2()  # minor
    major = reader.u2()
    if not 45 <= major <= 70:
        raise UnsafeInventory("R22 unknown Java classfile major")
    count = reader.u2()
    cp: list[tuple | None] = [None] * count
    i = 1
    while i < count:
        tag = reader.u1()
        if tag == 1:
            length = reader.u2()
            cp[i] = ("utf8", bytes(reader.take(length)).decode(
                "utf-8", errors="replace"))
        elif tag in (3, 4):
            reader.take(4)
        elif tag in (5, 6):
            reader.take(8)
            i += 1
        elif tag in (7, 8, 16, 19, 20):
            cp[i] = (tag, reader.u2())
        elif tag in (9, 10, 11, 12, 17, 18):
            cp[i] = (tag, reader.u2(), reader.u2())
        elif tag == 15:
            cp[i] = (tag, reader.u1(), reader.u2())
        else:
            raise UnsafeInventory(f"R22 unknown constant-pool tag {tag}")
        i += 1

    def entry(index: int, tag: int | str) -> tuple:
        if not 0 < index < count or cp[index] is None or cp[index][0] != tag:
            raise UnsafeInventory("R22 malformed constant-pool reference")
        return cp[index]

    def utf8(index: int) -> str:
        return str(entry(index, "utf8")[1])

    class_name = lambda index: utf8(entry(index, 7)[1])
    refs: set[tuple[str, str, str]] = set()
    for row in cp:
        if row is None or row[0] not in (10, 11):
            continue
        owner = class_name(row[1])
        nt = entry(row[2], 12)
        refs.add((owner, utf8(nt[1]), utf8(nt[2])))

    reader.u2()  # flags
    reader.u2()  # this_class
    reader.u2()  # superclass
    interfaces = reader.u2()
    reader.take(interfaces * 2)

    def skip_attributes() -> None:
        for _ in range(reader.u2()):
            reader.u2()
            reader.take(reader.u4())

    for _ in range(reader.u2()):  # fields
        reader.take(6)
        skip_attributes()
    natives = 0
    for _ in range(reader.u2()):  # methods
        flags = reader.u2()
        reader.take(4)
        if flags & 0x0100:
            natives += 1
        skip_attributes()
    skip_attributes()  # class attributes
    if reader.pos != len(data):
        raise UnsafeInventory("R22 unexpected trailing classfile bytes")
    return refs, natives


def risk_category(owner: str, method: str) -> str | None:
    if (owner == "java/lang/ProcessBuilder" and
            method in ("start", "startPipeline")):
        return "process_builder_launch"
    if owner == "java/lang/Runtime" and method.startswith("exec"):
        return "runtime_exec"
    if (owner in ("java/lang/System", "java/lang/Runtime") and
            method in ("load", "loadLibrary")):
        return "native_library_loader"
    if owner.startswith("java/net/") or owner.startswith("javax/net/"):
        return "network_api_method"
    if owner.startswith("java/nio/channels/") and (
            "Socket" in owner or "Datagram" in owner):
        return "nio_network_channel_method"
    return None


def inspect_jar(path: Path, expected_sha: str) -> dict:
    if not path.is_file() or path.is_symlink():
        raise UnsafeInventory("R22 source JAR is absent or a symlink")
    if sha256_file(path) != expected_sha:
        raise UnsafeInventory("R22 JAR SHA256 differs from exact pinned source")
    observations: dict[str, set[tuple[str, str, str, str]]] = {
        name: set() for name in (
            "process_builder_launch", "runtime_exec",
            "native_library_loader", "network_api_method",
            "nio_network_channel_method")
    }
    native_methods = Counter()
    class_names: list[str] = []
    native_entries = []
    with zipfile.ZipFile(path) as jar:
        inventory = jar.namelist()
        if len(set(inventory)) != len(inventory):
            raise UnsafeInventory("R22 duplicate JAR entries")
        class_names = [name for name in inventory if name.endswith(".class")]
        if not 1 <= len(class_names) <= MAX_CLASSES:
            raise UnsafeInventory("R22 implausible Java class count")
        native_entries = sorted(
            name for name in inventory
            if name.lower().endswith((".dll", ".so", ".dylib", ".jnilib")))
        for name in class_names:
            data = jar.read(name)
            refs, count = parse_class(data)
            if count:
                native_methods[name] += count
            for owner, method, descriptor in refs:
                category = risk_category(owner, method)
                if category:
                    observations[category].add(
                        (name, owner, method, descriptor))
    if sha256_file(path) != expected_sha:
        raise UnsafeInventory("R22 pinned JAR changed during bytecode inventory")
    return {
        "sha256": expected_sha,
        "class_count": len(class_names),
        "class_names": tuple(class_names),
        "native_methods_declared": sum(native_methods.values()),
        "native_method_classes": len(native_methods),
        "embedded_native_library_entries": native_entries,
        "sensitive": observations,
    }


def assess(original: dict, airgap: dict) -> dict:
    if original["class_names"] != airgap["class_names"]:
        raise UnsafeInventory("R22 original/airgap class inventories differ")
    new_launch = sorted(
        (airgap["sensitive"]["process_builder_launch"]
         | airgap["sensitive"]["runtime_exec"]) -
        (original["sensitive"]["process_builder_launch"]
         | original["sensitive"]["runtime_exec"]))
    obs = {}
    for category, items in airgap["sensitive"].items():
        obs[category] = {
            "airgap_distinct_static_references": len(items),
            "original_distinct_static_references":
                len(original["sensitive"][category]),
            "sample": [
                {"class": cls, "owner": owner, "method": method}
                for cls, owner, method, _ in sorted(items)[:6]
            ],
        }
    in_class = lambda filename, owner, method: any(
        ref[0] == filename and ref[1] == owner and ref[2] == method
        for ref in airgap["sensitive"].get(
            "process_builder_launch" if method == "start" else "runtime_exec",
            set()))
    known_escape = (
        in_class("rs/s/n/c.class", "java/lang/ProcessBuilder", "start")
        and in_class("rs/s/n/c.class", "java/lang/Runtime", "exec"))
    if not known_escape:
        raise UnsafeInventory("R22 known process-execution references missing")
    if new_launch:
        raise UnsafeInventory(
            "R22 airgap added new process-launch method references")
    return {
        "result": "R22_STATIC_PROCESS_NATIVE_SURFACE_INVENTORY_PASS",
        "original_v308_sha256": ORIGINAL_SHA,
        "certified_airgap_v308_sha256": AIRGAP_SHA,
        "classes_compared": airgap["class_count"],
        "native_methods_declared_original": original["native_methods_declared"],
        "native_methods_declared_airgap": airgap["native_methods_declared"],
        "embedded_native_libraries_original":
            len(original["embedded_native_library_entries"]),
        "embedded_native_libraries_airgap":
            len(airgap["embedded_native_library_entries"]),
        "known_process_launch_surface_still_present": True,
        "airgap_new_process_launch_references": len(new_launch),
        "sensitive_method_references": obs,
        "complete_process_tree_network_isolation_proven": False,
        "graphical_client_started": False,
        "firewall_rules_modified": 0,
        "existing_client_processes_stopped": 0,
        "interpretation": (
            "Constant-pool method references are potential call sites, not "
            "proof of runtime reachability. Presence of process launch and "
            "native interfaces means java/javaw-only firewall blocks do NOT "
            "establish complete process-tree egress isolation."
        ),
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--original-v308", type=Path, required=True)
    parser.add_argument("--certified-airgap-v308", type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        original = inspect_jar(args.original_v308, ORIGINAL_SHA)
        airgap = inspect_jar(args.certified_airgap_v308, AIRGAP_SHA)
        result = assess(original, airgap)
        print(json.dumps(result, indent=2, sort_keys=True))
        return 0
    except (UnsafeInventory, OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
        print(json.dumps({
            "result": "R22_STATIC_PROCESS_NATIVE_SURFACE_INVENTORY_BLOCKED",
            "error": str(error),
            "graphical_client_started": False,
        }, indent=2, sort_keys=True), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
