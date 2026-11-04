#!/usr/bin/env python3
"""R8: real-JVM PvM per-hit XP S2C134 → saved account → fresh JVM bootstrap."""
from __future__ import annotations
import argparse
import json
from pathlib import Path
import sys

from synthetic_mortal_respawn import run as run_r7
from synthetic_persistence_reconnect import ReconnectFailure


def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar",type=Path,
        default=Path(__file__).resolve().parents[2]/
            "server"/"build"/"SpawnPKLocalServer.jar")
    parser.add_argument("--java",default="java")
    args=parser.parse_args(argv)
    try:
        receipt=run_r7(args.jar,args.java,xp=True)
        if receipt["result"]!="R8_CUSTOM_PVM_XP_TWO_LOOT_RECONNECT_PASS":
            raise ReconnectFailure("R8 conditional complete run did not pass")
        if receipt["custom_attack_xp_total_gain"]!=60 or (
            len(receipt["xp_progression"])!=2
        ):
            raise ReconnectFailure("R8 XP receipt incomplete")
        print(json.dumps(receipt,indent=2,sort_keys=True))
        return 0
    except (ReconnectFailure,OSError,ValueError,AssertionError) as exc:
        print(json.dumps({"result":"R8_CUSTOM_PVM_XP_RECONNECT_FAIL",
                          "error":str(exc)},indent=2),file=sys.stderr)
        return 1


if __name__=="__main__":
    raise SystemExit(main())
