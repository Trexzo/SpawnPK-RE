"""Independent exact S2C framing/quantity/item decoder regression checks."""
import socket
import struct
import unittest

from synthetic_s2c_bank import (
    decode_container53, next_server_frame, require_bank_pair,
)


def encoded_slot(item, quantity):
    if quantity < 0 or quantity > 0xFFFFFFFF:
        raise ValueError("quantity")
    if quantity < 255:
        qty = bytes([quantity])
    else:
        qty = bytes([
            255, (quantity >> 16) & 255, (quantity >> 24) & 255,
            quantity & 255, (quantity >> 8) & 255,
        ])
    wire_id = item + 1
    return qty + bytes([(wire_id + 128) & 255, wire_id >> 8])


class ConstantCipher:
    def __init__(self, value=7):
        self.value = value
        self.calls = 0

    def next_int(self):
        self.calls += 1
        return self.value


class S2CBankWireTests(unittest.TestCase):
    def test_container53_legacy_short_and_extended_quantity(self):
        data = (
            struct.pack(">HH", 5382, 3)
            + encoded_slot(995, 100000)
            + encoded_slot(-1, 0)
            + encoded_slot(560, 2500)
        )
        widget, slots = decode_container53(data)
        self.assertEqual(widget, 5382)
        self.assertEqual(slots, ((995, 100000), (-1, 0), (560, 2500)))
        for bad in (data[:-1], data + b"\x00", data[:4] + b"\x00"):
            with self.subTest(bad=bad):
                with self.assertRaises(AssertionError):
                    decode_container53(bad)

    def test_bank_pair_uses_wire_quantities(self):
        bank = [(995, 99999)] + [(-1, 0)] * 351
        inv = [(995, 1)] + [(-1, 0)] * 27
        receipt = require_bank_pair({5382: bank, 5064: inv}, 99999, (995, 1))
        self.assertEqual(receipt["bank_coins"], 99999)
        inv[0] = (-1, 0)
        with self.assertRaises(AssertionError):
            require_bank_pair({5382: bank, 5064: inv}, 99999, (995, 1))
        with self.assertRaises(AssertionError):
            require_bank_pair({5382: bank}, 99999, (995, 1))

    def test_isaac_framed_fixed_varshort_and_reject_unknown(self):
        a, b = socket.socketpair()
        try:
            a.settimeout(3)
            b.settimeout(3)
            from synthetic_revision317_login import read_exact
            cipher = ConstantCipher(7)
            b.sendall(bytes([71 + 7]) + b"\x01\x02\x03")
            self.assertEqual(next_server_frame(a, cipher, read_exact),
                             (71, b"\x01\x02\x03"))
            payload = struct.pack(">HH", 5064, 1) + encoded_slot(995, 1)
            b.sendall(bytes([(53 + 7) & 255]) + struct.pack(">H", len(payload)) + payload)
            self.assertEqual(next_server_frame(a, cipher, read_exact),
                             (53, payload))
            self.assertIsNone(next_server_frame(a, cipher, read_exact, 0.02))
            b.sendall(bytes([(254 + 7) & 255]))
            with self.assertRaisesRegex(AssertionError, "unrecognized S2C"):
                next_server_frame(a, cipher, read_exact)
            self.assertEqual(cipher.calls, 3)
        finally:
            a.close()
            b.close()


if __name__ == "__main__":
    unittest.main()
