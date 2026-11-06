#!/usr/bin/env python3
"""R13: controlled REAL Java server smoke; does not launch any client.

A separate/private SpawnPK-RE Git checkout and exact pinned server JAR are
required. Only the newly created Java server child may be terminated. Existing
v312 client processes are never inspected, injected into, or terminated.
Local-only tests: TCP game listener and AUX versions HTTP response.

This tool does NOT certify the original graphical client or OS egress policy.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import socket
import subprocess
import sys
import tempfile
import time
from typing import Any
from urllib.request import urlopen

from r10_original_gui_preflight import (
    Check, git_identity, java_identity, loopback_ports, server_jar_identity
)

GAME_PORT = 43594
AUX_PORT = 43595
VERSIONS_URL = "http://127.0.0.1:43595/spk_live/versions.txt"


class ProbeBlocked(RuntimeError):
    pass


def check_inputs(repo: Path, head: str, jar: Path, sha: str, java: Path,
                 private_parent: Path) -> None:
    if not repo.is_dir() or not private_parent.is_dir():
        raise ProbeBlocked("source checkout or private-parent missing")
    if private_parent.is_symlink():
        raise ProbeBlocked("private-parent symlink rejected")
    if not private_parent.is_absolute():
        raise ProbeBlocked("private-parent must be absolute")
    if private_parent.resolve() == Path.home().resolve():
        raise ProbeBlocked("private-parent cannot be the real user home")
    if not sha or len(sha) != 64 or any(c not in "0123456789abcdef" for c in sha.lower()):
        raise ProbeBlocked("independent server SHA-256 required")
    checks: list[Check] = []
    checks += git_identity(repo, head)
    checks += server_jar_identity(jar, sha)
    checks.append(Check(
        "canonical_server_jar",
        jar.resolve() == (repo / "server" / "build" / "SpawnPKLocalServer.jar").resolve(),
        "must be selected checkout built server JAR",
    ))
    checks.append(java_identity(java))
    checks += loopback_ports()
    failures = [x.name + "=" + x.detail for x in checks if not x.ok]
    if failures:
        raise ProbeBlocked("R13 admission denied: " + " | ".join(failures))


def child_listener_owned(pid: int) -> bool | None:
    """Returns None on non-Windows (no claim of port-to-PID ownership)."""
    if os.name != "nt":
        return None
    ps = (
        "$p=" + str(pid) + "; "
        "$c=Get-NetTCPConnection -State Listen -ErrorAction Stop "
        "| Where-Object {$_.LocalPort -in @(43594,43595)}; "
        "if(@($c).Count -ne 2){exit 11}; "
        "foreach($x in $c){if($x.OwningProcess -ne $p -or "
        "$x.LocalAddress -ne '127.0.0.1'){exit 12}}; exit 0"
    )
    try:
        result = subprocess.run(
            ["powershell.exe", "-NoProfile", "-NonInteractive", "-Command", ps],
            capture_output=True, timeout=6, check=False,
        )
        return result.returncode == 0
    except (OSError, subprocess.TimeoutExpired):
        return False


def local_versions() -> str:
    with urlopen(VERSIONS_URL, timeout=1.5) as response:
        if response.status != 200:
            raise ProbeBlocked("R13 AUX unexpected HTTP status")
        body = response.read(16_385)
        if len(body) > 16_384:
            raise ProbeBlocked("R13 AUX response unexpectedly long")
        text = body.decode("us-ascii")
        for required in ("cache_version", "sprite_version", "config_version"):
            if required not in text:
                raise ProbeBlocked("R13 AUX version contract missing " + required)
        return hashlib.sha256(body).hexdigest()


def ping_game() -> None:
    with socket.create_connection(("127.0.0.1", GAME_PORT), timeout=2):
        pass


def smoke(repo: Path, head: str, jar: Path, sha: str, java: Path,
          private_parent: Path, max_seconds: float = 18) -> dict[str, Any]:
    repo = repo.resolve(strict=True)
    jar = jar.resolve(strict=True)
    java = java.resolve(strict=True)
    parent = private_parent.absolute()
    check_inputs(repo, head, jar, sha, java, parent)

    owned = Path(tempfile.mkdtemp(prefix="spawnpk-r13-owned-server-", dir=str(parent)))
    child: subprocess.Popen | None = None
    try:
        home = owned / "server-user-home"
        home.mkdir()
        stdout = owned / "stdout.log"
        stderr = owned / "stderr.log"
        with stdout.open("wb") as out, stderr.open("wb") as err:
            # Popen only after admission. argv avoids unsafe shell string parsing.
            child = subprocess.Popen([
                str(java), "-Duser.home=" + str(home),
                "-Djava.net.preferIPv4Stack=true",
                "-jar", str(jar), "--bootstrap", "--movement",
            ], cwd=repo, stdout=out, stderr=err, stdin=subprocess.DEVNULL)

        ready = False
        versions_hash = ""
        deadline = time.monotonic() + max_seconds
        while time.monotonic() < deadline:
            if child.poll() is not None:
                raise ProbeBlocked("R13 newly owned server exited before readiness")
            try:
                versions_hash = local_versions()
                ping_game()
                if child_listener_owned(child.pid) is False:
                    raise ProbeBlocked("R13 listener ports are not owned by our child")
                ready = True
                break
            except (OSError, TimeoutError, ConnectionError):
                time.sleep(0.12)
        if not ready:
            raise ProbeBlocked("R13 server failed loopback game/AUX readiness")

        # Verify source and expected server JAR were not changed during run.
        if hashlib.sha256(jar.read_bytes()).hexdigest() != sha.lower():
            raise ProbeBlocked("R13 server JAR changed during live smoke")
        identity = git_identity(repo, head)
        if not all(check.ok for check in identity):
            raise ProbeBlocked("R13 checkout changed during live smoke")

        receipt = {
            "result": "R13_OWNED_LOOPBACK_SERVER_SMOKE_PASS",
            "head": head.lower(),
        "platform": sys.platform,
            "server_sha256": sha.lower(),
            "game_listener": "127.0.0.1:43594",
            "aux_listener": "127.0.0.1:43595",
            "aux_versions_sha256": versions_hash,
            "child_pid": child.pid,
            "child_listener_ownership": child_listener_owned(child.pid),
            "owned_workspace": str(owned),
            "existing_clients_stopped": 0,
            "original_gui_started": False,
            "gameplay_graphical_acceptance": False,
            "outbound_egress_enforced": False,
            "limitation": "server-only local smoke; no OS egress proof or GUI certification",
        }
        (owned / "r13-server-smoke-receipt.json").write_text(
            json.dumps(receipt, sort_keys=True, indent=2) + "\n", encoding="utf-8"
        )
        return receipt
    finally:
        if child is not None and child.poll() is None:
            # Only child object created by THIS invocation; never use
            # taskkill /IM java.exe or inspect/stop any other client PID.
            child.terminate()
            try:
                child.wait(timeout=8)
            except subprocess.TimeoutExpired:
                child.kill()
                child.wait(timeout=5)


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", required=True, type=Path)
    parser.add_argument("--source-head", required=True)
    parser.add_argument("--server-jar", required=True, type=Path)
    parser.add_argument("--server-sha256", required=True)
    parser.add_argument("--java-exe", required=True, type=Path)
    parser.add_argument("--private-parent", required=True, type=Path)
    args = parser.parse_args(argv)
    try:
        result = smoke(args.repo, args.source_head, args.server_jar,
                       args.server_sha256, args.java_exe, args.private_parent)
        print(json.dumps(result, indent=2, sort_keys=True))
        return 0
    except (ProbeBlocked, OSError, ValueError, subprocess.TimeoutExpired) as exc:
        print(json.dumps({
            "result": "R13_OWNED_LOOPBACK_SERVER_SMOKE_BLOCKED",
            "error": f"{type(exc).__name__}: {exc}",
            "original_gui_started": False,
            "existing_clients_stopped": 0,
        }, indent=2, sort_keys=True), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
