#!/usr/bin/env python3
"""R51: genuine Main JVM restart refuses orphaned account stage, then resumes only after test-only review.

The stage is a fault-injected marker in a private temp account directory.
No user-owned account, Java client or Windows settings are accessed.
This is fail-closed recovery triage, NOT automatic replay/rollback or a hard-kill test.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import socket
import subprocess
import sys
import tempfile

from synthetic_revision317_login import HOST,PORT,Isaac,_begin,read_exact
from synthetic_persistence_reconnect import (
    ReconnectFailure, ports_in_use, start_server,stop_own_server,
    read_bootstrap_and_inventory,wait_session_final,open_bank,
    log_tail,
)
from synthetic_ground_take_reconnect import wait_log_contains


DENIAL="R34_ACCOUNT_LOAD_REJECTED action=NO_WORLD_NO_SAVE"


def sha(data:bytes)->str:
    return hashlib.sha256(data).hexdigest()


def verify_denied_login(sock:socket.socket,logfile:Path)->None:
    # Exact normal synthetic v308-shaped RSA/ISAAC handshake is still valid.
    # Profile load is performed after the first widget912 arrives.
    seeds=_begin(sock,revision=317,username="localtest")
    if read_exact(sock,3)!=bytes((2,205,0)):
        raise ReconnectFailure("R51 handshake rejected before account stage check")
    cipher=Isaac(seeds)
    sock.sendall(bytes(((185+cipher.next_int())&255,912>>8,912&255)))
    wait_log_contains(logfile,(DENIAL,),seconds=10)
    trace=log_tail(logfile,60000)
    if "V512_WORLD_REGISTER playerId=" in trace:
        raise ReconnectFailure("R51 quarantined user was registered in World")
    if "V5123_ACCOUNT ACCOUNT_LOADED" in trace or (
            "V5123_ACCOUNT ACCOUNT_DEFAULTS_NO_FILE" in trace):
        raise ReconnectFailure("R51 quarantined profile used stale/default snapshot")
    if "V5123_ACCOUNT_SAVE reason=" in trace or (
            "V5123_ACCOUNT_FINAL_SAVE" in trace):
        raise ReconnectFailure("R51 quarantined profile saved over canonical")


def run(jar:Path,java:str)->dict:
    jar=jar.expanduser().resolve(strict=True)
    if not jar.is_file():
        raise ReconnectFailure("R51 exact server jar missing")
    if ports_in_use():
        raise ReconnectFailure("R51 localhost service exists; refuse to start")
    with tempfile.TemporaryDirectory(prefix="spk-r51-ambiguous-stage-") as tmp:
        root=Path(tmp)
        account=root/"only-test-account"/"opensrc.properties"
        phases=[]

        # Phase 1: establish an authentic canonical account through the
        # existing full Main JVM + World + final persistence worker.
        proc,logfile=start_server(java,jar,root,account,"r51-baseline")
        try:
            with socket.create_connection((HOST,PORT),timeout=6) as sock:
                sock.settimeout(7)
                c2s,s2c,inventory=read_bootstrap_and_inventory(sock)
                if inventory[0]!=(-1,0):
                    raise ReconnectFailure("R51 initial inventory unexpectedly nonempty")
                open_bank(sock,c2s,s2c,100000,(-1,0))
            wait_session_final(logfile,account)
        finally:
            stop_own_server(proc,logfile)
        original=account.read_bytes()
        original_sha=sha(original)
        phases.append({"Main_JVM":1,"result":"CANONICAL_SNAPSHOT_SAVED",
                       "canonical_sha256":original_sha})

        # A distinct, explicitly private Java fault-injection child executes
        # the REAL atomic publisher. Its replacement callback is invoked only
        # after FileChannel.force(true), and abruptly halts THAT child before
        # atomic move/finally cleanup. Never halt an existing Main server.
        crash=subprocess.run(
            [java,"-Dspk.local.r51InterruptPublisherTestOnly=true",
             "-cp",str(jar),
             "spk.local.R51InterruptedAccountPublisherChild",str(account)],
            cwd=root,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,
            timeout=20,check=False,
        )
        trace=crash.stdout.decode("utf-8","replace")
        if crash.returncode!=71 or (
                "R51_TEST_ONLY_FORCED_STAGE_BEFORE_ATOMIC_REPLACE" not in trace):
            raise ReconnectFailure(
                "R51 forced-staging child did not halt at exact callback "+
                f"exit={crash.returncode} tail={trace[-1800:]}"
            )
        if sha(account.read_bytes())!=original_sha:
            raise ReconnectFailure(
                "R51 exact forced-stage halt changed canonical before move"
            )
        prefix="."+account.name+".r16-"
        matches=[p for p in account.parent.iterdir()
                 if p.name.startswith(prefix)
                 and p.name.endswith(".stage")
                 and len(p.name)>len(prefix)+len(".stage")]
        if len(matches)!=1:
            raise ReconnectFailure(
                f"R51 expected one genuine orphan stage, found {len(matches)}"
            )
        stage=matches[0]
        staged=stage.read_bytes() # Only independent test inspection.
        if b"r51.uncommitted.test.marker=UNPUBLISHED" not in staged or (
                b"bank.0=995,100099,0" not in staged):
            raise ReconnectFailure(
                "R51 forced stage did not contain expected uncommitted test data"
            )
        stage_sha=sha(staged)

        proc,logfile=start_server(java,jar,root,account,"r51-denied")
        try:
            with socket.create_connection((HOST,PORT),timeout=6) as sock:
                sock.settimeout(7)
                verify_denied_login(sock,logfile)
            if sha(account.read_bytes())!=original_sha:
                raise ReconnectFailure("R51 denied login modified canonical bytes")
            if sha(stage.read_bytes())!=stage_sha:
                raise ReconnectFailure("R51 denied login modified staging evidence")
        finally:
            stop_own_server(proc,logfile)
        phases.append({"Main_JVM":2,"result":"ACCOUNT_STAGE_QUARANTINE",
                       "world_registered":False,"saved_defaults":False,
                       "canonical_sha256":sha(account.read_bytes()),
                       "stage_sha256":sha(stage.read_bytes())})

        # Simulate review of a test-owned stage: preserve first, explicitly
        # discard ONLY the synthetic injected marker, not any operator file.
        if stage.read_bytes()!=staged or account.read_bytes()!=original:
            raise ReconnectFailure("R51 review found unexpected profile changes")
        stage.unlink()

        proc,logfile=start_server(java,jar,root,account,"r51-reviewed-resume")
        try:
            with socket.create_connection((HOST,PORT),timeout=6) as sock:
                sock.settimeout(7)
                c2s,s2c,inventory=read_bootstrap_and_inventory(sock)
                if inventory[0]!=(-1,0):
                    raise ReconnectFailure("R51 reviewed resume inventory drift")
                open_bank(sock,c2s,s2c,100000,(-1,0))
            wait_session_final(logfile,account)
            wait_log_contains(logfile,(
                "ACCOUNT_LOADED profile=opensrc",
            ),seconds=8)
        finally:
            stop_own_server(proc,logfile)
        if stage.exists():
            raise ReconnectFailure("R51 synthetic stage reappeared after normal save")
        phases.append({"Main_JVM":3,"result":"REVIEWED_BASELINE_RESUMED",
                       "bank_coins":100000})

        return {
            "result":"R51_REAL_JVM_AMBIGUOUS_STAGE_QUARANTINE_AND_REVIEWED_RESUME_PASS",
            "distinct_real_Main_JVMs":3,
            "orphan_marker_generated_in_disposable_test_directory":True,
            "true_child_jvm_halted_after_forced_account_stage":True,
            "test_child_halt_exit_code":71,
            "load_refused_before_world_registration":True,
            "canonical_and_stage_bytes_preserved_during_denial":True,
            "no_automatic_stage_replay_or_cleanup":True,
            "explicit_disposable_test_marker_removal_only":True,
            "bank_coins_after_reviewed_resume":100000,
            "power_loss_crash_atomicity_proven":False,
            "original_graphical_client_launched":False,
            "phases":phases,
        }


def main(argv=None)->int:
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar",type=Path,default=Path(__file__).resolve().parents[2]
                        /"server"/"build"/"SpawnPKLocalServer.jar")
    parser.add_argument("--java",default="java")
    args=parser.parse_args(argv)
    try:
        print(json.dumps(run(args.jar,args.java),indent=2,sort_keys=True))
        return 0
    except (ReconnectFailure,OSError,ValueError,AssertionError) as failure:
        print(json.dumps({"result":"R51_AMBIGUOUS_STAGE_REAL_JVM_FAILED",
                          "error":str(failure)},indent=2),file=sys.stderr)
        return 1


if __name__=="__main__":
    raise SystemExit(main())
