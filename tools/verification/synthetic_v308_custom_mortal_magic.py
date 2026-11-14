#!/usr/bin/env python3
"""R45: real Java11 C2S131 Air surge kills ONLY opt-in mortal NPC.

The original graphical v308 client is NOT executed. This is an opt-in
CUSTOM_LOCALLAB damage rule, not a recovered original SpawnPK spell formula.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import socket
import sys
import tempfile
import time

from synthetic_revision317_login import HOST,PORT
from synthetic_s2c_bank import decode_container53
from synthetic_ground_take_reconnect import (
    decode_spawn44, require_scene_step, take_packet, wait_log_contains,
)
from synthetic_persistence_reconnect import (
    ReconnectFailure, ports_in_use, start_server, stop_own_server,
    read_bootstrap_and_inventory, require_snapshot, wait_session_final,
    open_bank, log_tail, next_frame,
)
from synthetic_mortal_pvm_loot import (
    discover_fixture, go_to_loot_tile, deposit_loot,
    ITEM,LOOT_X,LOOT_Y,START_BANK,FINAL_BANK,
)

FLAGS=(
    "-Dspk.local.r6MortalFixture=true",
    "-Dspk.local.r45MortalMagic=true",
)


def npc_spell131(cipher,scene:int,widget:int)->bytes:
    if not 0<=scene<16383 or not 0<=widget<=65535:
        raise ValueError("invalid scene or spell widget")
    return bytes(((131+cipher.next_int())&255,
                  (scene+128)&255,(scene>>8)&255,
                  (widget>>8)&255,(widget+128)&255))


def run(jar:Path,java:str)->dict:
    jar=jar.expanduser().resolve(strict=True)
    if not jar.is_file():
        raise ReconnectFailure("R45 exact server JAR missing")
    if ports_in_use():
        raise ReconnectFailure("R45 local ports occupied; refused before launch")
    with tempfile.TemporaryDirectory(prefix="spk-r45-custom-air-surge-") as temp:
        root=Path(temp)
        account=root/"only-test-account"/"opensrc.properties"
        phases=[]
        for phase in (1,2):
            child,logfile=start_server(java,jar,root,account,
                                       f"r45-real-main-{phase}",
                                       fixture_options=FLAGS)
            try:
                with socket.create_connection((HOST,PORT),timeout=6) as sock:
                    sock.settimeout(8)
                    c2s,s2c,inventory=read_bootstrap_and_inventory(sock)
                    if inventory[0] != (-1,0):
                        raise ReconnectFailure("R45 initial inventory not empty")
                    if phase==1:
                        scene=discover_fixture(logfile)

                        # Nonfixture HOME NPC retains unknown original authority
                        # despite R45 enabled. There must be no mortal mutation.
                        sock.sendall(npc_spell131(c2s,103,19100))
                        wait_log_contains(
                            logfile,
                            ("spell=Air surge",
                             "TARGET_NPC_VISIBLE def=7605 effect=UNIMPLEMENTED_SERVER_AUTHORITY"),
                            seconds=8,
                        )
                        if "R45_CUSTOM_MORTAL_AIR_SURGE_HIT" in log_tail(logfile,100000):
                            raise ReconnectFailure(
                                "R45 ordinary HOME NPC unexpectedly damaged"
                            )

                        # This rune-free exact v308 spell widget is the ONLY
                        # custom spell admitted on the opt-in practice NPC.
                        sock.sendall(npc_spell131(c2s,scene,19100))
                        spawn=None
                        observed_hit=False
                        deadline=time.monotonic()+16
                        while time.monotonic()<deadline:
                            frame=next_frame(sock,s2c)
                            if frame is None:
                                continue
                            op,body=frame
                            if op==65:
                                observed_hit=True
                            elif op==44:
                                if spawn is not None:
                                    raise ReconnectFailure("duplicate S2C44 magic loot")
                                spawn=decode_spawn44(body)
                                if spawn[:2]!=(ITEM,1):
                                    raise ReconnectFailure(
                                        f"wrong magic death ground drop {spawn}"
                                    )
                                break
                            elif op==53:
                                widget,slots=decode_container53(body)
                                if widget==3214 and slots[0]!=(-1,0):
                                    raise ReconnectFailure(
                                        "NPC loot appeared without explicit Take"
                                    )
                        if not observed_hit or spawn is None:
                            raise ReconnectFailure(
                                "R45 magic did not publish both NPC65 and loot44"
                            )
                        wait_log_contains(logfile,(
                            "R45_CUSTOM_MORTAL_AIR_SURGE_HIT",
                            "authority=CUSTOM_LOCALLAB_OPT_IN_AIR_SURGE_V1",
                            f"scene={scene} hpBefore=10 hpAfter=0 damage=10",
                            f"R6_MORTAL_NPC_DEATH scene={scene}",
                            "R9_PVM_KILL_OUTCOME_EMITTED",
                            "originalDamageFormula=false",
                        ),seconds=8)

                        # A stale follow-up target cannot produce a duplicate
                        # kill/drop. Verify World/scene guard on real packet path.
                        sock.sendall(npc_spell131(c2s,scene,19100))
                        wait_log_contains(logfile,(
                            "REJECTED_TARGET_NPC_NOT_VISIBLE",
                        ),seconds=8)
                        if log_tail(logfile,150000).count(
                            f"R6_MORTAL_NPC_DEATH scene={scene}")!=1:
                            raise ReconnectFailure("duplicate mortal NPC death")
                        go_to_loot_tile(sock,c2s,s2c,logfile)
                        sock.sendall(take_packet(c2s,ITEM,LOOT_X,LOOT_Y))
                        removed=require_scene_step(sock,s2c,(ITEM,1),156)
                        if removed!=(ITEM,spawn[2]):
                            raise ReconnectFailure("R45 Take did not match earned loot")
                        wait_log_contains(logfile,(
                            "V511_GROUND_TAKE","result=TAKE_ON_TILE_IMMEDIATE"),
                            seconds=8)
                        open_bank(sock,c2s,s2c,START_BANK,(ITEM,1))
                        deposit_loot(sock,c2s,s2c)
                    else:
                        open_bank(sock,c2s,s2c,FINAL_BANK,(-1,0))
                wait_session_final(logfile,account)
                saved=require_snapshot(account,FINAL_BANK,0)
                if phase==2:
                    wait_log_contains(logfile,("ACCOUNT_LOADED profile=opensrc",))
                phases.append({"jvm":phase,"bank_coins":FINAL_BANK,
                               "profile_sha256":saved})
            finally:
                stop_own_server(child,logfile)
        return {
            "result":"R45_REAL_JVM_CUSTOM_AIR_SURGE_PVM_LOOT_BANK_RECONNECT_PASS",
            "distinct_server_jvms":2,
            "client_style":"synthetic_v308_C2S131",
            "accepted_spell":"Air surge widget19100 rune-free",
            "ordinary_home_npc_remains_unknown":True,
            "server_owned_npc_hp_death":True,
            "single_canonical_npc_kill_outcome":True,
            "exact_npc65_and_loot44":True,
            "duplicate_stale_target_denied":True,
            "take156_bank53_saved_and_reloaded":True,
            "bank_coins_restored":FINAL_BANK,
            "original_spawnpk_damage_or_graphics_proven":False,
            "phases":phases,
        }


def main(argv=None)->int:
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--jar",type=Path,default=Path(__file__).resolve().parents[2]
                   /"server"/"build"/"SpawnPKLocalServer.jar")
    p.add_argument("--java",default="java")
    args=p.parse_args(argv)
    try:
        print(json.dumps(run(args.jar,args.java),indent=2,sort_keys=True))
        return 0
    except (ReconnectFailure,OSError,ValueError,AssertionError) as e:
        print(json.dumps({
            "result":"R45_CUSTOM_MORTAL_MAGIC_REAL_JVM_FAILED",
            "error":str(e)
        },indent=2,sort_keys=True),file=sys.stderr)
        return 1


if __name__=="__main__":
    raise SystemExit(main())
