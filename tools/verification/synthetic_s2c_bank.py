"""Fail-closed decoder for the S2C subset used by the LocalLab bank wire test.

Not a general client protocol specification. Opcodes are ISAAC-decrypted and
framed solely where this repository has explicit fixed/var length evidence.
"""
from __future__ import annotations

import select
import struct

FIXED = {
    36: 3, 44: 5, 71: 3, 73: 4, 85: 2, 97: 2,
    101: 2, 110: 1, 134: 6, 151: 4, 156: 3,
    219: 0, 248: 4, 249: 3,
}
VAR_SHORT = {53, 65, 81, 126}
MAX_FRAME = 65535


def next_server_frame(sock, cipher, read_exact, timeout=0.3):
    """Return (opcode, payload) or None if no packet starts by deadline.

    A pending header/body is read exactly with the socket timeout; it is never
    silently skipped, and unsupported opcodes fail rather than desync ISAAC.
    """
    ready, _, _ = select.select([sock], [], [], timeout)
    if not ready:
        return None
    op = (read_exact(sock, 1)[0] - cipher.next_int()) & 255
    if op in FIXED:
        size = FIXED[op]
    elif op in VAR_SHORT:
        size = struct.unpack(">H", read_exact(sock, 2))[0]
    else:
        raise AssertionError(f"unrecognized S2C opcode {op}; wire parity not proven")
    if size > MAX_FRAME:
        raise AssertionError("oversized S2C frame")
    return op, read_exact(sock, size)


def decode_container53(data: bytes) -> tuple[int, tuple[tuple[int, int], ...]]:
    """Decode full client widget slots: BE u16 widget/count, byte/legacy-Y qty, U item."""
    if len(data) < 4:
        raise AssertionError("truncated 53 container header")
    widget, count = struct.unpack(">HH", data[:4])
    if count > 352:
        raise AssertionError(f"unsupported widget size {count}")
    slots = []
    p = 4
    for _ in range(count):
        if p >= len(data):
            raise AssertionError("truncated item quantity")
        qty = data[p]
        p += 1
        if qty == 255:
            if p + 4 > len(data):
                raise AssertionError("truncated extended quantity")
            b0, b1, b2, b3 = data[p:p+4]
            qty = (b1 << 24) | (b0 << 16) | (b3 << 8) | b2
            p += 4
        if p + 2 > len(data):
            raise AssertionError("truncated item id")
        lo, hi = data[p:p+2]
        item = ((hi << 8) | ((lo - 128) & 255)) - 1
        p += 2
        if item == -1 and qty != 0:
            raise AssertionError("empty slot has nonzero quantity")
        slots.append((item, qty))
    if p != len(data):
        raise AssertionError(f"trailing bytes in container53: {len(data)-p}")
    return widget, tuple(slots)


def require_bank_pair(received, expected_bank, expected_inventory):
    bank = received.get(5382)
    inv = received.get(5064)
    if bank is None or inv is None:
        raise AssertionError("bank wire pair missing one or both packet53 containers")
    if len(bank) != 352 or len(inv) != 28:
        raise AssertionError(f"incorrect fixed container slots {len(bank)}/{len(inv)}")
    if bank[0] != (995, expected_bank):
        raise AssertionError(f"bank wire slot0 {bank[0]} expected (995,{expected_bank})")
    if inv[0] != expected_inventory:
        raise AssertionError(f"inventory wire slot0 {inv[0]} expected {expected_inventory}")
    if any(item != -1 for item, _ in inv[1:]):
        raise AssertionError("unexpected inventory item created elsewhere")
    return {"bank_coins": bank[0][1], "inventory_slot0": inv[0]}
