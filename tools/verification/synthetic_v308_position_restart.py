#!/usr/bin/env python3
"""R41: synthetic v308 position restoration across two *separate Main JVMs*.

No original graphical client or account, no remote network, and no host Java
process termination. Uses R4's owned-child private stdin shutdown contract.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import socket
import sys
import tempfile
import time

from synthetic_persistence_reconnect import (
    ReconnectFailure, ports_in_use, start_server, stop_own_server,
    wait_session_final, read_bootstrap_and_inventory, next_frame, log_tail,
)
from synthetic_revision317_login import HOST, PORT


def bits(fields: tuple[tuple[int, int], ...]) -> bytes:
    """Exact MSB-first BitWriter layout, independently decoded from the v308 packet."""
    value = 0
    count = 0
    for field, width in fields:
        if not 1 <= width <= 31 or not 0 <= field < (1 << width):
            raise ValueError("R41 invalid bit-field width/value")
        value = (value << width) | field
        count += width
    value <<= (-count) % 8
    return value.to_bytes((count + 7) // 8, "big")


# Java BootstrapPackets.player81WalkStep(4) and
# BootstrapPackets.player81TeleportNoAppearance(0,55,56) byte-parity.
WALK_EAST_81 = bits(((1, 1), (1, 2), (4, 3), (0, 1), (0, 8), (2047, 11)))
# BitWriter's zero-width count is not legal here: encode the 8-bit zero normally.
RESTORED_3088_3495_81 = bits((
    (1, 1), (3, 2), (0, 2), (1, 1), (0, 1),
    (55, 7), (56, 7), (0, 8), (2047, 11),
))
HOME_WALK = bytes(((3088 + 128) & 255, 3088 >> 8,
                   3495 & 255, 3495 >> 8, 0))


def account_position(account: Path) -> tuple[int, int, int]:
    """Independent bounded inspection of only the three canonical schema keys."""
    if account.is_symlink() or not account.is_file():
        raise ReconnectFailure("R41 private profile missing/not a regular file")
    if account.stat().st_size > 128_000:
        raise ReconnectFailure("R41 private account file too large")
    expected = ("movement.worldX", "movement.worldY", "movement.plane")
    values: dict[str, str] = {}
    for line in account.read_text(encoding="iso-8859-1").splitlines():
        if not line or line.startswith(("#", "!")):
            continue
        for key in expected:
            if line.startswith(key + "="):
                if key in values:
                    raise ReconnectFailure("R41 duplicate persisted coordinate")
                values[key] = line[len(key) + 1:].strip()
    if len(values) != 3:
        raise ReconnectFailure("R41 missing persisted movement fields")
    try:
        return tuple(int(values[key]) for key in expected)
    except ValueError as e:
        raise ReconnectFailure("R41 invalid persistent coordinate field") from e


def require_restored_packets(frames: list[bytes], phase: int) -> None:
    """Capture only post-login opcode81s before normal inventory53."""
    if phase == 1:
        if frames:
            raise ReconnectFailure("R41 new account unexpectedly teleported on login")
    else:
        if frames != [RESTORED_3088_3495_81]:
            raise ReconnectFailure(
                "R41 second Java JVM did not send exactly one byte-exact saved-position S2C81 "
                f"got={[x.hex() for x in frames]}"
            )


def wait_for_authoritative_walk(sock, server_cipher) -> None:
    deadline = time.monotonic() + 14
    while time.monotonic() < deadline:
        frame = next_frame(sock, server_cipher, timeout=0.35)
        if frame is None:
            continue
        opcode, body = frame
        if opcode == 81 and body == WALK_EAST_81:
            return
    raise ReconnectFailure("R41 no exact v308 S2C81 walking step on real Main JVM")


def journey(jar: Path, java: str) -> dict:
    jar = jar.expanduser().resolve(strict=True)
    if not jar.is_file():
        raise ReconnectFailure("R41 exact server JAR missing")
    if ports_in_use():
        raise ReconnectFailure("R41 occupied loopback ports; refused to touch other server")
    with tempfile.TemporaryDirectory(prefix="spk-r41-v308-jvm-restore-") as dirname:
        root = Path(dirname)
        account = root / "only-test-account" / "opensrc.properties"
        if account.exists():
            raise ReconnectFailure("R41 would overwrite existing account")
        evidence: list[dict] = []
        for phase in (1, 2):
            process, logfile = start_server(
                java, jar, root, account, f"r41-server-phase-{phase}"
            )
            try:
                observed_restores: list[bytes] = []

                def frame_witness(frame):
                    opcode, payload = frame
                    if opcode == 81 and len(payload) == 5:
                        observed_restores.append(payload)

                with socket.create_connection((HOST, PORT), timeout=6) as sock:
                    sock.settimeout(8)
                    c2s, s2c, _ = read_bootstrap_and_inventory(
                        sock, on_frame=frame_witness
                    )
                    require_restored_packets(observed_restores, phase)
                    if phase == 1:
                        sock.sendall(
                            bytes(((164 + c2s.next_int()) & 255, 5)) + HOME_WALK
                        )
                        wait_for_authoritative_walk(sock, s2c)

                # A real TCP EOF must finish LocalSession cleanup and
                # final-save before shutting down the specific Main JVM.
                wait_session_final(logfile, account)
                coordinates = account_position(account)
                if coordinates != (3088, 3495, 0):
                    raise ReconnectFailure(
                        f"R41 phase={phase} incorrect durable saved position {coordinates}"
                    )
                server_log = log_tail(logfile, 130000)
                if "persistent=true" not in server_log:
                    raise ReconnectFailure("R41 missing persistent login marker")
                if phase == 1 and (
                    "M5_MOVEMENT_REQUEST" not in server_log
                    or "M5_AUTHORITATIVE_TICK mode=WALK" not in server_log
                ):
                    raise ReconnectFailure("R41 first JVM has no accepted authoritative walk")
                if phase == 2 and (
                    "ACCOUNT_LOADED profile=opensrc" not in server_log
                    or "V51214_POSITION_RESTORE world=3088,3495,0" not in server_log
                ):
                    raise ReconnectFailure("R41 new JVM did not load and restore position")
                evidence.append({
                    "phase": phase,
                    "distinct_Main_JVM": True,
                    "position": list(coordinates),
                    "restored_opcode81_hex": (
                        RESTORED_3088_3495_81.hex() if phase == 2 else None
                    ),
                    "account_sha256": hashlib.sha256(account.read_bytes()).hexdigest(),
                })
            finally:
                stop_own_server(process, logfile)

        return {
            "result": "R41_TWO_REAL_JVM_V308_PERSISTENT_POSITION_RESTORE_PASS",
            "fresh_main_jvms": 2,
            "v308_shaped_synthetic_client": True,
            "first_authoritative_walk81_exact": True,
            "second_jvm_restored_position81_exact": True,
            "saved_coordinates": [3088, 3495, 0],
            "two_graceful_owned_jvm_shutdowns": True,
            "private_temp_account_only": True,
            "original_graphical_v308_client_tested": False,
            "sandbox_guest_tested": False,
            "phases": evidence,
        }


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--jar", type=Path,
        default=Path(__file__).resolve().parents[2]
        / "server" / "build" / "SpawnPKLocalServer.jar"
    )
    parser.add_argument("--java", default="java")
    args = parser.parse_args(argv)
    try:
        print(json.dumps(journey(args.jar, args.java), sort_keys=True, indent=2))
        return 0
    except (ReconnectFailure, OSError, ValueError, AssertionError) as error:
        print(json.dumps({
            "result": "R41_TWO_REAL_JVM_V308_RECONNECT_FAILED",
            "error": str(error),
        }, sort_keys=True, indent=2), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
