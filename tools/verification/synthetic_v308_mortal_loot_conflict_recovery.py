#!/usr/bin/env python3
"""R48: real Main JVM Wind Strike/Air Surge blocked by an already-owned loot tile.

This is a disposable CUSTOM_LOCALLAB / synthetic v308-shaped TCP regression.
No graphical client, live account, owner JVM, firewall or Windows feature touched.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import socket
import sys
import tempfile
import time

from synthetic_revision317_login import HOST,PORT,bank_action_145
from synthetic_s2c_bank import decode_container53
from synthetic_persistence_reconnect import (
    ReconnectFailure,ports_in_use,start_server,stop_own_server,
    read_bootstrap_and_inventory,next_frame,wait_session_final,log_tail,
)
from synthetic_ground_take_reconnect import (
    drop_packet,take_packet,decode_spawn44,decode_remove156,wait_log_contains,
)
from synthetic_mortal_pvm_loot import discover_fixture,walk164
from synthetic_v308_custom_mortal_magic import (
    npc_spell131,MAGIC_XP_INITIAL,require_magic_xp_snapshot,
)
from synthetic_v308_wind_strike_runes import (
    command103,await_inventory,require_inventory,
    open_real_bank,assert_account,
)

FLAGS=(
    "-Dspk.local.r6MortalFixture=true",
    "-Dspk.local.r45MortalMagic=true",
    "-Dspk.local.r46MortalMagicXp=true",
    "-Dspk.local.r47WindStrikeRunes=true",
)
AIR,MIND,COIN=556,558,995
FIXTURE_X,FIXTURE_Y=3088,3495
FINAL_BANK=100002


def await_drop(sock,s2c,air:int,mind:int,coins:int)->tuple:
    """Pair an actual owner ground spawn44 with its inventory53 mutation."""
    inv=False
    ground=None
    deadline=time.monotonic()+12
    while time.monotonic()<deadline:
        frame=next_frame(sock,s2c)
        if frame is None:
            continue
        op,payload=frame
        if op==53:
            widget,slots=decode_container53(payload)
            if widget==3214:
                require_inventory(slots,air,mind,coins)
                inv=True
        elif op==44:
            if ground is not None:
                raise ReconnectFailure("R48 duplicate initial ground item")
            ground=decode_spawn44(payload)
            if ground[:2]!=(COIN,1):
                raise ReconnectFailure("R48 wrong initial owned ground item")
        if inv and ground is not None:
            return ground
    raise ReconnectFailure("R48 initial owner coin Drop lacks inventory53/ground44")


def await_take(sock,s2c,air:int,mind:int,coins:int)->tuple:
    """S2C156 ground removal and exact inventory slot2 quantity."""
    removed=None
    inv=False
    deadline=time.monotonic()+12
    while time.monotonic()<deadline:
        frame=next_frame(sock,s2c)
        if frame is None:
            continue
        op,payload=frame
        if op==53:
            widget,slots=decode_container53(payload)
            if widget==3214:
                require_inventory(slots,air,mind,coins)
                inv=True
        elif op==156:
            if removed is not None:
                raise ReconnectFailure("R48 duplicate owned ground removal")
            removed=decode_remove156(payload)
        elif op==44:
            raise ReconnectFailure("R48 unexpected ground spawn during Take")
        if inv and removed is not None:
            return removed
    raise ReconnectFailure(
        f"R48 ground Take missing inv53 or remove156 inv={inv} removed={removed}"
    )


def no_mutation_on_blocked_spells(sock,s2c,logfile,scene):
    """Reject both actual C2S131 spells before any rune, XP or NPC damage."""
     # The caller sends both packets. Check stable log and the wire, including
    # unsolicited periodic World pulses, without confusing normal NPC65 with
    # a published damaging hit.
    wait_log_contains(logfile,("R48_REJECTED_OWNER_LOOT_TILE_OCCUPIED",),seconds=8)
    tail=log_tail(logfile,150000)
    if tail.count("R48_REJECTED_OWNER_LOOT_TILE_OCCUPIED")!=2:
        raise ReconnectFailure("R48 both distinct spell denials not observed")
    for unexpected in (
        "R47_CUSTOM_MORTAL_WIND_STRIKE_HIT",
        "R45_CUSTOM_MORTAL_AIR_SURGE_HIT",
        "R6_MORTAL_NPC_DEATH",
        "R46_MAGIC_XP_GRANTED",
        "R9_PVM_KILL_OUTCOME_EMITTED",
    ):
        if unexpected in tail:
            raise ReconnectFailure(
                "R48 blocked spell caused forbidden mutation: "+unexpected
            )
    deadline=time.monotonic()+0.9
    while time.monotonic()<deadline:
        frame=next_frame(sock,s2c,0.15)
        if frame is None:
            continue
        op,payload=frame
        if op==53:
            widget,slots=decode_container53(payload)
            if widget==3214:
                require_inventory(slots,2,2,0)
        if op in (44,156):
            raise ReconnectFailure("R48 blocked spell emitted ground mutation")


def await_real_wind_hit(sock,s2c,logfile,scene:int):
    inv=False
    mask=False
    drop=None
    deadline=time.monotonic()+15
    while time.monotonic()<deadline:
        frame=next_frame(sock,s2c)
        if frame is None:
            continue
        op,payload=frame
        if op==53:
            widget,slots=decode_container53(payload)
            if widget==3214:
                require_inventory(slots,1,1,1)
                inv=True
        elif op==65:
            mask=True
        elif op==44:
            if drop is not None:
                raise ReconnectFailure("R48 repeated earned ground loot")
            drop=decode_spawn44(payload)
            if drop[:2]!=(COIN,1):
                raise ReconnectFailure("R48 unexpected reward ground item")
        if inv and mask and drop is not None:
            break
    if not inv or not mask or drop is None:
        raise ReconnectFailure(
            f"R48 recovery Wind Strike missing inventory53/NPC65/ground44 "
            f"inv={inv} npc={mask} drop={drop} tail={log_tail(logfile,2600)}"
        )
    wait_log_contains(logfile,(
        "R47_CUSTOM_MORTAL_WIND_STRIKE_HIT",
        "runeCost=556x1,558x1",
        f"R6_MORTAL_NPC_DEATH scene={scene}",
        "R9_PVM_KILL_OUTCOME_EMITTED",
    ),seconds=8)
    if log_tail(logfile,150000).count(
            f"R6_MORTAL_NPC_DEATH scene={scene}")!=1:
        raise ReconnectFailure("R48 recovery emitted duplicate NPC death")
    return drop


def deposit_earned_coins(sock,c2s,s2c):
    sock.sendall(bank_action_145(c2s,5064,2,COIN))
    got={}
    deadline=time.monotonic()+12
    while time.monotonic()<deadline:
        frame=next_frame(sock,s2c)
        if frame is None or frame[0]!=53:
            continue
        widget,slots=decode_container53(frame[1])
        if widget in (5382,5064):
            got[widget]=slots
        if len(got)==2:
            if got[5382][0]!=(COIN,FINAL_BANK):
                raise ReconnectFailure("R48 bank did not receive two earned coins")
            require_inventory(got[5064],1,1,0)
            return
    raise ReconnectFailure("R48 missing bank53 after two-coin deposit")


def run(jar:Path,java:str)->dict:
    jar=jar.expanduser().resolve(strict=True)
    if not jar.is_file():
        raise ReconnectFailure("R48 exact server JAR missing")
    if ports_in_use():
        raise ReconnectFailure("R48 occupied localhost ports; existing process untouched")
    with tempfile.TemporaryDirectory(prefix="spk-r48-mortal-loot-conflict-") as name:
        root=Path(name)
        account=root/"only-test-account"/"opensrc.properties"
        phases=[]
        for phase in (1,2):
            proc,logfile=start_server(
                java,jar,root,account,f"r48-Main-{phase}",fixture_options=FLAGS)
            try:
                with socket.create_connection((HOST,PORT),timeout=6) as sock:
                    sock.settimeout(7)
                    c2s,s2c,initial=read_bootstrap_and_inventory(sock)
                    require_inventory(initial,
                                      0 if phase==1 else 1,
                                      0 if phase==1 else 1,0)
                    if phase==1:
                        scene=discover_fixture(logfile)
                        sock.sendall(command103(c2s,"item 556 2"))
                        await_inventory(sock,s2c,2,0,0)
                        sock.sendall(command103(c2s,"item 558 2"))
                        await_inventory(sock,s2c,2,2,0)
                        sock.sendall(command103(c2s,"item 995 1"))
                        await_inventory(sock,s2c,2,2,1)

                        # Move directly onto the living fixture's tile; a
                        # genuine native C2S87 will seed a pre-existing owner
                        # coin precisely where a later lethal reward would go.
                        sock.sendall(walk164(c2s,FIXTURE_X,FIXTURE_Y))
                        wait_log_contains(logfile,(
                            "M5_AUTHORITATIVE_TICK mode=WALK tiles=1",
                            "from=3087,3495 to=3088,3495",
                        ),seconds=9)
                        sock.sendall(drop_packet(c2s,COIN,3214,2))
                        blocker=await_drop(sock,s2c,2,2,0)
                        wait_log_contains(logfile,(
                            "V511_GROUND_DROP",
                            "result=DROP_OK amount=1",
                        ),seconds=8)

                        # Both spell routes must reject at the same World
                        # preflight, leaving 2+2 runes, 10 NPC HP, 0 Magic XP,
                        # and one original ground item unchanged.
                        sock.sendall(npc_spell131(c2s,scene,1152))
                        sock.sendall(npc_spell131(c2s,scene,19100))
                        no_mutation_on_blocked_spells(sock,s2c,logfile,scene)

                        # Removing the blocker must recover the *same* live
                        # NPC; only the first successfully published hit
                        # thereafter may spend runes and produce one death.
                        sock.sendall(take_packet(c2s,COIN,FIXTURE_X,FIXTURE_Y))
                        taken=await_take(sock,s2c,2,2,1)
                        if taken!=(COIN,blocker[2]):
                            raise ReconnectFailure("R48 owner blocking coin identity changed")
                        sock.sendall(npc_spell131(c2s,scene,1152))
                        reward=await_real_wind_hit(sock,s2c,logfile,scene)
                        sock.sendall(npc_spell131(c2s,scene,1152))
                        wait_log_contains(logfile,(
                            "REJECTED_TARGET_NPC_NOT_VISIBLE",),seconds=8)
                        sock.sendall(take_packet(c2s,COIN,FIXTURE_X,FIXTURE_Y))
                        taken=await_take(sock,s2c,1,1,2)
                        if taken!=(COIN,reward[2]):
                            raise ReconnectFailure("R48 earned reward identity changed")

                        open_real_bank(sock,c2s,s2c,100000,1,1,2)
                        deposit_earned_coins(sock,c2s,s2c)
                    else:
                        open_real_bank(sock,c2s,s2c,FINAL_BANK,1,1,0)
                wait_session_final(logfile,account)
                digest=assert_account(account,FINAL_BANK,1,1,0)
                require_magic_xp_snapshot(account,MAGIC_XP_INITIAL)
                if phase==2:
                    wait_log_contains(logfile,("ACCOUNT_LOADED profile=opensrc",))
                phases.append({"Main_JVM":phase,"bank":FINAL_BANK,
                               "air_runes":1,"mind_runes":1,
                               "magic_xp":MAGIC_XP_INITIAL,
                               "profile_sha256":digest})
            finally:
                stop_own_server(proc,logfile)
        return {
            "result":"R48_REAL_JVM_LOOT_CONFLICT_ZERO_RUNE_DEBIT_AND_RECOVERY_PASS",
            "distinct_real_Main_JVMs":2,
            "existing_owned_coin_blocks_both_magic_paths":True,
            "initial_rejected_casts_spent_zero_runes":True,
            "initial_rejected_casts_awarded_zero_magic_xp":True,
            "npc_health_unchanged_until_recovery":True,
            "blocker_take_and_real_hit_recovery":True,
            "single_canonical_kill_and_reward_after_recovery":True,
            "remaining_air_and_mind_each":1,
            "recovered_bank_coins":FINAL_BANK,
            "graphical_original_v308_launched":False,
            "crash_safe_atomicity_proven":False,
            "phases":phases,
        }


def main(argv=None)->int:
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--jar",type=Path,default=Path(__file__).resolve().parents[2]
                   /"server"/"build"/"SpawnPKLocalServer.jar")
    p.add_argument("--java",default="java")
    a=p.parse_args(argv)
    try:
        print(json.dumps(run(a.jar,a.java),indent=2,sort_keys=True))
        return 0
    except (ReconnectFailure,OSError,ValueError,AssertionError) as error:
        print(json.dumps({
            "result":"R48_LOOT_CONFLICT_PREDEBIT_FAILED","error":str(error)
        },indent=2,sort_keys=True),file=sys.stderr)
        return 1


if __name__=="__main__":
    raise SystemExit(main())
