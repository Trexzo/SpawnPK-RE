#!/usr/bin/env python3
"""R42: v308 walking remains authoritative AFTER restore across three Main JVMs.

All three java server processes are private, sequential, loopback-only children.
Client protocol is synthetic v308-shaped, NOT the original graphical JAR.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import socket
import sys
import tempfile

from synthetic_persistence_reconnect import (
    ReconnectFailure, ports_in_use, start_server, stop_own_server,
    wait_session_final, read_bootstrap_and_inventory, log_tail,
)
from synthetic_revision317_login import HOST, PORT
from synthetic_v308_position_restart import (
    WALK_EAST_81, account_position, bits, wait_for_authoritative_walk,
)


def restore81(world_x: int, world_y: int, plane: int = 0) -> bytes:
    """Exact Java BootstrapPackets.player81TeleportNoAppearance wire bit order.

    The Java method's argument names are (plane, localX, localY), while
    LocalSessionBootstrapPublisher passes (plane, localY, localX).
    Here the field values deliberately follow that actual production call.
    """
    if plane != 0 or not (3032 <= world_x < 3136 and 3440 <= world_y < 3544):
        raise ReconnectFailure("R42 position outside persisted HOME plane/window")
    local_y, local_x = world_y - 3440, world_x - 3032
    return bits((
        (1, 1), (3, 2), (plane, 2), (1, 1), (0, 1),
        (local_y, 7), (local_x, 7), (0, 8), (2047, 11),
    ))


def walk164(world_x: int, world_y: int = 3495) -> bytes:
    """Exact pinned v308 C2S164 var-byte: LE short-A X; LE Y; negated run."""
    if not (3033 <= world_x < 3136 and 3440 <= world_y < 3544):
        raise ReconnectFailure("R42 invalid HOME waypoint")
    return bytes(((world_x + 128) & 255, (world_x >> 8) & 255,
                  world_y & 255, (world_y >> 8) & 255, 0))


def check_initial_restore(observed: list[bytes], expected: tuple[int, int] | None):
    wanted = [] if expected is None else [restore81(*expected)]
    if observed != wanted:
        raise ReconnectFailure(
            "R42 exact restored S2C81 missing, unexpected, or duplicate "
            f"observed={[a.hex() for a in observed]} wanted={[a.hex() for a in wanted]}"
        )


def journey(jar: Path, java: str) -> dict:
    jar = jar.expanduser().resolve(strict=True)
    if not jar.is_file():
        raise ReconnectFailure("R42 missing exact server JAR")
    if ports_in_use():
        raise ReconnectFailure("R42 occupied 43594 or 43595: refusing existing service")

    expected_start = (None, (3088, 3495), (3089, 3495))
    expected_after = ((3088, 3495, 0), (3089, 3495, 0), (3089, 3495, 0))
    with tempfile.TemporaryDirectory(prefix="spk-r42-v308-post-restore-walk-") as temp:
        root = Path(temp)
        account = root / "only-test-account" / "opensrc.properties"
        if account.exists():
            raise ReconnectFailure("R42 will not replace any profile")
        phases: list[dict] = []
        for phase in (1, 2, 3):
            # R4 harness refuses occupied ports, owns the exact newly launched
            # Main child, and gracefully stops it with a private stdin token
            # on Windows (never mass-kills java.exe).
            child, logfile = start_server(
                java, jar, root, account, f"r42-main-{phase}"
            )
            try:
                restore_frames: list[bytes] = []

                def witness(frame):
                    op, body = frame
                    if op == 81 and len(body) == 5:
                        restore_frames.append(body)

                with socket.create_connection((HOST, PORT), timeout=6) as sock:
                    sock.settimeout(8)
                    c2s, s2c, _ = read_bootstrap_and_inventory(
                        sock, on_frame=witness
                    )
                    check_initial_restore(restore_frames, expected_start[phase - 1])
                    if phase != 3:
                        destination = 3087 + phase
                        packet = walk164(destination)
                        sock.sendall(
                            bytes(((164 + c2s.next_int()) & 255, len(packet))) + packet
                        )
                        wait_for_authoritative_walk(sock, s2c)

                # Owned LocalSession must exit and publish a final profile
                # before request to shut down its dedicated server JVM.
                wait_session_final(logfile, account)
                position = account_position(account)
                expected = expected_after[phase - 1]
                if position != expected:
                    raise ReconnectFailure(
                        f"R42 server={phase} position after accepted walk was "
                        f"{position}, required {expected}"
                    )
                logs = log_tail(logfile, 150000)
                if "persistent=true" not in logs:
                    raise ReconnectFailure(f"R42 server={phase} persistent login absent")
                if phase > 1 and (
                    "ACCOUNT_LOADED profile=opensrc" not in logs
                    or f"V51214_POSITION_RESTORE world={expected_start[phase-1][0]},"
                       f"{expected_start[phase-1][1]},0" not in logs
                ):
                    raise ReconnectFailure(f"R42 server={phase} account restore absent")
                if phase < 3:
                    old_x = 3086 + phase
                    new_x = 3087 + phase
                    if (
                        f"M5_AUTHORITATIVE_TICK mode=WALK tiles=1 "
                        f"from={old_x},3495 to={new_x},3495" not in logs
                    ):
                        raise ReconnectFailure(
                            f"R42 server={phase} no authoritative resumed walking tick"
                        )
                phases.append({
                    "phase": phase,
                    "new_Main_JVM": True,
                    "restored_byte_exact": phase > 1,
                    "walk_after_restore": phase == 2,
                    "account_position": list(position),
                    "account_sha256": hashlib.sha256(account.read_bytes()).hexdigest(),
                })
            finally:
                stop_own_server(child, logfile)

        return {
            "result": "R42_THREE_MAIN_JVM_V308_POST_RESTORE_WALK_PASS",
            "fresh_server_jvms": 3,
            "first_walk_to": [3088, 3495],
            "second_jvm_continued_walk_to": [3089, 3495],
            "third_jvm_byte_exact_restored_position": [3089, 3495],
            "original_gui_launched": False,
            "uses_only_isolated_temporary_accounts": True,
            "guest_sandbox_launched": False,
            "phases": phases,
        }


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--jar", type=Path, default=Path(__file__).resolve().parents[2]
        / "server" / "build" / "SpawnPKLocalServer.jar"
    )
    parser.add_argument("--java", default="java")
    args = parser.parse_args(argv)
    try:
        print(json.dumps(journey(args.jar, args.java), indent=2, sort_keys=True))
        return 0
    except (ReconnectFailure, OSError, ValueError, AssertionError) as e:
        print(json.dumps({
            "result": "R42_THREE_MAIN_JVM_V308_POST_RESTORE_WALK_FAIL",
            "error": str(e),
        }, indent=2, sort_keys=True), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
