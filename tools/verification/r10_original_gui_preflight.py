#!/usr/bin/env python3
"""R10 original-client graphical acceptance: fail-closed READ-ONLY preflight.

This script does not launch, download, edit, stop or inject any process. It
distinguishes the original v308 JAR from the *different, patched* certified
airgap v308 JAR. A raw original JAR is evidence, NOT approved to make external
network requests. There is deliberately no '--ignore-hash' or '--force' flag.

R10 is not GUI acceptance itself; it generates an auditable precondition
receipt for a later separately controlled, isolated Windows GUI run.
"""
from __future__ import annotations

import argparse
from dataclasses import asdict, dataclass
import hashlib
import json
import os
from pathlib import Path
import re
import socket
import subprocess
import sys
from typing import Callable
import zipfile

ORIGINAL_V308_SHA256 = "854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6"
CERTIFIED_AIRGAP_V308_SHA256 = "024fad774453430bb964076b98d460a6dae821ee31100322d104e30d9a9c97a7"
ORIGIN = "trexzo/spawnpk-re"
GAME_PORTS = (43594, 43595)
ALLOWED_JAVA_MAJORS = frozenset({11, 17, 21})


@dataclass(frozen=True)
class Check:
    name: str
    ok: bool
    detail: str


def file_sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def candidate_digest(path: Path, expected: str, name: str) -> Check:
    if not path.is_file():
        return Check(name, False, f"missing file: {path}")
    if not re.fullmatch(r"[0-9a-f]{64}", expected):
        return Check(name, False, "invalid fixed digest")
    got = file_sha256(path)
    return Check(
        name, got == expected,
        f"sha256={got}; expected={expected}; path={path}"
    )


def server_jar_identity(path: Path, expected_sha: str | None) -> list[Check]:
    if not path.is_file():
        return [Check("server_artifact", False, f"missing JAR: {path}")]
    checks = [
        Check(
            "server_sha256_pin",
            bool(expected_sha) and file_sha256(path) == expected_sha,
            f"sha256={file_sha256(path)}; expected={expected_sha or 'NOT_SUPPLIED'}",
        )
    ]
    try:
        with zipfile.ZipFile(path) as jar:
            names = set(jar.namelist())
            required = ("spk/local/Main.class", "spk/local/equipment_slots.tsv")
            missing = [name for name in required if name not in names]
            checks.append(Check(
                "server_jar_structure", not missing,
                f"missing={missing}" if missing else "required server classes and resources present",
            ))
            main = jar.read("spk/local/Main.class") if not missing else b""
            java_11 = len(main) >= 8 and main[:4] == b"\xca\xfe\xba\xbe" and int.from_bytes(main[6:8], "big") == 55
            checks.append(Check(
                "java_11_server_bytecode",
                java_11, f"Main.class major={int.from_bytes(main[6:8], 'big') if len(main) >= 8 else 'MISSING'}",
            ))
    except (OSError, zipfile.BadZipFile, KeyError, RuntimeError) as exc:
        checks.append(Check("server_jar_structure", False, f"invalid JAR: {type(exc).__name__}"))
    return checks


def run_readonly(command: list[str], timeout: float = 8.0) -> tuple[bool, str]:
    try:
        done = subprocess.run(
            command,
            check=False, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            text=True, errors="replace", timeout=timeout
        )
        return done.returncode == 0, done.stdout.strip() or done.stderr.strip()
    except (OSError, subprocess.TimeoutExpired) as exc:
        return False, type(exc).__name__


def git_identity(repo: Path, head: str) -> list[Check]:
    if not re.fullmatch(r"[0-9a-fA-F]{40}", head):
        return [Check("source_revision", False, "expected commit is not 40 hex characters")]
    ok, actual = run_readonly(["git", "-C", str(repo), "rev-parse", "HEAD"])
    result = [Check(
        "source_revision", ok and actual.lower() == head.lower(),
        f"current={actual}; required={head.lower()}"
    )]
    ok, remote = run_readonly(["git", "-C", str(repo), "remote", "get-url", "origin"])
    normalized = remote.lower().replace("\\", "/").rstrip("/")
    recognized = (normalized.endswith("/" + ORIGIN) or
                  normalized.endswith("/" + ORIGIN + ".git") or
                  normalized.endswith(":" + ORIGIN) or
                  normalized.endswith(":" + ORIGIN + ".git"))
    result.append(Check(
        "source_repository_origin",
        ok and recognized,
        f"origin={remote}" if ok else f"git remote unavailable: {remote}",
    ))
    ok, changes = run_readonly([
        "git", "-C", str(repo), "status", "--porcelain=v1", "--untracked-files=no"
    ])
    result.append(Check(
        "tracked_source_clean",
        ok and not changes,
        "tracked files clean" if ok and not changes else f"dirty-or-unknown: {changes[:180]}",
    ))
    return result


def java_identity(exe: Path) -> Check:
    if not exe.is_file():
        return Check("java_11_plus", False, f"missing Java executable: {exe}")
    try:
        completed = subprocess.run(
            [str(exe), "-version"], stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            text=True, errors="replace", timeout=8, check=False
        )
    except (OSError, subprocess.TimeoutExpired) as exc:
        return Check("java_11_plus", False, f"Java not executable: {type(exc).__name__}")
    combined = completed.stdout + completed.stderr
    match = re.search(r'version\s+"(1\.\d+|\d+)', combined)
    if match is None:
        return Check("java_11_plus", False, "Java version could not be authenticated")
    version = match.group(1)
    major = int(version[2:]) if version.startswith("1.") else int(version)
    return Check(
        "java_11_plus", completed.returncode == 0 and major in ALLOWED_JAVA_MAJORS,
        f"java_major={major}; allowed={sorted(ALLOWED_JAVA_MAJORS)}; path={exe}"
    )


