#!/usr/bin/env python3
"""R13: make a hash-audited *private* JDK11 for future OS egress restriction.

Copy only into a new invocation-owned folder in system TEMP. Never touch the
installed JDK or existing Java game clients. No game client or server launches.
This does NOT install/modify a firewall rule or grant GUI acceptance.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import stat
import subprocess
import sys
import tempfile
from typing import Any


MAX_SNAPSHOT_BYTES = 1024 * 1024 * 1024
MAX_SINGLE_FILE = 500 * 1024 * 1024
REQUIRED_JDK11 = ("bin/java.exe", "bin/javaw.exe", "release", "lib/modules")


class SnapshotBlocked(RuntimeError):
    pass


def hash_file(path: Path) -> str:
    d = hashlib.sha256()
    with path.open("rb") as stream:
        for part in iter(lambda: stream.read(1024 * 1024), b""):
            d.update(part)
    return d.hexdigest()


def regular(path: Path, *, directory: bool) -> None:
    st = path.lstat()
    marker = getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0x400)
    if path.is_symlink() or bool(getattr(st, "st_file_attributes", 0) & marker):
        raise SnapshotBlocked("R13 source JDK contains symlink/reparse entry")
    if not (stat.S_ISDIR(st.st_mode) if directory else stat.S_ISREG(st.st_mode)):
        raise SnapshotBlocked("R13 JDK contains nonordinary file or directory")


def java_version(exe: Path) -> int:
    try:
        result = subprocess.run(
            [str(exe), "-version"], check=False, timeout=12,
            capture_output=True, text=True, errors="replace",
        )
    except (OSError, subprocess.TimeoutExpired) as error:
        raise SnapshotBlocked("JDK cannot run java -version") from error
    if result.returncode:
        raise SnapshotBlocked("JDK java -version exited nonzero")
    match = re.search(r'version\s+"(\d+)\.', result.stdout + result.stderr)
    if not match:
        raise SnapshotBlocked("unsupported Java version string")
    return int(match.group(1))


def enumerate_jdk(source: Path) -> list[tuple[str, Path, int]]:
    regular(source, directory=True)
    for required in REQUIRED_JDK11:
        path = source.joinpath(*required.split("/"))
        if not path.is_file():
            raise SnapshotBlocked(f"JDK11 missing required path: {required}")
        regular(path, directory=False)
    found: list[tuple[str, Path, int]] = []
    total = 0
    for directory, dirs, files in os.walk(source, followlinks=False):
        root = Path(directory)
        regular(root, directory=True)
        for child in dirs:
            regular(root / child, directory=True)
        for filename in files:
            item = root / filename
            regular(item, directory=False)
            relative = item.relative_to(source).as_posix()
            size = item.stat().st_size
            total += size
            if size > MAX_SINGLE_FILE or total > MAX_SNAPSHOT_BYTES:
                raise SnapshotBlocked("R13 JDK size exceeds private snapshot safety bound")
            found.append((relative, item, size))
    found.sort(key=lambda row: row[0])
    if not found:
        raise SnapshotBlocked("empty JDK installation")
    return found


def guard_inputs(source: Path, parent: Path) -> tuple[Path, Path]:
    if not source.is_absolute() or not parent.is_absolute():
        raise SnapshotBlocked("R13 JDK source/private parent must be absolute")
    if not source.is_dir() or not parent.is_dir():
        raise SnapshotBlocked("R13 JDK source/private parent directory missing")
    src = source.resolve(strict=True)
    temp = Path(tempfile.gettempdir()).resolve()
    destination_parent = parent.resolve(strict=True)
    if destination_parent != temp:
        raise SnapshotBlocked("R13 private JDK snapshot must be generated directly in system TEMP")
    regular(destination_parent, directory=True)
    if src == temp or src.is_relative_to(temp) or temp.is_relative_to(src):
        raise SnapshotBlocked("R13 JDK input cannot overlap private output TEMP")
    if java_version(src / "bin" / "java.exe") != 11:
        raise SnapshotBlocked("R13 only supports exact major Java11 dedicated GUI JDK")
    return src, destination_parent


def make_snapshot(source: Path, parent: Path) -> dict[str, Any]:
    src, dest_parent = guard_inputs(source, parent)
    inventory = enumerate_jdk(src)
    owned_root = Path(tempfile.mkdtemp(prefix="spawnpk-r13-owned-jdk-", dir=str(dest_parent)))
    copied_root = owned_root / "jdk"
    try:
        copied_root.mkdir()
        recorded = []
        copied_bytes = 0
        for relative, original, size in inventory:
            target = copied_root.joinpath(*relative.split("/"))
            target.parent.mkdir(parents=True, exist_ok=True)
            regular(original, directory=False)
            if original.stat().st_size != size:
                raise SnapshotBlocked("JDK file size changed while creating snapshot")
            before = hash_file(original)
            with original.open("rb") as reader, target.open("xb") as writer:
                shutil.copyfileobj(reader, writer, length=1024 * 1024)
                writer.flush()
                os.fsync(writer.fileno())
            if hash_file(target) != before or hash_file(original) != before:
                raise SnapshotBlocked("private JDK byte mismatch / source drift")
            recorded.append((relative, size, before))
            copied_bytes += size

        # Recheck source after every target file was copied. Source identity
        # must not drift while another application updates the installed JDK.
        for name, original, size in inventory:
            expected = next(r[2] for r in recorded if r[0] == name)
            if original.stat().st_size != size or hash_file(original) != expected:
                raise SnapshotBlocked("source JDK mutated before snapshot committed")
        if java_version(copied_root / "bin" / "java.exe") != 11:
            raise SnapshotBlocked("private JDK executable failed Java11 probe")
        summary = hashlib.sha256()
        for name, size, digest in recorded:
            summary.update(f"{name}\0{size}\0{digest}\n".encode("utf-8"))
        receipt = {
            "result": "R13_PRIVATE_JAVA11_SNAPSHOT_PASS",
            "private_workspace": str(owned_root),
            "private_jdk_root": str(copied_root),
            "private_java_exe": str(copied_root / "bin" / "java.exe"),
            "private_java_sha256": hash_file(copied_root / "bin" / "java.exe"),
            "jdk_file_count": len(recorded),
            "copied_bytes": copied_bytes,
            "sha256_inventory": summary.hexdigest(),
            "source_jdk_preserved": True,
            "live_user_files_changed": False,
            "firewall_rules_changed": 0,
            "client_processes_started": 0,
            "existing_client_processes_stopped": 0,
            "actual_gui_acceptance": False,
            "next_step": "OS ActiveStore outbound-block rule admission still required",
        }
        (owned_root / "r13-private-java-receipt.json").write_text(
            json.dumps(receipt, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        return receipt
    except BaseException:
        # The path was allocated exclusively by this invocation.
        shutil.rmtree(owned_root)
        raise


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-jdk", type=Path, required=True)
    parser.add_argument("--private-parent", type=Path, default=Path(tempfile.gettempdir()))
    args = parser.parse_args(argv)
    try:
        result = make_snapshot(args.source_jdk, args.private_parent)
        print(json.dumps(result, indent=2, sort_keys=True))
        return 0
    except (SnapshotBlocked, OSError, ValueError) as exc:
        print(json.dumps({
            "result": "R13_PRIVATE_JAVA11_SNAPSHOT_BLOCKED",
            "error": f"{type(exc).__name__}: {exc}",
            "firewall_rules_changed": 0,
            "client_processes_started": 0,
            "existing_client_processes_stopped": 0,
        }, indent=2, sort_keys=True), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
