#!/usr/bin/env python3
"""Independent, synthetic revision-317 LocalLab login/bootstrap wire probe.

The original SpawnPK graphical client is NOT involved. This test cannot prove
real-client compatibility, gameplay, logout persistence, or crash durability.
"""
from __future__ import annotations

import socket
import struct

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


def accepted_login_and_bootstrap(host: str = HOST, port: int = PORT) -> dict:
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
        return {
            "result": "SYNTHETIC_REV317_LOGIN_BOOTSTRAP_PASS",
            "revision": 317,
            "client_config_fixture": 308,
            "s2c_first_five": [249, 73, 81, 110, 126],
            "real_graphical_client_tested": False,
            "durable_account_save_tested": False,
        }
