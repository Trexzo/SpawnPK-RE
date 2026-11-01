#!/usr/bin/env python3
"""Independent, synthetic revision-317 LocalLab login/bootstrap wire probe.

The original SpawnPK graphical client is NOT involved. This test cannot prove
real-client compatibility, gameplay, logout persistence, or crash durability.
"""
from __future__ import annotations

import socket
import struct
import time

HOST = "127.0.0.1"
PORT = 43594
MASK = 0xFFFFFFFF
SEEDS_HEAD = (0x01020304, 0x11223344)


def u32(v: int) -> int:
    return v & MASK


def _mix(values):
    a, b, c, d, e, f, g, h = values
    a = u32(a ^ (b << 11)); d = u32(d + a); b = u32(b + c)
    b = u32(b ^ (c >> 2)); e = u32(e + b); c = u32(c + d)
    c = u32(c ^ (d << 8)); f = u32(f + c); d = u32(d + e)
    d = u32(d ^ (e >> 16)); g = u32(g + d); e = u32(e + f)
    e = u32(e ^ (f << 10)); h = u32(h + e); f = u32(f + g)
    f = u32(f ^ (g >> 4)); a = u32(a + f); g = u32(g + h)
    g = u32(g ^ (h << 8)); b = u32(b + g); h = u32(h + a)
    h = u32(h ^ (a >> 9)); c = u32(c + h); a = u32(a + b)
    return [a, b, c, d, e, f, g, h]


class Isaac:
    """Unsigned Python equivalent of this repository's spk.local.IsaacCipher."""
    def __init__(self, seeds):
        self.results = [0] * 256
        self.memory = [0] * 256
        self.results[:min(len(seeds), 256)] = [u32(v) for v in seeds[:256]]
        state = [0x9E3779B9] * 8
        for _ in range(4):
            state = _mix(state)
        for pass_number in range(2):
            source = self.results if pass_number == 0 else self.memory[:]
            for i in range(0, 256, 8):
                state = _mix([u32(a + b) for a, b in zip(state, source[i:i+8])])
                self.memory[i:i+8] = state
        self.accumulator = 0
        self.last = 0
        self.counter = 0
        self._isaac()
        self.count = 256

    def _isaac(self):
        self.counter = u32(self.counter + 1)
        self.last = u32(self.last + self.counter)
        for i in range(256):
            x = self.memory[i]
            mod = i & 3
            if mod == 0:
                self.accumulator = u32(self.accumulator ^ (self.accumulator << 13))
            elif mod == 1:
                self.accumulator = u32(self.accumulator ^ (self.accumulator >> 6))
            elif mod == 2:
                self.accumulator = u32(self.accumulator ^ (self.accumulator << 2))
            else:
                self.accumulator = u32(self.accumulator ^ (self.accumulator >> 16))
            self.accumulator = u32(self.accumulator + self.memory[(i + 128) & 255])
            y = u32(self.memory[(x & 1020) >> 2] + self.accumulator + self.last)
            self.memory[i] = y
            self.last = u32(self.memory[((y >> 8) & 1020) >> 2] + x)
            self.results[i] = self.last

    def next_int(self):
        old = self.count
        self.count -= 1
        if old == 0:
            self._isaac()
            self.count = 255
        return self.results[self.count]


def bank_action_145(cipher: Isaac, widget: int, slot: int, item: int) -> bytes:
    """Pinned three BE-A u16 action fields, ISAAC-encrypted opcode 145."""
    if any(v < 0 or v > 0xFFFF for v in (widget, slot, item)):
        raise ValueError("bank action field outside u16")
    def be_a(v):
        return bytes([v >> 8, (v + 128) & 255])
    return bytes([(145 + cipher.next_int()) & 255]) + (
        be_a(widget) + be_a(slot) + be_a(item)
    )


def read_exact(sock: socket.socket, size: int) -> bytes:
    value = bytearray()
    while len(value) < size:
        part = sock.recv(size - len(value))
        if not part:
            raise AssertionError(f"truncated socket frame: {len(value)}/{size}")
        value.extend(part)
    return bytes(value)


def login_body(server_seed: int, revision: int = 317) -> tuple[bytes, tuple[int, ...]]:
    seeds = (*SEEDS_HEAD, (server_seed >> 32) & MASK, server_seed & MASK)
    # Exact LocalLab fixture fields. Not an assertion about original client auth.
    inner = (
        b"\x0a" + struct.pack(">4I2I", *seeds, 748878668, 308)
        + b"local\nlocalpass\nLOCAL-DEVICE\nLOCAL-CLIENT\n"
    )
    if len(inner) > 255:
        raise AssertionError("inner frame overflow")
    outer = b"\xff" + struct.pack(">HB", revision, 0) + b"\x00" * 36
    outer += bytes([len(inner)]) + inner
    if len(outer) > 255:
        raise AssertionError("outer frame overflow")
    return b"\x10" + bytes([len(outer)]) + outer, seeds


