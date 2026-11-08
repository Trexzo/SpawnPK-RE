#!/usr/bin/env python3
"""R21: READ-ONLY fail-closed dual-java.exe/javaw.exe Windows egress audit.

R13 certified only java.exe. A graphical launcher can use javaw.exe instead,
so protecting just java.exe is insufficient. Require independent, exact
ActiveStore program-wide outbound BLOCK rules for BOTH dedicated executables.

Never installs firewall rules, launches a client or stops any process.
These program-specific firewall checks do not prove that other/native child
executables cannot create traffic, and do not prove loopback game connectivity.
"""
from __future__ import annotations

import argparse
from dataclasses import asdict, dataclass
import hashlib
import json
from pathlib import Path
import sys
from typing import Any

from r13_windows_gui_egress_gate import (
    basic_private_exe, firewall_record, no_existing_java_uses,
)

RULE_JAVA = "SpawnPK LocalLab R13 Private JDK All Outbound Block"
RULE_JAVAW = "SpawnPK LocalLab R21 Private JDK javaw All Outbound Block"
EXE_SHA256 = {
    "java.exe": "b2484c2889365b93fb48434e12c8f9bc21ca6cd5d4eb2eae39a46b17ced3c8ef",
    "javaw.exe": "0a4656c826032ffe28d58ebfa93e85d4c099c50a1857f29416461848f0c6adaa",
}


@dataclass(frozen=True)
class Check:
    name: str
    ok: bool
    detail: str


def hash_file(path: Path) -> str:
    hasher = hashlib.sha256()
    with path.open("rb") as source:
        for part in iter(lambda: source.read(1024 * 1024), b""):
            hasher.update(part)
    return hasher.hexdigest()


def executable_pin(executable: Path, expected_sha: str) -> Check:
    name = executable.name.lower()
    try:
        if not executable.is_file() or executable.is_symlink():
            return Check("sha256_" + name, False,
                         "private executable missing or symbolic link")
        actual = hash_file(executable)
        return Check("sha256_" + name, actual == expected_sha,
                     "sha256=" + actual + "; expected=" + expected_sha)
    except (OSError, ValueError):
        return Check("sha256_" + name, False, "cannot read executable bytes")


def audit(private_jdk_root: Path) -> dict[str, Any]:
    root = private_jdk_root.expanduser()
    checks: list[Check] = []
    for name, rule_name in (("java.exe", RULE_JAVA), ("javaw.exe", RULE_JAVAW)):
        exe = root / "bin" / name
        pinned = executable_pin(exe, EXE_SHA256[name])
        dedicated = basic_private_exe(exe, root)
        unused = no_existing_java_uses(exe)
        rule = firewall_record(exe, rule_name)
        checks.extend((
            pinned,
            Check("dedicated_" + name, dedicated.ok, dedicated.detail),
            Check("unused_" + name, unused.ok, unused.detail),
            Check("active_block_" + name, rule.ok, rule.detail),
        ))
    ready = all(check.ok for check in checks)
    return {
        "result": ("R21_DUAL_PRIVATE_JAVA_OS_EGRESS_READY"
                   if ready else "R21_DUAL_PRIVATE_JAVA_OS_EGRESS_BLOCKED"),
        "mode": "READ_ONLY_NEVER_LAUNCH",
        "checks": [asdict(check) for check in checks],
        "private_jdk": str(root),
        "rule_names": {"java.exe": RULE_JAVA, "javaw.exe": RULE_JAVAW},
        "firewall_rules_changed": 0,
        "existing_java_processes_stopped": 0,
        "graphical_client_started": False,
        "graphical_acceptance": False,
        "warning": (
            "READY verifies two private-JDK program-specific Windows Firewall "
            "rules only. It is NOT a proof of all native/child-process egress, "
            "loopback reachability, or compatibility with active v312 sessions. "
            "R10 GUI process fence and independent runtime checks still apply."
        ),
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--private-jdk-root", type=Path, required=True)
    args = parser.parse_args(argv)
    result = audit(args.private_jdk_root)
    print(json.dumps(result, sort_keys=True, indent=2))
    return 0 if result["result"] == "R21_DUAL_PRIVATE_JAVA_OS_EGRESS_READY" else 2


if __name__ == "__main__":
    sys.exit(main())
