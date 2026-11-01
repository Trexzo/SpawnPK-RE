#!/usr/bin/env python3
"""Isolated boot-only smoke for SpawnPK LocalLab (no client or saved accounts).

NOT a gameplay, graceful-shutdown, final-save, cross-JVM, crash durability,
Windows World-save, or original graphical-client acceptance test.
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

HOST = "127.0.0.1"
GAME_PORT = 43594
AUX_PORT = 43595


class SmokeFailure(RuntimeError):
    pass


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def port_accepts(host: str, port: int, timeout: float = 0.5) -> bool:
    try:
        with socket.create_connection((host, port), timeout=timeout):
            return True
    except OSError:
        return False


def probe_aux(host: str, port: int, timeout: float = 1.0) -> bool:
    with socket.create_connection((host, port), timeout=timeout) as sock:
        sock.settimeout(timeout)
        sock.sendall(
            b"GET /spk_live/versions.txt HTTP/1.1\r\n"
            b"Host: 127.0.0.1\r\nConnection: close\r\n\r\n"
        )
        response = bytearray()
        while len(response) < 4096:
            try:
                chunk = sock.recv(4096 - len(response))
            except socket.timeout:
                break
            if not chunk:
                break
            response.extend(chunk)
            if b"\r\n\r\n" in response:
                break
        status = bytes(response).split(b"\r\n", 1)[0]
        return status in (b"HTTP/1.1 200 OK", b"HTTP/1.0 200 OK")


def tail_log(path: Path, limit: int = 2800) -> str:
    if not path.exists():
        return ""
    with path.open("rb") as source:
        source.seek(0, os.SEEK_END)
        source.seek(max(0, source.tell() - limit))
        return source.read().decode("utf-8", "replace")[-limit:]


def run_smoke(jar: Path, java: str, startup_timeout: float,
              synthetic_login: bool = False, walk_bank: bool = False) -> dict:
    if walk_bank and not synthetic_login:
        raise SmokeFailure('walk_bank requires synthetic_login')
    jar = jar.expanduser().resolve(strict=True)
    if not jar.is_file():
        raise SmokeFailure("JAR path is not a regular file")
    if startup_timeout <= 0 or startup_timeout > 120:
        raise SmokeFailure("startup timeout must be between 0 and 120 seconds")

    # Do not attach to, reuse, or terminate an already-running game server.
    for port in (GAME_PORT, AUX_PORT):
        if port_accepts(HOST, port):
            raise SmokeFailure(
                f"localhost port {port} already accepts connections; existing server left untouched"
            )

    artifact_sha = sha256(jar)
    with tempfile.TemporaryDirectory(prefix="spk-isolated-boot-") as home:
        root = Path(home)
        log_path = root / "server.log"
        cmd = [java, f"-Duser.home={root}", "-jar", str(jar)]
        if synthetic_login:
            cmd.append("--bootstrap")
        if walk_bank:
            cmd.append("--movement")
        try:
            with log_path.open("w+b") as log:
                process = subprocess.Popen(
                    cmd, cwd=root, stdin=subprocess.DEVNULL,
                    stdout=log, stderr=subprocess.STDOUT,
                    env=os.environ.copy(),
                )
                try:
                    deadline = time.monotonic() + startup_timeout
                    game_up = False
                    aux_up = False
                    while time.monotonic() < deadline:
                        if process.poll() is not None:
                            raise SmokeFailure(
                                f"server exited before both sockets were ready (exit {process.returncode})"
                            )
                        if not game_up:
                            game_up = port_accepts(HOST, GAME_PORT, 0.1)
                        if not aux_up:
                            try:
                                aux_up = probe_aux(HOST, AUX_PORT, 0.3)
                            except OSError:
                                pass
                        if game_up and aux_up:
                            log_text = tail_log(log_path, 8000)
                            # Ensure this JVM actually announced the sockets,
                            # not an unrelated process racing for fixed ports.
                            if not (
                                "SpawnPK Local Lab" in log_text
                                and "GAME  :" in log_text
                                and "AUX   :" in log_text
                            ):
                                time.sleep(0.15)
                                continue
                            if process.poll() is not None:
                                raise SmokeFailure(
                                    "server exited immediately after sockets became ready"
                                )
                            login_receipt = {}
                            if synthetic_login:
                                from synthetic_revision317_login import (
                                    accepted_login_and_bootstrap, rejected_revision,
                                )
                                try:
                                    rejected_revision()
                                    cutoff = time.monotonic() + 3
                                    while "expected protocol revision 317, got 316" not in tail_log(log_path, 12000):
                                        if time.monotonic() >= cutoff:
                                            raise SmokeFailure("invalid revision denial not observed in server log")
                                        time.sleep(0.1)
                                    login_receipt = accepted_login_and_bootstrap(
                                        walk_bank=walk_bank,
                                        read_server_log=lambda: tail_log(log_path, 65000),
                                    )
                                    cutoff = time.monotonic() + 10
                                    while True:
                                        session_log = tail_log(log_path, 18000)
                                        if ("V512_WORLD_REGISTER playerId=" in session_log
                                                and "LOGIN_SUCCESS_LOCAL" in session_log
                                                and "V512_WORLD_UNREGISTER playerId=" in session_log):
                                            break
                                        if process.poll() is not None or time.monotonic() >= cutoff:
                                            raise SmokeFailure("synthetic session registration/unregister not observed")
                                        time.sleep(0.2)
                                except (AssertionError, OSError) as exc:
                                    raise SmokeFailure(f"synthetic revision317 wire probe failed: {exc}") from exc
                            return {
                                "result": (
                                    "BOOT_AND_SYNTHETIC_WALK_BANK_PASS" if walk_bank else
                                    "BOOT_AND_SYNTHETIC_LOGIN_PASS" if synthetic_login else
                                    "BOOT_ONLY_PASS"
                                ),
                                "synthetic_authoritative_walk_tested": walk_bank,
                                "synthetic_bank_open_tested": walk_bank,
                                "synthetic_revision317": login_receipt if synthetic_login else None,
                                "synthetic_invalid_revision_denied": synthetic_login,
                                "synthetic_world_unregister_observed": synthetic_login,
                                "jar_sha256": artifact_sha,
                                "loopback_game_port": GAME_PORT,
                                "loopback_aux_port": AUX_PORT,
                                "aux_versions_http_200": True,
                                "isolated_temp_home": True,
                                "original_client_gui_tested": False,
                                "account_persistence_tested": False,
                                "clean_shutdown_tested": False,
                                "crash_durability_tested": False,
                                "windows_positive_world_save_tested": False,
                            }
                        time.sleep(0.15)
                    raise SmokeFailure(
                        "timed out waiting for both game and auxiliary endpoints"
                    )
                except SmokeFailure as exc:
                    raise SmokeFailure(
                        f"{exc}; isolated server output tail: {tail_log(log_path)}"
                    ) from exc
                finally:
                    # Cleanup of a temporary empty instance, not a validated
                    # graceful account-finalization or save operation.
                    if process.poll() is None:
                        process.terminate()
                        try:
                            process.wait(timeout=5)
                        except subprocess.TimeoutExpired:
                            process.kill()
                            process.wait(timeout=5)
        except OSError as exc:
            raise SmokeFailure(
                f"unable to start isolated Java process: {exc}"
            ) from exc


def main(argv=None) -> int:
    root = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--jar", type=Path,
        default=root / "server" / "build" / "SpawnPKLocalServer.jar"
    )
    parser.add_argument(
        "--java", default="java", help="JDK/JRE executable (default: java)"
    )
    parser.add_argument("--startup-timeout", type=float, default=20)
    parser.add_argument("--synthetic-login", action="store_true",
                        help="test revision-317 ISAAC wire bootstrap; NOT original GUI")
    parser.add_argument("--walk-bank", action="store_true",
                        help="with --synthetic-login, test movement and bank using isolated JVM")
    args = parser.parse_args(argv)
    try:
        print(json.dumps(
            run_smoke(args.jar, args.java, args.startup_timeout,
                      args.synthetic_login, args.walk_bank),
            indent=2, sort_keys=True,
        ))
        return 0
    except (SmokeFailure, OSError, ValueError) as exc:
        print(json.dumps(
            {"result": "BOOT_ONLY_FAIL", "error": str(exc)}, indent=2
        ), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
