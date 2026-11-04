#!/usr/bin/env python3
"""R6: real-JVM combat against explicitly custom mortal NPC -> loot -> bank -> reconnect.

Synthetic pinned revision317 client only. No claim about original SpawnPK
damage maths, NPC drops, graphical rendering, or Windows/crash durability.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import socket
import sys
import tempfile
import time

from synthetic_revision317_login import HOST, PORT
from synthetic_s2c_bank import decode_container53, require_bank_pair
from synthetic_ground_take_reconnect import (
    decode_spawn44, decode_remove156, require_scene_step,
    take_packet, wait_log_contains,
)
from synthetic_persistence_reconnect import (
    ReconnectFailure, log_tail, open_bank, ports_in_use, read_bootstrap_and_inventory,
    require_snapshot, start_server, stop_own_server, wait_session_final, next_frame,
)

FIXTURE_OPTION = ("-Dspk.local.r6MortalFixture=true",)
ITEM = 995
LOOT_X = 3088
LOOT_Y = 3495
START_BANK = 100_000
FINAL_BANK = 100_001


def attack_npc72(cipher, scene: int) -> bytes:
    """Exact C2S72: ISAAC opcode and scene index as big-endian short-A."""
    if not 0 <= scene < 16383:
        raise ValueError("invalid 14-bit NPC scene index")
    return bytes([(72 + cipher.next_int()) & 255,
                  scene >> 8, (scene + 128) & 255])


def walk164(cipher, x: int, y: int) -> bytes:
    """Pinned client C2S164: LE-A x, LE y, negated run, var-byte length."""
    for value in (x, y):
        if not 0 <= value <= 0xFFFF:
            raise ValueError("invalid tile")
    body = bytes([(x + 128) & 255, x >> 8, y & 255, y >> 8, 0])
    return bytes([(164 + cipher.next_int()) & 255, len(body)]) + body


def discover_fixture(logfile: Path) -> int:
    deadline = time.monotonic() + 10
    pattern = re.compile(
        r"R6_MORTAL_NPC_BOOTSTRAP scene=(\d+) def=1 "
        r"world=(\d+),(\d+) hp=10 loot=995x1"
    )
    while time.monotonic() < deadline:
        match = pattern.search(log_tail(logfile, 100000))
        if match:
            scene, x, y = map(int, match.groups())
            if (x, y) != (LOOT_X, LOOT_Y):
                raise ReconnectFailure(
                    f"custom mortal fixture in wrong tile {x},{y}"
                )
            return scene
        time.sleep(0.08)
    raise ReconnectFailure("explicit mortal NPC fixture spawn was not logged")


def kill_and_check_loot(sock, c2s, s2c, logfile: Path, scene: int):
    sock.sendall(attack_npc72(c2s, scene))
    got_spawn = None
    hits = 0
    deadline = time.monotonic() + 18
    while time.monotonic() < deadline:
        frame = next_frame(sock, s2c)
        if frame is None:
            continue
        opcode, payload = frame
        if opcode == 65:
            hits += 1
        elif opcode == 44:
            if got_spawn is not None:
                raise ReconnectFailure("duplicate NPC generated loot packet44")
            got_spawn = decode_spawn44(payload)
            if got_spawn[:2] != (ITEM, 1):
                raise ReconnectFailure(
                    f"NPC death created incorrect ground loot {got_spawn}"
                )
            break
        elif opcode == 53:
            widget, slots = decode_container53(payload)
            if widget == 3214 and slots[0] != (-1, 0):
                raise ReconnectFailure(
                    "NPC death gave inventory loot without Take"
                )
    if got_spawn is None:
        raise ReconnectFailure("no scene S2C44 NPC-generated ground loot")
    if hits < 1:
        raise ReconnectFailure("NPC attack had no server NPC65 publication")

    wait_log_contains(
        logfile,
        (f"R6_MORTAL_NPC_DEATH scene={scene}",
         "def=1 hpBefore=10 damage=10 hpAfter=0",
         "dropItem=995 amount=1",
         "authority=CUSTOM_LOCALLAB_OPT_IN_FIXTURE",
         "M2_HIT_PUBLISHED"),
        seconds=8,
    )
    return got_spawn


def go_to_loot_tile(sock, c2s, s2c, logfile: Path):
    sock.sendall(walk164(c2s, LOOT_X, LOOT_Y))
    deadline = time.monotonic() + 12
    moved = False
    while time.monotonic() < deadline:
        log = log_tail(logfile, 100000)
        if ("M5_AUTHORITATIVE_TICK mode=WALK tiles=1 from=3087,3495" in log
                or "M5_AUTHORITATIVE_TICK mode=RUN tiles=1 from=3087,3495" in log):
            moved = True
            break
        next_frame(sock, s2c, 0.15)
    if not moved:
        raise ReconnectFailure("could not reach NPC death loot tile")


def deposit_loot(sock, c2s, s2c):
    from synthetic_revision317_login import bank_action_145
    sock.sendall(bank_action_145(c2s, 5064, 0, ITEM))
    reported = {}
    deadline = time.monotonic() + 12
    while time.monotonic() < deadline:
        frame = next_frame(sock, s2c)
        if frame is None:
            continue
        if frame[0] == 53:
            widget, slots = decode_container53(frame[1])
            if widget in (5382, 5064):
                reported[widget] = slots
        if len(reported) == 2:
            require_bank_pair(reported, FINAL_BANK, (-1, 0))
            return
    raise ReconnectFailure("S2C53 did not publish deposited loot")


def run(jar: Path, java: str):
    jar = jar.expanduser().resolve(strict=True)
    if not jar.is_file():
        raise ReconnectFailure("built server JAR not present")
    if ports_in_use():
        raise ReconnectFailure("existing localhost server present; not touching it")
    with tempfile.TemporaryDirectory(prefix="spk-r6-mortal-loot-") as home:
        root = Path(home)
        account = root / "isolated-account" / "opensrc.properties"
        evidence = []
        for phase in (1, 2):
            process, logfile = start_server(
                java, jar, root, account, f"r6-jvm-{phase}",
                fixture_options=FIXTURE_OPTION,
            )
            try:
                with socket.create_connection((HOST, PORT), timeout=5) as sock:
                    sock.settimeout(7)
                    c2s, s2c, inv = read_bootstrap_and_inventory(sock)
                    if inv[0] != (-1, 0):
                        raise ReconnectFailure(
                            f"phase={phase} unexpected initial inventory={inv[0]}"
                        )
                    scene = discover_fixture(logfile)
                    if phase == 1:
                        spawn = kill_and_check_loot(sock, c2s, s2c, logfile, scene)
                        go_to_loot_tile(sock, c2s, s2c, logfile)
                        sock.sendall(take_packet(c2s, ITEM, LOOT_X, LOOT_Y))
                        removed = require_scene_step(sock, s2c, (ITEM, 1), 156)
                        if removed != (ITEM, spawn[2]):
                            raise ReconnectFailure(
                                f"NPC-generated loot Take mismatched spawn={spawn} remove={removed}"
                            )
                        wait_log_contains(
                            logfile,
                            ("V511_GROUND_TAKE",
                             "result=TAKE_ON_TILE_IMMEDIATE"),
                        )
                        # Player is now one tile closer to the HOME bank.
                        # Walk to 3089,3495 and open object26972 at 3090,3495.
                        open_bank(sock, c2s, s2c, START_BANK, (ITEM, 1))
                        deposit_loot(sock, c2s, s2c)
                    else:
                        open_bank(sock, c2s, s2c, FINAL_BANK, (-1, 0))
                wait_session_final(logfile, account)
                sha = require_snapshot(account, FINAL_BANK, 0)
                if phase == 2:
                    wait_log_contains(logfile, ("ACCOUNT_LOADED profile=opensrc",))
                evidence.append({
                    "process": phase,
                    "bank_coins": FINAL_BANK,
                    "inventory_coins": 0,
                    "account_sha256": sha,
                    "npc_killed": phase == 1,
                })
            finally:
                stop_own_server(process, logfile)

        return {
            "result": "R6_SYNTHETIC_CUSTOM_MORTAL_NPC_LOOT_RECONNECT_PASS",
            "custom_fixture_authority_only": True,
            "npc_death_generated_ground_item": True,
            "npc_death_ground_spawn_s2c44": True,
            "ground_take_s2c156_and_inventory53": True,
            "bank_loot_deposit_and_relogin": True,
            "fresh_jvm_count": 2,
            "net_bank_coin_gain": 1,
            "original_spawnpk_drop_table_tested": False,
            "original_graphical_client_tested": False,
            "windows_safe_world_save_tested": False,
            "crash_durability_tested": False,
            "phases": evidence,
        }


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--jar", type=Path,
        default=Path(__file__).resolve().parents[2] /
                "server" / "build" / "SpawnPKLocalServer.jar",
    )
    parser.add_argument("--java", default="java")
    args = parser.parse_args(argv)
    try:
        print(json.dumps(run(args.jar, args.java), indent=2, sort_keys=True))
        return 0
    except (ReconnectFailure, OSError, ValueError, AssertionError) as error:
        print(json.dumps({
            "result": "R6_SYNTHETIC_CUSTOM_MORTAL_NPC_LOOT_FAIL",
            "error": str(error),
        }, indent=2), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
