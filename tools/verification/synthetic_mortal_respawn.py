#!/usr/bin/env python3
"""R7: custom 25-HP PvM multi-hit, non-reused NPC respawn and 2-loot persistence.

Runs one isolated Java JVM with explicitly enabled R6+R7 fixtures, then a
different JVM using the same *temporary* account. A synthetic revision317
client validates server-issued S2C44/156/53 and logs for the 3-hit HP lifecycle.
No original SpawnPK mechanics/GUI, crash recovery or Windows account evidence.
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

from synthetic_revision317_login import HOST, PORT, bank_action_145
from synthetic_s2c_bank import decode_container53, require_bank_pair
from synthetic_ground_take_reconnect import (
    decode_spawn44, require_scene_step, take_packet, wait_log_contains,
)
from synthetic_mortal_pvm_loot import (
    attack_npc72, walk164,
)
from synthetic_persistence_reconnect import (
    ReconnectFailure, log_tail, open_bank, ports_in_use,
    read_bootstrap_and_inventory, require_snapshot,
    start_server, stop_own_server, wait_session_final, next_frame,
)

OPTIONS = (
    "-Dspk.local.r6MortalFixture=true",
    "-Dspk.local.r7LifecycleFixture=true",
)
COIN = 995
BASE_BANK = 100_000
FINAL_BANK = 100_002
R8_OPTION = "-Dspk.local.r8PvmXp=true"
DEFAULT_ATTACK_XP = 13_034_431


def decode_skill134(body):
    if len(body) != 6:
        raise ReconnectFailure("wrong fixed-size S2C134")
    skill = body[0]
    xp = (body[3] << 24) | (body[4] << 16) | (body[1] << 8) | body[2]
    return skill, xp, body[5]


class AttackXpProbe:
    def __init__(self):
        self.updates = []

    def observe(self, frame):
        if frame is None or frame[0] != 134:
            return
        skill, xp, current = decode_skill134(frame[1])
        if skill == 0:
            if current != 99:
                raise ReconnectFailure(f"R8 changed current Attack level to {current}")
            self.updates.append(xp)

    def require_progression(self, before_count, expected_steps):
        needed = [DEFAULT_ATTACK_XP + 10 * n
                  for n in expected_steps]
        got = self.updates[before_count:]
        if got != needed:
            raise ReconnectFailure(
                f"XP S2C134 updates {got}, expected {needed}"
            )


def require_persisted_xp(path, expected):
    if path.stat().st_size > 100_000:
        raise ReconnectFailure("oversized account file")
    seen = []
    for row in path.read_text(encoding="iso-8859-1").splitlines():
        if row.startswith("skill.0.xp="):
            seen.append(row.split("=", 1)[1].strip())
    if seen != [str(expected)]:
        raise ReconnectFailure(f"R8 attack XP file mismatch {seen} vs {expected}")


def find_initial_actor(logfile: Path):
    regex = re.compile(
        r"R6_MORTAL_NPC_BOOTSTRAP scene=(\d+) def=1 "
        r"world=(\d+),(\d+) hp=25 loot=995x1"
    )
    deadline = time.monotonic() + 10
    while time.monotonic() < deadline:
        found = regex.search(log_tail(logfile))
        if found:
            scene, x, y = map(int, found.groups())
            if (x, y) != (3088, 3495):
                raise ReconnectFailure("initial test actor was not east of HOME")
            return scene, x, y
        time.sleep(0.08)
    raise ReconnectFailure("no R7 25-HP test actor spawned")


def await_kill(sock, c2s, s2c, logfile, scene, xp_probe=None):
    before_xp = len(xp_probe.updates) if xp_probe is not None else 0
    sock.sendall(attack_npc72(c2s, scene))
    deadline = time.monotonic() + 24
    ground_spawn = None
    npc_updates = 0
    while time.monotonic() < deadline:
        frame = next_frame(sock, s2c)
        if frame is None:
            continue
        op, data = frame
        if xp_probe is not None:
            xp_probe.observe(frame)
        if op == 65:
            npc_updates += 1
        elif op == 44:
            if ground_spawn is not None:
                raise ReconnectFailure("duplicate ground loot spawn on same kill")
            ground_spawn = decode_spawn44(data)
            if ground_spawn[:2] != (COIN, 1):
                raise ReconnectFailure("incorrect R7 death loot: "+str(ground_spawn))
            break
        elif op == 53:
            widget, slots = decode_container53(data)
            if widget == 3214 and slots[0] not in ((-1, 0), (COIN, 1)):
                raise ReconnectFailure("unexpected inventory mutation during PvM")
    if ground_spawn is None or npc_updates < 3:
        raise ReconnectFailure(
            f"3-attack death missing scene packets spawn={ground_spawn} updates={npc_updates}"
        )
    if xp_probe is not None:
        deadline = time.monotonic() + 6
        while len(xp_probe.updates) < before_xp + 3 and time.monotonic() < deadline:
            frame = next_frame(sock, s2c, 0.35)
            xp_probe.observe(frame)
        if len(xp_probe.updates) != before_xp + 3:
            raise ReconnectFailure(
                f"R8 expected 3 skill134 XP updates, before={before_xp} "
                f"after={len(xp_probe.updates)}"
            )
    # The gameplay code, not the fixture driver, must have advanced HP through
    # 25 -> 15 -> 5 -> 0 and removed the actor before creating ground loot.
    wait_log_contains(
        logfile,
        (
            f"R6_MORTAL_NPC_HIT scene={scene} hpBefore=25 damage=10 hpAfter=15",
            f"R6_MORTAL_NPC_HIT scene={scene} hpBefore=15 damage=10 hpAfter=5",
            f"R6_MORTAL_NPC_DEATH scene={scene} def=1 "
            "hpBefore=5 damage=10 hpAfter=0 dropItem=995 amount=1",
            "authority=CUSTOM_LOCALLAB_OPT_IN_FIXTURE",
        ), seconds=8,
    )
    return ground_spawn


def walk_and_take(sock, c2s, s2c, logfile, item_tile, qty_after, spawn):
    x, y = item_tile
    sock.sendall(walk164(c2s, x, y))
    # Movement request is authoritative; no inventory mutation before Take.
    deadline = time.monotonic() + 12
    while time.monotonic() < deadline:
        recent = log_tail(logfile, 100000)
        if (f"M5_AUTHORITATIVE_TICK mode=WALK tiles=1 from={x-1},{y}" in recent
                or f"M5_AUTHORITATIVE_TICK mode=RUN tiles=1 from={x-1},{y}" in recent):
            break
        next_frame(sock, s2c, 0.15)
    # No need to infer the exact movement finish from a delayed client graphic:
    # server Take is accepted only on the exact tile.
    sock.sendall(take_packet(c2s, COIN, x, y))
    removed = require_scene_step(sock, s2c, (COIN, qty_after), 156, seconds=12)
    if removed != (COIN, spawn[2]):
        raise ReconnectFailure(
            f"R7 Take scene-coordinate mismatch spawn={spawn} removed={removed}"
        )
    wait_log_contains(logfile, ("V511_GROUND_TAKE", "result=TAKE_ON_TILE_IMMEDIATE"))
    return removed


def await_new_generation(sock, s2c, logfile, old_scene):
    pattern = re.compile(
        r"R7_MORTAL_NPC_RESPAWN scene=(\d+) previousScene=(\d+) "
        r"tick=(\d+) dueTick=(\d+) generation=2 "
        r"R6_MORTAL_NPC_BOOTSTRAP scene=(\d+) def=1 "
        r"world=(\d+),(\d+) hp=25"
    )
    deadline = time.monotonic() + 15
    npc65 = 0
    while time.monotonic() < deadline:
        frame = next_frame(sock, s2c)
        if frame is not None and frame[0] == 65:
            npc65 += 1
        found = pattern.search(log_tail(logfile, 120000))
        if found:
            scene, prev, tick, due, repeated, x, y = map(int, found.groups())
            if (prev != old_scene or scene >= old_scene or scene != repeated
                    or tick < due):
                raise ReconnectFailure("R7 respawn used stale scene or early tick")
            if npc65 < 1:
                # The scene packet may precede the log publication, but
                # the fixture must produce a real NPC65 addition.
                deadline2 = time.monotonic() + 2
                while npc65 < 1 and time.monotonic() < deadline2:
                    fr = next_frame(sock, s2c, 0.2)
                    if fr is not None and fr[0] == 65:
                        npc65 += 1
            if npc65 < 1:
                raise ReconnectFailure("new NPC generation had no S2C65")
            return scene, x, y, tick, due
    raise ReconnectFailure("no delayed, distinct-scene R7 respawn")


def require_deposit(sock, c2s, s2c):
    # C2S145 is the pinned Deposit-1 action, not Deposit-All.
    # Requiring both intermediate states catches dropped/duplicated actions
    # and independently verifies the full two-loot bank delta.
    for deposited in (1, 2):
        sock.sendall(bank_action_145(c2s, 5064, 0, COIN))
        widgets = {}
        deadline = time.monotonic() + 12
        while time.monotonic() < deadline:
            frame = next_frame(sock, s2c)
            if frame is None:
                continue
            if frame[0] == 53:
                widget, slots = decode_container53(frame[1])
                if widget in (5382, 5064):
                    widgets[widget] = slots
            if len(widgets) == 2:
                remaining = 2 - deposited
                require_bank_pair(
                    widgets, BASE_BANK + deposited,
                    (COIN, remaining) if remaining else (-1, 0),
                )
                break
        else:
            raise ReconnectFailure(
                f"R7 Deposit-1 step {deposited} missing exact S2C53 pair"
            )


def run(jar: Path, java: str, xp: bool = False):
    jar = jar.expanduser().resolve(strict=True)
    if ports_in_use():
        raise ReconnectFailure("existing loopback server; no external process touched")
    with tempfile.TemporaryDirectory(prefix="spk-r7-multihit-respawn-") as temp:
        root = Path(temp)
        account = root / "accounts" / "opensrc.properties"
        phases = []
        xp_phases = []
        for phase in (1, 2):
            process, logfile = start_server(
                java, jar, root, account, f"r7-{phase}",
                fixture_options=OPTIONS + (R8_OPTION,) if xp else OPTIONS,
            )
            try:
                with socket.create_connection((HOST, PORT), timeout=5) as sock:
                    sock.settimeout(7)
                    xp_probe = AttackXpProbe() if xp else None
                    c2s, s2c, first_inv = read_bootstrap_and_inventory(
                        sock, on_frame=xp_probe.observe if xp_probe else None
                    )
                    if xp:
                        initial_xp = DEFAULT_ATTACK_XP if phase == 1 else DEFAULT_ATTACK_XP + 60
                        if xp_probe.updates != [initial_xp]:
                            raise ReconnectFailure(
                                f"R8 bootstrap S2C134 {xp_probe.updates}, expected {[initial_xp]}"
                            )
                        xp_phases.append({"jvm": phase, "bootstrap_attack_xp": initial_xp})
                    if first_inv[0] != (-1, 0):
                        raise ReconnectFailure("expected empty saved inventory")
                    if phase == 1:
                        old_scene, x1, y1 = find_initial_actor(logfile)
                        first_spawn = await_kill(
                            sock, c2s, s2c, logfile, old_scene, xp_probe=xp_probe,
                        )
                        if xp:
                            xp_probe.require_progression(1, (1, 2, 3))
                        walk_and_take(
                            sock, c2s, s2c, logfile,
                            (x1, y1), 1, first_spawn,
                        )
                        new_scene, x2, y2, tick, due = await_new_generation(
                            sock, s2c, logfile, old_scene,
                        )
                        # Old scene is now a retired entity: a delayed
                        # interaction must not target the respawn. Send it
                        # before the valid new-generation attack.
                        sock.sendall(attack_npc72(c2s, old_scene))
                        wait_log_contains(
                            logfile, (f"scene={old_scene}",), seconds=2,
                        )
                        second_spawn = await_kill(
                            sock, c2s, s2c, logfile, new_scene, xp_probe=xp_probe,
                        )
                        if xp:
                            xp_probe.require_progression(4, (4, 5, 6))
                        walk_and_take(
                            sock, c2s, s2c, logfile,
                            (x2, y2), 2, second_spawn,
                        )
                        if log_tail(logfile, 120000).count(
                            "R6_MORTAL_NPC_DEATH scene="
                        ) != 2:
                            raise ReconnectFailure(
                                "duplicate death or missing death generation"
                            )
                        open_bank(sock, c2s, s2c, BASE_BANK, (COIN, 2))
                        require_deposit(sock, c2s, s2c)
                        phases.append({
                            "jvm": 1, "hp_states": [25, 15, 5, 0],
                            "deaths": 2,
                            "scene_generations": [old_scene, new_scene],
                            "respawn_tick": tick, "scheduled_tick": due,
                        })
                    else:
                        open_bank(sock, c2s, s2c, FINAL_BANK, (-1, 0))
                        phases.append({"jvm": 2, "bank_restored": FINAL_BANK})
                wait_session_final(logfile, account)
                require_snapshot(account, FINAL_BANK, 0)
                if xp:
                    require_persisted_xp(account, DEFAULT_ATTACK_XP + 60)
                    xp_phases[-1]["persisted_attack_xp"] = DEFAULT_ATTACK_XP + 60
                    if phase == 1:
                        text_log = log_tail(logfile, 150000)
                        if text_log.count("R8_PVM_XP_GRANTED targetScene=") != 6:
                            raise ReconnectFailure("R8 must have exactly 6 awarded combat hits")
                        if "authority=CUSTOM_LOCALLAB_XP_PER_PUBLISHED_DAMAGE_V1" not in text_log:
                            raise ReconnectFailure("R8 custom XP authority not logged")
                else:
                    # The original R7 fixture must not acquire XP when the
                    # independent R8 opt-in flag is absent.
                    require_persisted_xp(account, DEFAULT_ATTACK_XP)
                if phase == 2:
                    wait_log_contains(logfile, ("ACCOUNT_LOADED profile=opensrc",))
            finally:
                stop_own_server(process, logfile)
        return {
            "result": ("R8_CUSTOM_PVM_XP_TWO_LOOT_RECONNECT_PASS" if xp
                       else "R7_CUSTOM_MULTIHIT_RESPAWN_TWO_LOOT_RECONNECT_PASS"),
            "custom_only": True,
            "nonlethal_hp_25_to_15_to_5": True,
            "lethal_hp_5_to_0": True,
            "delayed_new_scene_generation": True,
            "npc_generated_ground_loot_count": 2,
            "bank_coin_gain": 2,
            "bank_coins_after_reload": FINAL_BANK,
            "separate_jvm_count": 2,
            "real_gui_tested": False,
            "original_spawnpk_combat_tested": False,
            "crash_durability_tested": False,
            "windows_save_tested": False,
            "phases": phases,
            "xp_progression": xp_phases if xp else None,
            "custom_attack_xp_total_gain": 60 if xp else None,
            "original_spawnpk_xp_rate_tested": False,
            "xp_current_level_unchanged": bool(xp),
        }


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", type=Path,
        default=Path(__file__).resolve().parents[2] /
                "server" / "build" / "SpawnPKLocalServer.jar")
    parser.add_argument("--java", default="java")
    args = parser.parse_args(argv)
    try:
        print(json.dumps(run(args.jar, args.java), indent=2, sort_keys=True))
        return 0
    except (ReconnectFailure, ValueError, OSError, AssertionError) as exc:
        print(json.dumps({"result": "R7_CUSTOM_MULTIHIT_RESPAWN_FAIL",
                          "error": str(exc)}, indent=2), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
