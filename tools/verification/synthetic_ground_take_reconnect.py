#!/usr/bin/env python3
"""R5 real-JVM socket proof: ordinary Drop -> visible ground -> Take -> reconnect.

Custom LocalLab, synthetic revision317 client only. This is not NPC kill/drop,
graphical-client acceptance, crash-durable saving or Windows publication proof.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import socket
import sys
import tempfile
import time

from synthetic_revision317_login import HOST, PORT
from synthetic_s2c_bank import decode_container53
from synthetic_persistence_reconnect import (
    ReconnectFailure, log_tail, open_bank, ports_in_use, read_bootstrap_and_inventory,
    require_snapshot, start_server, stop_own_server, wait_session_final, withdraw_one,
    next_frame,
)

BANK_INITIAL = 100000
BANK_AFTER_WITHDRAW = 99999
COIN_ID = 995
DROP_AT = (3089, 3495)


def be_a(value):
    if value < 0 or value > 65535:
        raise ValueError("value not a u16")
    return bytes([value >> 8, (value + 128) & 255])


def drop_packet(cipher, item=995, widget=3214, slot=0):
    """Pinned C2S87 item BE-A, widget ordinary BE, slot BE-A."""
    return (
        bytes([(87 + cipher.next_int()) & 255])
        + be_a(item)
        + bytes([widget >> 8, widget & 255])
        + be_a(slot)
    )


def take_packet(cipher, item=995, x=3089, y=3495):
    """Pinned C2S236 (ground option 3): world Y LE, item BE, world X LE."""
    for v in (item, x, y):
        if v < 0 or v > 65535:
            raise ValueError("ground field not u16")
    return bytes([(236 + cipher.next_int()) & 255]) + bytes([
        y & 255, y >> 8, item >> 8, item & 255, x & 255, x >> 8,
    ])


def decode_spawn44(body):
    if len(body) != 5:
        raise ReconnectFailure("S2C44 wrong body length")
    item_id = ((body[1] << 8) | ((body[0] - 128) & 255))
    amount = (body[2] << 8) | body[3]
    return item_id, amount, body[4]


def decode_remove156(body):
    if len(body) != 3:
        raise ReconnectFailure("S2C156 wrong body length")
    coord = (body[0] - 128) & 255
    item_id = (body[1] << 8) | body[2]
    return item_id, coord


def await_normal_inventory(sock, cipher, wanted, seconds=10):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        frame = next_frame(sock, cipher)
        if frame is not None and frame[0] == 53:
            widget, slots = decode_container53(frame[1])
            if widget == 3214:
                if len(slots) != 28:
                    raise ReconnectFailure("inventory3214 slot length != 28")
                if slots[0] != wanted:
                    raise ReconnectFailure(
                        f"inventory3214 unexpected slot0={slots[0]}, expected={wanted}"
                    )
                if any(item != -1 for item, _ in slots[1:]):
                    raise ReconnectFailure("unexpected inventory items in other slots")
                return
    raise ReconnectFailure(f"S2C53 normal-inventory missing wanted={wanted}")


def require_scene_step(sock, cipher, expected_item_state, scene_opcode, seconds=12):
    """Require inventory update and exact scene mutation from one gameplay action."""
    deadline = time.monotonic() + seconds
    seen_inv = False
    scene = None
    while time.monotonic() < deadline:
        frame = next_frame(sock, cipher)
        if frame is None:
            continue
        op, body = frame
        if op == 53:
            widget, slots = decode_container53(body)
            if widget == 3214:
                if len(slots) != 28 or slots[0] != expected_item_state:
                    raise ReconnectFailure(
                        f"wrong S2C53 inventory for scene step: {widget} {slots[0]}"
                    )
                seen_inv = True
        elif op == scene_opcode:
            if scene is not None:
                raise ReconnectFailure("duplicate ground scene update")
            scene = decode_spawn44(body) if op == 44 else decode_remove156(body)
        elif op in (44, 156):
            raise ReconnectFailure(f"unexpected ground scene op={op}; wanted={scene_opcode}")
        if seen_inv and scene is not None:
            return scene
    raise ReconnectFailure(
        f"missing S2C53 inventory and/or scene opcode {scene_opcode}; "
        f"inventory={seen_inv} scene={scene}"
    )


def wait_log_contains(logfile, tokens, seconds=6):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        log = log_tail(logfile, 100000)
        if all(token in log for token in tokens):
            return
        time.sleep(0.08)
    raise ReconnectFailure(f"server action log missing {tokens}: "+log_tail(logfile, 5000))


def run(jar: Path, java: str):
    jar = jar.expanduser().resolve(strict=True)
    if not jar.is_file():
        raise ReconnectFailure("built Java JAR missing")
    if ports_in_use():
        raise ReconnectFailure("existing loopback server; will not touch it")
    with tempfile.TemporaryDirectory(prefix="spk-r5-ground-take-") as directory:
        root = Path(directory)
        account = root / "accounts" / "opensrc.properties"
        results = []
        for phase in (1, 2):
            process, logfile = start_server(java, jar, root, account, f"r5-process-{phase}")
            try:
                with socket.create_connection((HOST, PORT), timeout=6) as sock:
                    sock.settimeout(6)
                    c2s, s2c, initial = read_bootstrap_and_inventory(sock)
                    expected_login = (-1, 0) if phase == 1 else (995, 1)
                    if initial[0] != expected_login:
                        raise ReconnectFailure(
                            f"JVM {phase} initial S2C53 inventory={initial[0]}, "
                            f"expected={expected_login}"
                        )
                    open_bank(sock, c2s, s2c, BANK_INITIAL if phase == 1
                              else BANK_AFTER_WITHDRAW, expected_login)
                    if phase == 1:
                        withdraw_one(sock, c2s, s2c)
                        # The exact pinned client closes its bank with C2S130.
                        # BankUI restores normal inventory widget 3214, which
                        # is required by the exact inventory option-5 Drop writer.
                        sock.sendall(bytes([(130 + c2s.next_int()) & 255]))
                        await_normal_inventory(sock, s2c, (995, 1))
                        wait_log_contains(logfile, ("V522_INTERFACE_CLOSE opcode=130", "bankWasOpen=true"))

                        sock.sendall(drop_packet(c2s))
                        spawn = require_scene_step(sock, s2c, (-1, 0), 44)
                        if spawn[0] != 995 or spawn[1] != 1:
                            raise ReconnectFailure(f"wrong ground spawn S2C44: {spawn}")
                        wait_log_contains(logfile, ("V511_GROUND_DROP", "result=DROP_OK amount=1"))

                        sock.sendall(take_packet(c2s))
                        removed = require_scene_step(sock, s2c, (995, 1), 156)
                        if removed != (995, spawn[2]):
                            raise ReconnectFailure(
                                f"ground remove identity mismatch spawn={spawn} remove={removed}"
                            )
                        wait_log_contains(
                            logfile,
                            ("V511_GROUND_TAKE", "result=TAKE_ON_TILE_IMMEDIATE"),
                        )

                        # A subsequent Take of the same (now-removed) ground
                        # item must be rejected, without a duplicate inventory
                        # packet or new ground-item publish.
                        sock.sendall(take_packet(c2s))
                        wait_log_contains(
                            logfile, ("GroundItemInteraction{opcode=236",
                                      "result=REJECTED_NOT_VISIBLE_OR_MISSING"),
                        )
                        cutoff = time.monotonic() + 0.8
                        while time.monotonic() < cutoff:
                            frame = next_frame(sock, s2c, 0.08)
                            if frame is not None and frame[0] in (44, 156):
                                raise ReconnectFailure("duplicate Take published scene change")
                            if frame is not None and frame[0] == 53:
                                widget, _ = decode_container53(frame[1])
                                if widget == 3214:
                                    raise ReconnectFailure("duplicate Take mutated normal inventory")
                wait_session_final(logfile, account)
                require_snapshot(account, BANK_AFTER_WITHDRAW, 1)
                if phase == 2:
                    wait_log_contains(logfile, ("ACCOUNT_LOADED profile=opensrc",))
                results.append({
                    "jvm": phase,
                    "bank_coins": BANK_AFTER_WITHDRAW,
                    "inventory_coins": 1,
                    "drop_take_wire_verified": phase == 1,
                })
            finally:
                stop_own_server(process, logfile)
        return {
            "result": "R5_SYNTHETIC_GROUND_DROP_TAKE_RECONNECT_PASS",
            "fresh_jvm_count": 2,
            "account_only_in_temp_directory": True,
            "synthetic_ground_spawn44_observed": True,
            "synthetic_ground_remove156_observed": True,
            "normal_inventory53_drop_take_observed": True,
            "double_take_denied": True,
            "persistent_bank_and_inventory_relogin": True,
            "npc_kill_loot_tested": False,
            "original_gui_tested": False,
            "crash_durability_tested": False,
            "windows_account_save_tested": False,
            "phases": results,
        }


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--jar", type=Path,
        default=Path(__file__).resolve().parents[2] / "server" /
                "build" / "SpawnPKLocalServer.jar",
    )
    parser.add_argument("--java", default="java")
    args = parser.parse_args(argv)
    try:
        print(json.dumps(run(args.jar, args.java), indent=2, sort_keys=True))
        return 0
    except (ReconnectFailure, ValueError, OSError, AssertionError) as error:
        print(json.dumps({"result": "R5_SYNTHETIC_GROUND_DROP_TAKE_FAIL",
                          "error": str(error)}, indent=2), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
