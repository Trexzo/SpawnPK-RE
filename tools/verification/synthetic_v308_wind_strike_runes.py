#!/usr/bin/env python3
"""R47: real Java11 v308 Wind strike exact air+mind rune spend and reconnect.

Only the explicit CUSTOM_LOCALLAB mortal practice NPC and synthetic v308
client traffic are exercised. Never starts a graphical client or live RSPS.
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

from synthetic_revision317_login import HOST,PORT,bank_action_145
from synthetic_s2c_bank import decode_container53
from synthetic_ground_take_reconnect import (
    decode_spawn44,decode_remove156,take_packet,wait_log_contains,
)
from synthetic_persistence_reconnect import (
    ReconnectFailure,ports_in_use,start_server,stop_own_server,
    read_bootstrap_and_inventory,wait_session_final,log_tail,next_frame,
    OVERLAY,
)
from synthetic_mortal_pvm_loot import discover_fixture,go_to_loot_tile
from synthetic_v308_custom_mortal_magic import npc_spell131

FLAGS=(
    "-Dspk.local.r6MortalFixture=true",
    "-Dspk.local.r45MortalMagic=true",
    "-Dspk.local.r47WindStrikeRunes=true",
)
AIR=556
MIND=558
COIN=995
EXPECTED_BANK=100001
FIXTURE_X,FIXTURE_Y=3088,3495


def command103(cipher,command:str)->bytes:
    raw=(command+"\n").encode("iso-8859-1")
    if len(raw)>255 or "\n" in command:
        raise ValueError("R47 invalid command")
    return bytes(((103+cipher.next_int())&255,len(raw)))+raw


def require_inventory(slots,air,mind,coins):
    if len(slots)!=28:
        raise ReconnectFailure("R47 normal inventory slots not 28")
    desired=((AIR,air) if air else (-1,0),
             (MIND,mind) if mind else (-1,0),
             (COIN,coins) if coins else (-1,0))
    if tuple(slots[:3])!=desired or any(x!=(-1,0) for x in slots[3:]):
        raise ReconnectFailure(
            f"R47 inventory unexpected first3={slots[:3]}, expected={desired}")


def await_inventory(sock,s2c,air,mind,coins):
    deadline=time.monotonic()+12
    while time.monotonic()<deadline:
        frame=next_frame(sock,s2c)
        if frame is None:
            continue
        if frame[0]==53:
            widget,slots=decode_container53(frame[1])
            if widget==3214:
                if tuple(slots[:3])==(
                        (AIR,air) if air else (-1,0),
                        (MIND,mind) if mind else (-1,0),
                        (COIN,coins) if coins else (-1,0)):
                    require_inventory(slots,air,mind,coins)
                    return
    raise ReconnectFailure(
        f"R47 missing native inventory53 air={air} mind={mind} coins={coins}")


def assert_account(account:Path,bank_coins:int,air:int,mind:int,coins:int):
    if not account.is_file() or account.stat().st_size>120000:
        raise ReconnectFailure("R47 missing or oversized private snapshot")
    props={}
    for line in account.read_text(encoding="iso-8859-1").splitlines():
        if "=" not in line or line.startswith("#") or line.startswith("!"):
            continue
        key,value=line.split("=",1)
        if key in ("inventory.0","inventory.1","inventory.2","bank.0"):
            props.setdefault(key,[]).append(value.strip())
    want={
        "inventory.0":[f"{AIR},{air},0" if air else ""],
        "inventory.1":[f"{MIND},{mind},0" if mind else ""],
        "inventory.2":[f"{COIN},{coins},0" if coins else ""],
        "bank.0":[f"{COIN},{bank_coins},0"],
    }
    if props!=want:
        raise ReconnectFailure(
            f"R47 persisted rune/bank slots mismatch {props} expected={want}")
    return hashlib.sha256(account.read_bytes()).hexdigest()


def await_coin_take(sock,s2c,air,mind):
    seen_inventory=False
    removed=None
    deadline=time.monotonic()+12
    while time.monotonic()<deadline:
        frame=next_frame(sock,s2c)
        if frame is None:
            continue
        op,payload=frame
        if op==53:
            widget,slots=decode_container53(payload)
            if widget==3214:
                require_inventory(slots,air,mind,1)
                seen_inventory=True
        elif op==156:
            if removed is not None:
                raise ReconnectFailure("R47 duplicate ground removal")
            removed=decode_remove156(payload)
        elif op==44:
            raise ReconnectFailure("R47 earned ground drop duplicated on Take")
        if seen_inventory and removed is not None:
            return removed
    raise ReconnectFailure(
        "R47 earned loot Take missing S2C156 and/or inventory53 "
        f"seenInventory={seen_inventory} removed={removed}")


def open_real_bank(sock,c2s,s2c,expected_bank,air,mind,coins):
    walk=bytes(((3089+128)&255,3089>>8,3495&255,3495>>8,0))
    obj=bytes(((3090+128)&255,3090>>8,26972>>8,26972&255,
               3495>>8,(3495+128)&255))
    sock.sendall(
        bytes(((164+c2s.next_int())&255,len(walk)))+walk+
        bytes(((132+c2s.next_int())&255,))+obj
    )
    overlay=False
    received={}
    deadline=time.monotonic()+15
    while time.monotonic()<deadline:
        frame=next_frame(sock,s2c)
        if frame is None:
            continue
        if frame==(248,OVERLAY):
            overlay=True
            received.clear()
        if overlay and frame[0]==53:
            widget,slots=decode_container53(frame[1])
            if widget in (5382,5064):
                received[widget]=slots
        if len(received)==2:
            bank=received[5382]
            inv=received[5064]
            if bank[0]!=(COIN,expected_bank):
                raise ReconnectFailure(
                    f"R47 bank currency mismatch {bank[0]} vs {expected_bank}")
            require_inventory(inv,air,mind,coins)
            return
    raise ReconnectFailure("R47 missing native bank overlay and containers")


def deposit_single_coin(sock,c2s,s2c,air,mind):
    sock.sendall(bank_action_145(c2s,5064,2,COIN))
    got={}
    deadline=time.monotonic()+12
    while time.monotonic()<deadline:
        frame=next_frame(sock,s2c)
        if frame is not None and frame[0]==53:
            widget,slots=decode_container53(frame[1])
            if widget in (5382,5064):
                got[widget]=slots
        if len(got)==2:
            if got[5382][0]!=(COIN,EXPECTED_BANK):
                raise ReconnectFailure("R47 deposited coin not in bank")
            require_inventory(got[5064],air,mind,0)
            return
    raise ReconnectFailure("R47 native bank deposit no completed packet53")


def journey(jar:Path,java:str)->dict:
    jar=jar.expanduser().resolve(strict=True)
    if not jar.is_file():
        raise ReconnectFailure("R47 exact server JAR absent")
    if ports_in_use():
        raise ReconnectFailure("R47 occupied localhost ports; refuse existing service")
    with tempfile.TemporaryDirectory(prefix="spk-r47-rune-cost-") as tmp:
        root=Path(tmp)
        account=root/"only-test-account"/"opensrc.properties"
        phases=[]
        for phase in (1,2):
            proc,logfile=start_server(java,jar,root,account,
                                      f"r47-main-{phase}",
                                      fixture_options=FLAGS)
            try:
                with socket.create_connection((HOST,PORT),timeout=6) as sock:
                    sock.settimeout(8)
                    c2s,s2c,inventory=read_bootstrap_and_inventory(sock)
                    require_inventory(inventory,
                                      0 if phase==1 else 1,
                                      0 if phase==1 else 1,0)
                    if phase==1:
                        scene=discover_fixture(logfile)
                        sock.sendall(command103(c2s,"item 556 2"))
                        await_inventory(sock,s2c,2,0,0)

                        # Explicitly visible client Wind Strike requirement
                        # denies a missing mind rune, with no NPC/rune mutation.
                        sock.sendall(npc_spell131(c2s,scene,1152))
                        wait_log_contains(logfile,(
                            "spell=Wind strike",
                            "REJECTED_VISIBLE_RESOURCE_REQUIREMENT",
                            "inventoryAlternatives=[558]",
                        ),seconds=8)
                        if "R47_CUSTOM_MORTAL_WIND_STRIKE_HIT" in log_tail(logfile,150000):
                            raise ReconnectFailure("R47 no-cost cast was accepted")

                        sock.sendall(command103(c2s,"item 558 2"))
                        await_inventory(sock,s2c,2,2,0)
                        sock.sendall(npc_spell131(c2s,scene,1152))

                        seen_inventory=False
                        seen_hit=False
                        spawn=None
                        deadline=time.monotonic()+15
                        while time.monotonic()<deadline:
                            frame=next_frame(sock,s2c)
                            if frame is None:
                                continue
                            opcode,payload=frame
                            if opcode==53:
                                widget,slots=decode_container53(payload)
                                if widget==3214:
                                    require_inventory(slots,1,1,0)
                                    seen_inventory=True
                            elif opcode==65:
                                seen_hit=True
                            elif opcode==44:
                                if spawn is not None:
                                    raise ReconnectFailure("R47 duplicate rune spell drop")
                                spawn=decode_spawn44(payload)
                                if spawn[:2]!=(COIN,1):
                                    raise ReconnectFailure("R47 wrong earned coin drop")
                            if seen_inventory and seen_hit and spawn is not None:
                                break
                        if not seen_inventory or not seen_hit or spawn is None:
                            raise ReconnectFailure(
                                "R47 failed to emit inventory53 + NPC65 + loot44 "
                                f"inventory={seen_inventory} hit={seen_hit} spawn={spawn} "
                                f"log={log_tail(logfile,3500)}")
                        wait_log_contains(logfile,(
                            "R47_CUSTOM_MORTAL_WIND_STRIKE_HIT",
                            "runeCost=556x1,558x1",
                            "authority=CUSTOM_LOCALLAB_WIND_STRIKE_EXACT_RUNE_COST_V1",
                            f"R6_MORTAL_NPC_DEATH scene={scene}",
                            "R9_PVM_KILL_OUTCOME_EMITTED",
                        ),seconds=8)
                        # A stale second spell must not charge the remaining
                        # rune stacks or issue duplicate kill/loot.
                        sock.sendall(npc_spell131(c2s,scene,1152))
                        wait_log_contains(logfile,(
                            "REJECTED_TARGET_NPC_NOT_VISIBLE",
                        ),seconds=8)
                        if log_tail(logfile,150000).count(
                                f"R6_MORTAL_NPC_DEATH scene={scene}")!=1:
                            raise ReconnectFailure("R47 duplicate earned NPC death")
                        go_to_loot_tile(sock,c2s,s2c,logfile)
                        sock.sendall(take_packet(c2s,COIN,FIXTURE_X,FIXTURE_Y))
                        removed=await_coin_take(sock,s2c,1,1)
                        if removed!=(COIN,spawn[2]):
                            raise ReconnectFailure("R47 loot Take did not match drop")
                        open_real_bank(sock,c2s,s2c,100000,1,1,1)
                        deposit_single_coin(sock,c2s,s2c,1,1)
                    else:
                        open_real_bank(sock,c2s,s2c,EXPECTED_BANK,1,1,0)

                wait_session_final(logfile,account)
                digest=assert_account(account,EXPECTED_BANK,1,1,0)
                if phase==2:
                    wait_log_contains(logfile,("ACCOUNT_LOADED profile=opensrc",))
                phases.append({
                    "fresh_Main_JVM":phase,
                    "bank_coins":EXPECTED_BANK,
                    "air_runes_remaining":1,
                    "mind_runes_remaining":1,
                    "profile_sha256":digest,
                })
            finally:
                stop_own_server(proc,logfile)
        return {
            "result":"R47_REAL_JVM_CUSTOM_WIND_STRIKE_RUNES_LOOT_BANK_RECONNECT_PASS",
            "distinct_Main_JVMs":2,
            "visible_resource_requirement_missing_mind_denied":True,
            "exact_rune_cost_556x1_and_558x1":True,
            "npc65_and_earned_loot44":True,
            "stale_second_cast_no_rune_cost_or_double_drop":True,
            "native_inventory53_on_rune_spend":True,
            "earned_loot_take156_bank53":True,
            "persistent_runes_and_bank_restored":True,
            "original_spawnpk_damage_or_rune_rules_verified":False,
            "graphical_original_v308_launched":False,
            "phases":phases,
        }


def main(argv=None)->int:
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--jar",type=Path,default=Path(__file__).resolve().parents[2]
                   /"server"/"build"/"SpawnPKLocalServer.jar")
    p.add_argument("--java",default="java")
    args=p.parse_args(argv)
    try:
        print(json.dumps(journey(args.jar,args.java),indent=2,sort_keys=True))
        return 0
    except (ReconnectFailure,OSError,ValueError,AssertionError) as exc:
        print(json.dumps({
            "result":"R47_REAL_JVM_CUSTOM_WIND_STRIKE_RUNES_FAILED",
            "error":str(exc)
        },indent=2,sort_keys=True),file=sys.stderr)
        return 1


if __name__=="__main__":
    raise SystemExit(main())
