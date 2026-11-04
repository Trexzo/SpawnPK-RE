#!/usr/bin/env python3
"""R4 synthetic TCP bank-state persistence across TWO real Java JVM processes.

Runs an isolated LocalLab profile; no original GUI, Windows safe-publication,
crash/power-loss durability, multi-JVM concurrent lease, or public auth proof.
The sole persistent profile is created inside a fresh temporary directory.
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

from synthetic_revision317_login import (
    HOST, PORT, Isaac, _begin, bank_action_145, read_exact, u32,
)
from synthetic_s2c_bank import decode_container53, next_server_frame, require_bank_pair

AUX_PORT = 43595
OVERLAY = bytes([5292 >> 8, (5292 + 128) & 255, 5063 >> 8, 5063])
BOOTSTRAP = (249, 73, 81, 110, 126)


class ReconnectFailure(AssertionError):
    pass


def ports_in_use() -> bool:
    for port in (PORT, AUX_PORT):
        try:
            with socket.create_connection((HOST, port), timeout=0.25):
                return True
        except OSError:
            pass
    return False


def log_tail(path: Path, size=65536) -> str:
    if not path.is_file():
        return ""
    with path.open("rb") as f:
        f.seek(0, os.SEEK_END)
        f.seek(max(0, f.tell() - size))
        return f.read().decode("utf-8", "replace")


def start_server(java: str, jar: Path, root: Path, account: Path, label: str):
    if ports_in_use():
        raise ReconnectFailure("fixed loopback ports occupied; no external server touched")
    logfile = root / (label + ".log")
    cmd = [
        java, f"-Duser.home={root}",
        f"-Dspk.local.accountFile={account}",
        "-jar", str(jar), "--bootstrap", "--movement",
    ]
    with logfile.open("wb") as output:
        process = subprocess.Popen(
            cmd, cwd=root, stdin=subprocess.DEVNULL,
            stdout=output, stderr=subprocess.STDOUT,
            env=os.environ.copy(),
        )
    deadline = time.monotonic() + 30
    try:
        while time.monotonic() < deadline:
            if process.poll() is not None:
                raise ReconnectFailure(
                    f"{label} JVM exited before listen; exit={process.returncode} "
                    f"tail={log_tail(logfile, 4000)}"
                )
            log = log_tail(logfile, 8000)
            if ("SpawnPK Local Lab" in log
                    and "GAME  :" in log
                    and "AUX   :" in log):
                with socket.create_connection((HOST, PORT), timeout=1):
                    pass
                return process, logfile
            time.sleep(0.15)
        raise ReconnectFailure(f"{label} JVM startup timeout: {log_tail(logfile, 3500)}")
    except BaseException:
        if process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)
        raise


def stop_own_server(process, logfile: Path):
    if process.poll() is not None:
        raise ReconnectFailure(
            f"JVM unexpectedly exited before controlled stop, code={process.returncode}"
        )
    process.terminate()
    try:
        process.wait(timeout=20)
    except subprocess.TimeoutExpired as exc:
        process.kill()
        process.wait(timeout=5)
        raise ReconnectFailure("JVM graceful stop exceeded 20s") from exc
    # This validates orderly shutdown coordination of the test JVM, not
    # atomic-rename/fsync crash consistency of account publication.
    if "V5123_PERSISTENCE_SHUTDOWN_CLEAN" not in log_tail(logfile, 14000):
        raise ReconnectFailure(
            "shutdown did not log clean persistence termination "
            + log_tail(logfile, 3000)
        )
    deadline = time.monotonic() + 5
    while ports_in_use():
        if time.monotonic() >= deadline:
            raise ReconnectFailure("loopback ports still occupied after own JVM stopped")
        time.sleep(0.1)


def wait_session_final(logfile: Path, account: Path):
    deadline = time.monotonic() + 12
    while time.monotonic() < deadline:
        contents = log_tail(logfile, 100000)
        if ("V512_WORLD_UNREGISTER playerId=" in contents
                and account.is_file()):
            if ("V5123_ACCOUNT_FINAL_SAVE_WAIT_FAILED" in contents
                    or "V5123_ACCOUNT_LOAD_FAILED" in contents):
                raise ReconnectFailure("session reported persistence failure")
            return
        time.sleep(0.15)
    raise ReconnectFailure(
        "no verified final session teardown/account file: " + log_tail(logfile, 5000)
    )


def snapshot_slots(path: Path):
    """Read only two canonical Java Properties keys; independent file check."""
    if not path.is_file() or path.stat().st_size > 100_000:
        raise ReconnectFailure("account snapshot missing or oversized")
    found = {}
    for line in path.read_text(encoding="iso-8859-1").splitlines():
        if not line or line[0] in "#!":
            continue
        for key in ("bank.0", "inventory.0"):
            if line.startswith(key + "="):
                if key in found:
                    raise ReconnectFailure(f"duplicate persisted key {key}")
                found[key] = line.split("=", 1)[1].strip()
    if len(found) != 2:
        raise ReconnectFailure(f"required persisted slot keys missing: {sorted(found)}")
    return found


def require_snapshot(path: Path, bank_qty: int, inv_qty: int):
    got = snapshot_slots(path)
    want = {
        "bank.0": f"995,{bank_qty},0",
        "inventory.0": f"995,{inv_qty},0" if inv_qty else "",
    }
    if got != want:
        raise ReconnectFailure(f"file bank/inventory mismatch: got={got} expected={want}")
    return hashlib.sha256(path.read_bytes()).hexdigest()


def next_frame(sock, cipher, timeout=0.3):
    return next_server_frame(sock, cipher, read_exact, timeout)


def read_bootstrap_and_inventory(sock):
    seeds = _begin(sock, revision=317, username="localtest")
    if read_exact(sock, 3) != bytes([2, 205, 0]):
        raise ReconnectFailure("persistent alias login not admitted")
    c2s = Isaac(seeds)
    s2c = Isaac([u32(x + 50) for x in seeds])
    sock.sendall(bytes([(185 + c2s.next_int()) & 255, 912 >> 8, 912 & 255]))
    for expected in BOOTSTRAP:
        frame = next_frame(sock, s2c, 5)
        if frame is None or frame[0] != expected:
            raise ReconnectFailure(f"wrong initial bootstrap opcode {frame}, wanted {expected}")
    deadline = time.monotonic() + 12
    while time.monotonic() < deadline:
        frame = next_frame(sock, s2c)
        if frame is not None and frame[0] == 53:
            widget, slots = decode_container53(frame[1])
            if widget == 3214:
                if len(slots) != 28:
                    raise ReconnectFailure("normal inventory not 28 slots")
                return c2s, s2c, slots
    raise ReconnectFailure("normal inventory packet53 never arrived")


def open_bank(sock, c2s, s2c, expected_bank, expected_inventory):
    # Same exact pinned fixture as the successful R3 walk + object action.
    walk = bytes([(3089 + 128) & 255, 3089 >> 8, 3495 & 255, 3495 >> 8, 0])
    obj = bytes([(3090 + 128) & 255, 3090 >> 8,
                 26972 >> 8, 26972 & 255, 3495 >> 8, (3495 + 128) & 255])
    sock.sendall(
        bytes([(164 + c2s.next_int()) & 255, len(walk)]) + walk
        + bytes([(132 + c2s.next_int()) & 255]) + obj
    )
    overlay = False
    received = {}
    deadline = time.monotonic() + 17
    while time.monotonic() < deadline:
        frame = next_frame(sock, s2c)
        if frame is None:
            continue
        op, payload = frame
        if op == 248 and payload == OVERLAY:
            overlay = True
            received.clear()
        if op == 53 and overlay:
            widget, slots = decode_container53(payload)
            if widget in (5382, 5064):
                received[widget] = slots
        if overlay and len(received) == 2:
            require_bank_pair(received, expected_bank, expected_inventory)
            return
    raise ReconnectFailure("real-JVM bank overlay/53 pair not observed")


def withdraw_one(sock, c2s, s2c):
    sock.sendall(bank_action_145(c2s, 5382, 0, 995))
    received = {}
    deadline = time.monotonic() + 12
    while time.monotonic() < deadline:
        frame = next_frame(sock, s2c)
        if frame is None:
            continue
        if frame[0] == 53:
            widget, slots = decode_container53(frame[1])
            if widget in (5382, 5064):
                received[widget] = slots
        if len(received) == 2:
            require_bank_pair(received, 99_999, (995, 1))
            return
    raise ReconnectFailure("real JVM did not send updated bank and inventory53")


def run_journey(jar: Path, java: str):
    jar = jar.expanduser().resolve(strict=True)
    if not jar.is_file():
        raise ReconnectFailure("built JAR missing")
    if ports_in_use():
        raise ReconnectFailure("existing server detected; no existing process touched")
    with tempfile.TemporaryDirectory(prefix="spk-r4-process-reconnect-") as dirname:
        root = Path(dirname)
        account = root / "only-test-account" / "opensrc.properties"
        if account.exists():
            raise ReconnectFailure("test profile must be new")
        receipts = []
        try:
            for phase in (1, 2):
                process, logfile = start_server(
                    java, jar, root, account, f"process-{phase}"
                )
                try:
                    with socket.create_connection((HOST, PORT), timeout=6) as sock:
                        sock.settimeout(6)
                        c2s, s2c, initial_inv = read_bootstrap_and_inventory(sock)
                        wanted_inventory = (-1, 0) if phase == 1 else (995, 1)
                        if initial_inv[0] != wanted_inventory:
                            raise ReconnectFailure(
                                f"phase={phase} login inventory={initial_inv[0]}"
                                f" expected={wanted_inventory}"
                            )
                        open_bank(sock, c2s, s2c, 100_000 if phase == 1 else 99_999,
                                  wanted_inventory)
                        if phase == 1:
                            withdraw_one(sock, c2s, s2c)
                    # Real socket EOF triggers LocalSession final-save and World
                    # unregister; do not stop the JVM before both occur.
                    wait_session_final(logfile, account)
                    account_sha = require_snapshot(account, 99_999, 1)
                    phase_log = log_tail(logfile, 100000)
                    if "persistent=true" not in phase_log:
                        raise ReconnectFailure("persistent alias was not selected")
                    if phase == 2 and "ACCOUNT_LOADED profile=opensrc" not in phase_log:
                        raise ReconnectFailure("fresh World did not load persisted account")
                    receipts.append({
                        "phase": phase,
                        "new_java_process": True,
                        "bank_coins": 99_999,
                        "inventory_coins": 1,
                        "account_sha256": account_sha,
                    })
                finally:
                    stop_own_server(process, logfile)
            return {
                "result": "R4_SYNTHETIC_FRESH_JVM_RECONNECT_PASS",
                "profiles_isolated_in_temp": True,
                "fresh_jvm_count": 2,
                "normal_inventory53_relogin_verified": True,
                "bank53_relogin_verified": True,
                "file_snapshot_relogin_verified": True,
                "net_bank_delta": -1,
                "net_inventory_delta": 1,
                "phases": receipts,
                "original_gui_tested": False,
                "crash_power_loss_durability_tested": False,
                "multi_jvm_concurrent_ownership_tested": False,
                "windows_safe_world_save_tested": False,
            }
        except BaseException:
            # No persistent artifacts leave the temporary directory, even on
            # failure; errors from the own JVM's teardown still propagate.
            raise


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--jar", type=Path,
        default=Path(__file__).resolve().parents[2] / "server" / "build" /
                "SpawnPKLocalServer.jar",
    )
    parser.add_argument("--java", default="java")
    args = parser.parse_args(argv)
    try:
        print(json.dumps(run_journey(args.jar, args.java), indent=2, sort_keys=True))
        return 0
    except (ReconnectFailure, OSError, ValueError, AssertionError) as exc:
        print(json.dumps({"result": "R4_FRESH_JVM_RECONNECT_FAIL",
                          "error": str(exc)}, indent=2), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
