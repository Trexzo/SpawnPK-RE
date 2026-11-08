#!/usr/bin/env python3
"""R26 REAL owned LocalLab server only: localhost + graceful stdin shutdown.

No Windows Sandbox, graphical client, v312 JVM, firewall or account is
modified. Runs ONE newly owned Java server process in a new temporary HOME,
uses the LocalLab TEST-ONLY private stdin token and validates a clean
shutdown / port release. It is NOT a graphical v308 or guest acceptance.

This host-side proof exercises the same R14 Java shutdown contract used by
the R26 guest-only PowerShell command. Exact pinned compiled server SHA,
Git source HEAD, JDK, free localhost ports and private-parent gates required.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import secrets
import shutil
import socket
import subprocess
import sys
import tempfile
import time

from r13_windows_server_smoke import (
    ProbeBlocked, check_inputs, child_listener_owned, local_versions, ping_game,
)
from r10_original_gui_preflight import loopback_ports


class OwnedServerDenied(RuntimeError):
    pass


def ports_free() -> bool:
    # Probe both endpoints, not just a cached OS list.
    for port in (43594, 43595):
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as test:
            test.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 0)
            try:
                test.bind(("127.0.0.1", port))
            except OSError:
                return False
    return True


def log_has(log: Path, *required: str) -> bool:
    text = log.read_text(encoding="utf-8", errors="replace")
    return all(item in text for item in required)


def verify_owned_session(repo: Path, head: str, jar: Path, server_sha: str,
                         java: Path, parent: Path) -> dict:
    check_inputs(repo, head, jar, server_sha, java, parent)
    if not ports_free():
        raise OwnedServerDenied("R26 localhost game or AUX port occupied")
    owned = Path(tempfile.mkdtemp(prefix="spawnpk-r26-owned-server-", dir=parent))
    child: subprocess.Popen | None = None
    token = secrets.token_hex(32)
    graceful = False
    forced = False
    try:
        home = owned / "guest-style-home"
        (home / "data" / "accounts").mkdir(parents=True)
        account = home / "data" / "accounts" / "opensrc.properties"
        stdout = owned / "server-stdout.log"
        stderr = owned / "server-stderr.log"
        with stdout.open("wb") as out, stderr.open("wb") as err:
            child = subprocess.Popen([
                str(java),
                "-Duser.home=" + str(home),
                "-Djava.net.preferIPv4Stack=true",
                "-Dspk.local.accountFile=" + str(account),
                "-Dspk.local.acceptance.stdinShutdownEnabled=true",
                "-Dspk.local.acceptance.stdinShutdownToken=" + token,
                "-jar", str(jar), "--bootstrap", "--movement",
            ], cwd=home, stdin=subprocess.PIPE, stdout=out, stderr=err)

        response_sha = ""
        deadline = time.monotonic() + 24
        ready = False
        while time.monotonic() < deadline:
            if child.poll() is not None:
                raise OwnedServerDenied("R26 owned server exited before readiness")
            try:
                response_sha = local_versions()
                ping_game()
                if child_listener_owned(child.pid) is False:
                    raise OwnedServerDenied("R26 ports not owned by test child")
                ready = True
                break
            except (OSError, TimeoutError, ConnectionError):
                time.sleep(0.12)
        if not ready:
            raise OwnedServerDenied("R26 owned server game/AUX localhost not ready")
        if not log_has(stdout, "SpawnPK Local Lab",
                       "R15_ACCOUNT_DIRECTORY_LEASE_ACQUIRED"):
            raise OwnedServerDenied("R26 expected startup/lease logs missing")
        if hashlib.sha256(jar.read_bytes()).hexdigest() != server_sha:
            raise OwnedServerDenied("R26 pinned JAR mutated during smoke")
        listeners_owned_before_shutdown = child_listener_owned(child.pid)
        assert child.stdin is not None
        child.stdin.write((token + "\n").encode("ascii"))
        child.stdin.flush()
        child.stdin.close()
        if child.wait(timeout=36) != 0:
            raise OwnedServerDenied("R26 owned test server returned nonzero")
        graceful = log_has(
            stdout, "R14_TEST_ONLY_STDIN_SHUTDOWN_AUTHORIZED",
            "V5123_PERSISTENCE_SHUTDOWN_CLEAN",
        )
        if not graceful:
            raise OwnedServerDenied("R26 graceful server shutdown evidence missing")
        if not ports_free() or not all(x.ok for x in loopback_ports()):
            raise OwnedServerDenied("R26 owned server did not release both ports")
        if account.exists() and account.is_symlink():
            raise OwnedServerDenied("R26 synthetic account became symlink")
        result = {
            "result": "R26_OWNED_REAL_LOCALLAB_STDIN_LOOPBACK_RESTART_SAFE_PASS",
            "source_head": head,
            "server_sha256": server_sha,
            "game_listener": "127.0.0.1:43594",
            "aux_listener": "127.0.0.1:43595",
            "aux_versions_sha256": response_sha,
            "owned_child_pid": child.pid,
            "owner_listener_check_before_shutdown": listeners_owned_before_shutdown,
            "graceful_stdin_token_shutdown": True,
            "persistence_shutdown_clean_log": True,
            "ports_released_after_shutdown": True,
            "forced_kill_used": False,
            "test_workspace": str(owned),
            "real_user_profiles_mapped": False,
            "existing_client_processes_terminated": 0,
            "graphical_client_started": False,
            "sandbox_guest_tested": False,
        }
        (owned / "r26-owned-server-smoke-receipt.json").write_text(
            json.dumps(result, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        return result
    finally:
        if child is not None:
            if child.poll() is None:
                # Only our subprocess object may receive the private token
                # and, if needed, a forceful fallback cleanup.
                if child.stdin is not None and not child.stdin.closed:
                    try:
                        child.stdin.write((token + "\n").encode("ascii"))
                        child.stdin.flush()
                        child.stdin.close()
                        child.wait(timeout=12)
                    except (OSError, BrokenPipeError,
                            subprocess.TimeoutExpired):
                        pass
                if child.poll() is None:
                    child.kill()
                    child.wait(timeout=5)
                    forced = True
            if forced:
                (owned / "r26-forced-cleanup-warning.txt").write_text(
                    "Only the R26 invocation-owned server process was killed.\n",
                    encoding="utf-8",
                )


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", required=True, type=Path)
    parser.add_argument("--source-head", required=True)
    parser.add_argument("--server-jar", required=True, type=Path)
    parser.add_argument("--server-sha256", required=True)
    parser.add_argument("--java-exe", required=True, type=Path)
    parser.add_argument("--private-parent", required=True, type=Path)
    args = parser.parse_args(argv)
    try:
        record = verify_owned_session(
            args.repo, args.source_head, args.server_jar,
            args.server_sha256, args.java_exe, args.private_parent,
        )
        print(json.dumps(record, indent=2, sort_keys=True))
        return 0
    except (OwnedServerDenied, ProbeBlocked, OSError, ValueError,
            subprocess.TimeoutExpired, subprocess.SubprocessError) as exc:
        print(json.dumps({
            "result": "R26_OWNED_REAL_LOCALLAB_STDIN_LOOPBACK_BLOCKED",
            "error": str(exc),
            "existing_client_processes_terminated": 0,
            "graphical_client_started": False,
        }, indent=2, sort_keys=True), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