def isolated_home(home: Path, real_home: Path) -> Check:
    try:
        home = home.resolve(strict=True)
        real_home = real_home.resolve()
    except OSError as exc:
        return Check("isolated_client_home", False, f"cannot resolve isolation root: {exc}")
    if not home.is_dir() or os.path.normcase(str(home)) == os.path.normcase(str(real_home)):
        return Check("isolated_client_home", False, "client home missing or equals OS user home")
    cache = home / ".spawnpk"
    return Check(
        "isolated_client_home",
        cache.is_dir(),
        f"separate={home != real_home}; seeded_cache={cache.is_dir()}; path={home}",
    )


def loopback_ports(ports: tuple[int, ...] = GAME_PORTS,
                   checker: Callable[[int], bool] | None = None) -> list[Check]:
    """Bind without SO_REUSEADDR; an occupied port is a hard stop, not a kill instruction."""
    def available(port: int) -> bool:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.bind(("127.0.0.1", port))
        return True
    checker = checker or available
    results = []
    for port in ports:
        try:
            free = checker(port)
            results.append(Check(f"tcp_{port}_unoccupied", bool(free), f"loopback port {port} {'free' if free else 'occupied'}"))
        except OSError as exc:
            results.append(Check(f"tcp_{port}_unoccupied", False, f"port {port} occupied/denied: {exc.__class__.__name__}"))
    return results


def active_spawnpk_java() -> Check:
    """Protect existing client users. PowerShell read-only CIM; never kill a process."""
    if os.name != "nt":
        return Check("live_client_process_fence", False, "Windows process inventory required for GUI acceptance")
    ps = (
        "Get-CimInstance Win32_Process -Filter \"Name='java.exe' OR Name='javaw.exe'\" "
        "| Select-Object -ExpandProperty CommandLine"
    )
    ok, out = run_readonly(["powershell.exe", "-NoProfile", "-NonInteractive", "-Command", ps], 15)
    if not ok:
        return Check("live_client_process_fence", False, f"unable to inspect Java processes: {out[:100]}")
    # Deliberately conservative. Do not inspect arbitrary command lines beyond
    # this read-only check, and never terminate a running client.
    lines = [line.strip() for line in out.splitlines()]
    active = [
        line for line in lines if re.search(
            r"(?:spawnpk|fullspkclient|tpbotspawnpkresolver|rs\.gui\.Launcher)",
            line, re.IGNORECASE
        )
    ]
    return Check(
        "live_client_process_fence", not active,
        f"existing SpawnPK-related Java process_count={len(active)}; no action taken",
    )


def preflight(args: argparse.Namespace) -> dict:
    repo = args.repo.expanduser().resolve()
    jar = args.server_jar.expanduser().resolve()
    original = args.original_jar.expanduser().resolve()
    airgap = args.airgap_jar.expanduser().resolve()
    home = args.client_home.expanduser()
    checks = []
    checks += git_identity(repo, args.source_head)
    checks += server_jar_identity(jar, args.server_sha256)
    checks.append(Check(
        "server_inside_selected_checkout",
        jar == (repo / "server" / "build" / "SpawnPKLocalServer.jar").resolve(),
        f"server_path={jar}"
    ))
    checks.append(candidate_digest(original, ORIGINAL_V308_SHA256, "original_v308_pin"))
    checks.append(candidate_digest(airgap, CERTIFIED_AIRGAP_V308_SHA256, "certified_airgap_v308_pin"))
    checks.append(java_identity(args.java_exe.expanduser().resolve()))
    checks.append(isolated_home(home, Path.home()))
    checks.extend(loopback_ports())
    checks.append(active_spawnpk_java())

    return {
        "result": "R10_ORIGINAL_GUI_PREFLIGHT_READY" if all(x.ok for x in checks)
                  else "R10_ORIGINAL_GUI_PREFLIGHT_BLOCKED",
        "mode": "READ_ONLY_NEVER_LAUNCH",
        "launch_performed": False,
        "existing_processes_stopped": 0,
        "source_head_required": args.source_head.lower(),
        "client_classification": {
            "original": "unmodified v308 reference; NEVER launched by this preflight",
            "airgap": "independently patched loopback-only derivative; NOT original bytes",
        },
        "checks": [asdict(check) for check in checks],
        "next_step": (
            "Separate isolated Windows graphical session acceptance required;"
            " this receipt is NOT GUI PASS"
        ),
    }


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, required=True)
    parser.add_argument("--source-head", required=True)
    parser.add_argument("--server-jar", type=Path, required=True)
    parser.add_argument("--server-sha256", type=str, required=True,
                        help="Independent 64-hex expected hash of built server JAR")
    parser.add_argument("--original-jar", type=Path, required=True)
    parser.add_argument("--airgap-jar", type=Path, required=True)
    parser.add_argument("--java-exe", type=Path, required=True)
    parser.add_argument("--client-home", type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        result = preflight(args)
    except (ValueError, PermissionError, OSError) as error:
        result = {
            "result": "R10_ORIGINAL_GUI_PREFLIGHT_BLOCKED",
            "mode": "READ_ONLY_NEVER_LAUNCH",
            "launch_performed": False,
            "error": f"{type(error).__name__}: {error}",
        }
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0 if result["result"] == "R10_ORIGINAL_GUI_PREFLIGHT_READY" else 2


if __name__ == "__main__":
    raise SystemExit(main())
