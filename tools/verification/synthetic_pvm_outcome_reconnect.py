#!/usr/bin/env python3
"""R9: two custom PvM deaths -> typed World NPC_KILL events, loot and XP.

Strictly custom LocalLab; actual client protocol and JVM/process persistence
verified by the shared R7/R8 live-gameplay runner. No original SpawnPK
NPC kill-count, Collection Log, reward policy or graphical client is claimed.
"""
from __future__ import annotations
import argparse
import json
from pathlib import Path
import sys

from synthetic_mortal_respawn import run as run_journey
from synthetic_persistence_reconnect import ReconnectFailure


def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--jar",type=Path,
        default=Path(__file__).resolve().parents[2]/
        "server"/"build"/"SpawnPKLocalServer.jar",
    )
    parser.add_argument("--java",default="java")
    args=parser.parse_args(argv)
    try:
        receipt=run_journey(args.jar,args.java,xp=True,outcomes=True)
        outcomes=receipt.get("typed_npc_kill_world_events")
        if (not outcomes or len(outcomes)!=1 or
                outcomes[0]["npc_kill_count"]!=2 or
                outcomes[0]["verified_scenes"]!=[16382,16381]):
            raise ReconnectFailure("R9 expected two distinct NPC_KILL domain facts")
        if receipt["custom_attack_xp_total_gain"]!=60 or (
            receipt["bank_coins_after_reload"]!=100002
        ):
            raise ReconnectFailure("R9 XP, loot or fresh-JVM state regressed")
        receipt["result"]="R9_CUSTOM_PVM_TYPED_NPC_KILL_OUTCOME_RECONNECT_PASS"
        print(json.dumps(receipt,sort_keys=True,indent=2))
        return 0
    except (ReconnectFailure,OSError,ValueError,AssertionError) as error:
        print(json.dumps({
            "result":"R9_CUSTOM_PVM_TYPED_NPC_KILL_OUTCOME_FAIL",
            "error":str(error),
        },indent=2),file=sys.stderr)
        return 1


if __name__=="__main__":
    raise SystemExit(main())
