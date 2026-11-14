#!/usr/bin/env python3
"""R46 opt-in custom Magic XP per confirmed Air Surge hit, two actual Main JVMs."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys

from synthetic_persistence_reconnect import ReconnectFailure
from synthetic_v308_custom_mortal_magic import run,MAGIC_XP_INITIAL


def main(argv=None)->int:
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--jar",type=Path,default=Path(__file__).resolve().parents[2]
                   /"server"/"build"/"SpawnPKLocalServer.jar")
    p.add_argument("--java",default="java")
    args=p.parse_args(argv)
    try:
        result=run(args.jar,args.java,magic_xp=True)
        if (result["result"]!="R46_REAL_JVM_CUSTOM_AIR_SURGE_MAGIC_XP_RECONNECT_PASS"
                or result["custom_magic_xp_gain"]!=10
                or result["magic_xp_restored"]!=MAGIC_XP_INITIAL+10
                or len(result["phases"])!=2
                or any(p["magic_xp"]!=MAGIC_XP_INITIAL+10
                       for p in result["phases"])):
            raise ReconnectFailure("R46 Magic XP full-journal receipt incomplete")
        print(json.dumps(result,indent=2,sort_keys=True))
        return 0
    except (ReconnectFailure,OSError,ValueError,AssertionError) as exc:
        print(json.dumps({
            "result":"R46_CUSTOM_MAGIC_XP_PVM_RECONNECT_FAIL",
            "error":str(exc)
        },indent=2,sort_keys=True),file=sys.stderr)
        return 1


if __name__=="__main__":
    raise SystemExit(main())
