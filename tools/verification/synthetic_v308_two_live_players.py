#!/usr/bin/env python3
"""R43: two live v308-shaped LocalLab TCP clients, remote 81 and cleanup.

The clients are synthetic fixtures, NOT original graphical v308 Java clients.
Only the individually owned test Main JVM and temporary profiles are touched.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import select
import socket
import struct
import sys
import tempfile
import time

from synthetic_persistence_reconnect import (
    ReconnectFailure, ports_in_use, start_server, stop_own_server,
    read_bootstrap_and_inventory, log_tail,
)
from synthetic_s2c_bank import FIXED, VAR_SHORT
from synthetic_revision317_login import HOST, PORT, read_exact
from synthetic_v308_position_restart import WALK_EAST_81


class BitReader:
    def __init__(self, raw: bytes):
        self.raw = raw
        self.bits = 0

    def read(self, count: int) -> int:
        if not 0 < count <= 16 or self.bits + count > len(self.raw) * 8:
            raise ReconnectFailure("R43 truncated player81 bit section")
        out = 0
        for _ in range(count):
            k = self.bits
            out = (out << 1) | ((self.raw[k // 8] >> (7 - k % 8)) & 1)
            self.bits += 1
        return out


def decoded_player81(payload: bytes) -> dict:
    """Decode independently the Player81WorldSync bit prefix, not mask tails."""
    if len(payload) < 3 or len(payload) > 65535:
        raise ReconnectFailure("R43 invalid player81 payload")
    bits = BitReader(payload)
    local = bits.read(1)
    if local:
        motion = bits.read(2)
        if motion == 1:
            bits.read(3); bits.read(1)
        elif motion == 2:
            bits.read(3); bits.read(3); bits.read(1)
        elif motion == 3:
            bits.read(2); bits.read(1); bits.read(1)
            bits.read(7); bits.read(7)
        elif motion == 0:
            pass  # local mask-only
        else:
            raise ReconnectFailure("R43 unrecognized local movement")
    old = bits.read(8)
    if old > 16:
        raise ReconnectFailure("R43 excessive remote-player count")
    existing = []
    for _ in range(old):
        updated = bits.read(1)
        if not updated:
            existing.append(("idle", None))
            continue
        action = bits.read(2)
        if action == 0:
            existing.append(("mask", None))
        elif action == 1:
            direction = bits.read(3)
            bits.read(1)
            existing.append(("walk", direction))
        elif action == 2:
            d1, d2 = bits.read(3), bits.read(3)
            bits.read(1)
            existing.append(("run", (d1, d2)))
        elif action == 3:
            existing.append(("remove", None))
        else:
            raise ReconnectFailure("R43 invalid remote action")

    new = []
    for _ in range(16):
        idx = bits.read(11)
        if idx == 2047:
            break
        if idx < 0 or idx > 2046:
            raise ReconnectFailure("R43 invalid remote index")
        appearance = bits.read(1)
        discard_queue = bits.read(1)
        rel_y = bits.read(5)
        rel_x = bits.read(5)
        rel_y = rel_y - 32 if rel_y >= 16 else rel_y
        rel_x = rel_x - 32 if rel_x >= 16 else rel_x
        new.append((idx, rel_x, rel_y, appearance, discard_queue))
    else:
        raise ReconnectFailure("R43 player81 missing sentinel")
    # Presence of mask tails does not affect the already-framed bit prefix.
    return {"local": local, "remote_count": old, "existing": existing,
            "added": new, "bits": bits.bits}


def next_frame_with_options(sock: socket.socket, cipher, timeout=0.4):
    """Strict known-v308 outbound framing, including var-byte packet104."""
    ready, _, _ = select.select([sock], [], [], timeout)
    if not ready:
        return None
    op = (read_exact(sock, 1)[0] - cipher.next_int()) & 255
    if op in FIXED:
        n = FIXED[op]
    elif op in VAR_SHORT:
        n = struct.unpack(">H", read_exact(sock, 2))[0]
    elif op == 104:
        n = read_exact(sock, 1)[0]
    else:
        raise ReconnectFailure(f"R43 unexpected S2C opcode={op}")
    if n > 65535:
        raise ReconnectFailure("R43 packet length exceeds max")
    return op, read_exact(sock, n)


def options_and_remote_add(sock, cipher, label: str) -> int:
    """One remote player must be added; all exact native player options present."""
    options = {}
    remote_index = None
    deadline = time.monotonic() + 15
    while time.monotonic() < deadline:
        frame = next_frame_with_options(sock, cipher)
        if frame is None:
            continue
        op, body = frame
        if op == 104:
            if len(body) < 3 or body[-1] != 10:
                raise ReconnectFailure(f"R43 {label} invalid option104")
            slot = (-body[0]) & 255
            flag = (body[1] - 128) & 255
            text = body[2:-1].decode("iso-8859-1")
            if slot not in range(1, 6) or flag or slot in options:
                raise ReconnectFailure(f"R43 {label} invalid/duplicate option104")
            options[slot] = text
        elif op == 81:
            parsed = decoded_player81(body)
            for idx, dx, dy, mask, reset in parsed["added"]:
                if remote_index is not None or not mask or not reset:
                    raise ReconnectFailure(f"R43 {label} duplicate/bad remote admission")
                if dx != 0 or dy != 0:
                    raise ReconnectFailure(f"R43 {label} unexpected remote spawn delta")
                remote_index = idx
        if remote_index is not None and len(options) == 5:
            if options != {
                1: "Attack", 2: "Follow", 3: "Trade with",
                4: "null", 5: "null",
            }:
                raise ReconnectFailure(f"R43 {label} wrong exact player options")
            return remote_index
    raise ReconnectFailure(f"R43 {label} missing remote spawn/options: "
                           f"idx={remote_index} options={options}")


def see_remote_walk(sock, cipher) -> None:
    deadline = time.monotonic() + 12
    while time.monotonic() < deadline:
        frame = next_frame_with_options(sock, cipher)
        if frame is None:
            continue
        if frame[0] == 81:
            parsed = decoded_player81(frame[1])
            if parsed["remote_count"] == 1 and ("walk", 4) in parsed["existing"]:
                return
            if parsed["added"]:
                raise ReconnectFailure("R43 unexpected remote re-add before walk")
    raise ReconnectFailure("R43 first viewer never saw second player's east step")


def see_remote_removal(sock, cipher) -> None:
    deadline = time.monotonic() + 12
    while time.monotonic() < deadline:
        frame = next_frame_with_options(sock, cipher)
        if frame is None:
            continue
        if frame[0] == 81:
            parsed = decoded_player81(frame[1])
            if parsed["remote_count"] == 1 and ("remove", None) in parsed["existing"]:
                return
    raise ReconnectFailure("R43 first viewer never removed disconnected player")


def wait_player_count(logfile: Path, members: int) -> None:
    deadline = time.monotonic() + 12
    while time.monotonic() < deadline:
        raw = log_tail(logfile, 140000)
        if members == 2:
            if "V512_WORLD_REGISTER" in raw and "members=2" in raw:
                return
        elif members == 1:
            if "V512_WORLD_UNREGISTER" in raw and "members=1" in raw:
                return
        elif members == 0:
            if "V512_WORLD_UNREGISTER" in raw and "members=0" in raw:
                return
        time.sleep(0.1)
    raise ReconnectFailure(f"R43 members={members} never observed in World log")


def journey(jar: Path, java: str) -> dict:
    jar = jar.expanduser().resolve(strict=True)
    if not jar.is_file():
        raise ReconnectFailure("R43 server JAR not found")
    if ports_in_use():
        raise ReconnectFailure("R43 occupied loopback port: refusal before launch")
    with tempfile.TemporaryDirectory(prefix="spk-r43-two-live-players-") as temp:
        root = Path(temp)
        account = root / "only-test-accounts" / "opensrc.properties"
        child, logfile = start_server(java, jar, root, account, "r43-two-player")
        try:
            with socket.create_connection((HOST, PORT), timeout=6) as first:
                first.settimeout(8)
                # Both use the canonical LocalLab alias; the first admitted
                # player gets opensrc; second gets the independent src slot.
                _, first_s2c, _ = read_bootstrap_and_inventory(first)
                with socket.create_connection((HOST, PORT), timeout=6) as second:
                    second.settimeout(8)
                    second_c2s, second_s2c, _ = read_bootstrap_and_inventory(second)
                    wait_player_count(logfile, 2)
                    one_index = options_and_remote_add(first,first_s2c,"first")
                    two_index = options_and_remote_add(second,second_s2c,"second")
                    destination = (3088 + 128) & 255
                    raw_walk = bytes((destination, 3088 >> 8,
                                      3495 & 255,3495 >> 8,0))
                    second.sendall(
                        bytes(((164+second_c2s.next_int())&255,5)) + raw_walk
                    )
                    second_moved = False
                    deadline = time.monotonic() + 12
                    while time.monotonic() < deadline:
                        frame = next_frame_with_options(second, second_s2c)
                        if frame is not None and frame == (81,WALK_EAST_81):
                            second_moved = True
                            break
                    if not second_moved:
                        raise ReconnectFailure("R43 second client's authoritative self-walk missing")
                    see_remote_walk(first, first_s2c)
                wait_player_count(logfile,1)
                see_remote_removal(first,first_s2c)
            wait_player_count(logfile,0)
            text = log_tail(logfile,150000)
            if text.count("LOGIN_SUCCESS_LOCAL") != 2:
                raise ReconnectFailure("R43 incorrect login count")
            if "selected=src" not in text:
                raise ReconnectFailure("R43 second player did not get isolated src profile")
            if "M5_AUTHORITATIVE_TICK mode=WALK" not in text:
                raise ReconnectFailure("R43 server never published authoritative walk")
            if "V5123_ACCOUNT_FINAL_SAVE_WAIT_FAILED" in text:
                raise ReconnectFailure("R43 failed persistent final save")
            if not account.is_file() or not account.with_name("src.properties").is_file():
                raise ReconnectFailure("R43 missing separate primary and secondary account files")
            return {
                "result":"R43_TWO_LIVE_V308_SHAPED_PLAYERS_SYNC_PASS",
                "simultaneous_clients":2,
                "player_option104_both_clients_exact":True,
                "remote_add81_both_clients":True,
                "distinct_remote_indexes":[one_index,two_index],
                "second_player_authoritative_walk81":True,
                "first_viewer_remote_walk81":True,
                "first_viewer_remote_remove81":True,
                "member_counts_observed":[2,1,0],
                "separate_persistent_profiles":["opensrc","src"],
                "original_graphical_v308_client_tested":False,
            }
        finally:
            stop_own_server(child,logfile)


def main(argv=None) -> int:
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--jar",type=Path,default=Path(__file__).resolve().parents[2]
                   / "server"/"build"/"SpawnPKLocalServer.jar")
    p.add_argument("--java",default="java")
    args=p.parse_args(argv)
    try:
        print(json.dumps(journey(args.jar,args.java),indent=2,sort_keys=True))
        return 0
    except (ReconnectFailure,OSError,ValueError,AssertionError) as e:
        print(json.dumps({"result":"R43_TWO_LIVE_V308_PLAYERS_FAIL",
                          "error":str(e)},indent=2,sort_keys=True),file=sys.stderr)
        return 1


if __name__=="__main__":
    raise SystemExit(main())