def _begin(sock: socket.socket, revision: int):
    sock.sendall(bytes([14, 7]))
    pre = read_exact(sock, 17)
    if pre[:8] != b"\x00" * 8 or pre[8] != 0:
        raise AssertionError(f"prelogin refused: {pre[:9].hex()}")
    seed = struct.unpack(">Q", pre[9:])[0]
    login, seeds = login_body(seed, revision)
    sock.sendall(login)
    return seeds


def rejected_revision(host: str = HOST, port: int = PORT) -> None:
    with socket.create_connection((host, port), timeout=5) as sock:
        sock.settimeout(5)
        _begin(sock, revision=316)
        try:
            reply = sock.recv(1)
        except ConnectionResetError:
            # Peer closing after parsing the deliberately incompatible revision.
            reply = b""
        if reply != b"":
            raise AssertionError("server admitted unsupported protocol revision 316")


def accepted_login_and_bootstrap(host: str = HOST, port: int = PORT,
                                 walk_bank: bool = False,
                                 read_server_log=None,
                                 bank_roundtrip: bool = False,
                                 wire_inventory: bool = False) -> dict:
    if bank_roundtrip and not walk_bank:
        raise ValueError("bank roundtrip requires movement-to-bank test")
    if wire_inventory and not bank_roundtrip:
        raise ValueError("wire parity requires bank roundtrip")
    with socket.create_connection((host, port), timeout=5) as sock:
        sock.settimeout(8)
        seeds = _begin(sock, revision=317)
        result = read_exact(sock, 3)
        if result != bytes([2, 205, 0]):
            raise AssertionError(f"unexpected LocalLab login result: {result.hex()}")

        c2s = Isaac(seeds)
        s2c = Isaac([u32(x + 50) for x in seeds])
        # Existing pinned LocalLab test starts via native widget 912.
        sock.sendall(bytes([(185 + c2s.next_int()) & 255, 912 >> 8, 912 & 255]))

        def packet(expected, size=None):
            opcode = (read_exact(sock, 1)[0] - s2c.next_int()) & 255
            if opcode != expected:
                raise AssertionError(f"bootstrap opcode: got {opcode}, expected {expected}")
            if size is None:
                size = struct.unpack(">H", read_exact(sock, 2))[0]
            if size > 16_384:
                raise AssertionError("oversized bootstrap packet")
            return read_exact(sock, size)

        player = packet(249, 3)
        region = packet(73, 4)
        appearance = packet(81)
        energy = packet(110, 1)
        run_text = packet(126)
        if player != bytes([128, 129, 0]):
            raise AssertionError(f"local player index mismatch: {player.hex()}")
        if region != bytes([1, 1, 1, 180]):
            raise AssertionError(f"HOME region mismatch: {region.hex()}")
        if len(appearance) < 8:
            raise AssertionError("missing local appearance update")
        if energy[0] > 100 or b"%" not in run_text:
            raise AssertionError("missing run-energy/bootstrap widget")
        if walk_bank:
            if wire_inventory:
                from synthetic_s2c_bank import (
                    decode_container53, next_server_frame, require_bank_pair,
                )
                wire_overlay = False
                wire_bank = {}
            if read_server_log is None:
                raise ValueError("movement/bank proof requires an isolated server log reader")
            # Pinned v308 walk serializer: LE short-A X, LE Y, negated run.
            # From HOME 3087,3495 to 3089,3495 is exactly two east tiles.
            walk = bytes([
                (3089 + 128) & 255, 3089 >> 8,
                3495 & 255, 3495 >> 8,
                0,
            ])
            # The object writer is (X LE-A, objectId BE, Y BE-A).
            obj = bytes([
                (3090 + 128) & 255, 3090 >> 8,
                26972 >> 8, 26972 & 255,
                3495 >> 8, (3495 + 128) & 255,
            ])
            sock.sendall(
                bytes([(164 + c2s.next_int()) & 255, len(walk)]) + walk
                + bytes([(132 + c2s.next_int()) & 255]) + obj
            )
            deadline = time.monotonic() + 15
            accepted = False
            stepped = False
            bank_open = False
            while time.monotonic() < deadline:
                text = read_server_log()
                # Check authoritative state transitions, not simply receipt
                # of the encrypted bytes or a non-atomic anticipated log.
                accepted = accepted or (
                    "M5_MOVEMENT_REQUEST MovementRequest{opcode=164, run=false, waypoints=[3089,3495]} action=ACCEPTED" in text
                )
                stepped = stepped or (
                    "M5_AUTHORITATIVE_TICK mode=WALK tiles=1 from=3087,3495" in text
                    or "M5_AUTHORITATIVE_TICK mode=RUN tiles=2 from=3087,3495" in text
                )
                # A real bank-opening decision must be bound to the
                # advertised target and within 1 tile, not just a widget.
                bank_open = bank_open or (
                    "V5_BANK_OPEN ObjectInteraction{opcode=132, objectId=26972, worldX=3090, worldY=3495}" in text
                    and "distance=1" in text
                    and "bankContainer=5382" in text
                    and "action=OPENED_" in text
                )
                if (accepted and stepped and bank_open and
                        (not wire_inventory or
                         (wire_overlay and 5382 in wire_bank and 5064 in wire_bank))):
                    break
                # When checking wire parity, drain complete ISAAC frames.
                # Never consume an arbitrary recv buffer that would discard
                # stream boundaries/cipher state before packet53 inspection.
                if wire_inventory:
                    frame = next_server_frame(sock, s2c, read_exact)
                    if frame is not None:
                        opcode, payload = frame
                        if opcode == 248 and payload == bytes([
                            5292 >> 8, (5292 + 128) & 255,
                            5063 >> 8, 5063 & 255,
                        ]):
                            wire_overlay = True
                        elif opcode == 53 and wire_overlay:
                            widget, slots = decode_container53(payload)
                            if widget in (5382, 5064):
                                wire_bank[widget] = slots
                else:
                    sock.settimeout(0.3)
                    try:
                        if not sock.recv(65536):
                            raise AssertionError("server closed before movement/bank evidence")
                    except socket.timeout:
                        pass
            if not (accepted and stepped and bank_open):
                raise AssertionError(
                    "movement/bank proof incomplete; "
                    f"accepted={accepted} stepped={stepped} bank_open={bank_open}"
                )
            if wire_inventory:
                if not wire_overlay:
                    raise AssertionError("S2C248 bank overlay missing")
                require_bank_pair(wire_bank, 100_000, (-1, 0))
            if bank_roundtrip:
                # A rejected item mismatch must not grant currency. Then
                # withdraw 995x1 to inventory, deposit it back, and attempt
                # the stale deposit again. All four requests are normal,
                # ISAAC-encrypted C2S145, not direct in-memory test calls.
                cases = (
                    (5382, 0, 996, "REJECTED_BANK_ITEM_MISMATCH expected=995"),
                    (5382, 0, 995, "result=WITHDRAW_OK amount=1 requested=1 stackable=true bankOccupied=7 inventoryOccupied=1"),
                    (5064, 0, 995, "result=STORE_OK amount=1 stackable=true inventorySlotPreserved=true bankOccupied=7 inventoryOccupied=0"),
                    (5064, 0, 995, "result=REJECTED_INVENTORY_SLOT"),
                )
                wire_changes = 0
                for action_index, (widget, slot, item, expected) in enumerate(cases):
                    sock.sendall(bank_action_145(c2s, widget, slot, item))
                    cutoff = time.monotonic() + 10
                    passed = False
                    latest_pair = {}
                    while time.monotonic() < cutoff:
                        lines = read_server_log().splitlines()
                        # Match the expected bank action on the same log
                        # line as the specific widget/item request. Never
                        # count an unrelated bank operation.
                        passed = any(
                            "V522_BANK_ITEM_ACTION" in line
                            and f"widgetId={widget}, slot={slot}, itemId={item}" in line
                            and expected in line
                            for line in lines
                        )
                        if passed and (not wire_inventory or action_index in (0, 3) or
                                       (5382 in latest_pair and 5064 in latest_pair)):
                            break
                        if wire_inventory:
                            frame = next_server_frame(sock, s2c, read_exact)
                            if frame is not None and frame[0] == 53:
                                reported_widget, slots = decode_container53(frame[1])
                                if reported_widget in (5382, 5064):
                                    if action_index in (0, 3):
                                        raise AssertionError(
                                            "invalid or stale bank request published container53"
                                        )
                                    latest_pair[reported_widget] = slots
                        else:
                            sock.settimeout(0.3)
                            try:
                                if not sock.recv(65536):
                                    raise AssertionError(
                                        f"server closed during bank action widget={widget}"
                                    )
                            except socket.timeout:
                                pass
                    if not passed:
                        raise AssertionError(
                            f"bank action not confirmed widget={widget} item={item} expected={expected}"
                        )
                    if wire_inventory and action_index in (1, 2):
                        if len(latest_pair) != 2:
                            raise AssertionError(
                                "server log claimed mutation but S2C53 pair not observed"
                            )
                        if action_index == 1:
                            require_bank_pair(latest_pair, 99_999, (995, 1))
                        else:
                            require_bank_pair(latest_pair, 100_000, (-1, 0))
                        wire_changes += 1
                if wire_inventory and wire_changes != 2:
                    raise AssertionError("missing positive S2C53 bank mutation updates")
        return {
            "result": ("SYNTHETIC_REV317_BANK_ROUNDTRIP_PASS" if bank_roundtrip
                       else "SYNTHETIC_REV317_WALK_BANK_PASS" if walk_bank
                       else "SYNTHETIC_REV317_LOGIN_BOOTSTRAP_PASS"),
            "authoritative_walk_observed": walk_bank,
            "bank_open_observed": walk_bank,
            "bank_negative_mismatch_observed": bank_roundtrip,
            "bank_withdraw_deposit_observed": bank_roundtrip,
            "bank_stale_deposit_refused": bank_roundtrip,
            "s2c53_bank_inventory_parity": wire_inventory,
            "s2c53_positive_mutation_pairs": 2 if wire_inventory else 0,
            "revision": 317,
            "client_config_fixture": 308,
            "s2c_first_five": [249, 73, 81, 110, 126],
            "real_graphical_client_tested": False,
            "durable_account_save_tested": False,
        }
