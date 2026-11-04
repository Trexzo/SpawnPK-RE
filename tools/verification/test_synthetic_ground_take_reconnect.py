"""R5 pinned item Drop/Take request encoding and scene response fixtures."""
import unittest

from synthetic_revision317_login import Isaac
from synthetic_ground_take_reconnect import (
    be_a, decode_remove156, decode_spawn44, drop_packet, take_packet,
)


class GroundTakeFixtureTests(unittest.TestCase):
    def test_drop_packet87_exact_BE_A_layout_and_isaac(self):
        actual = drop_packet(Isaac((1, 2, 3, 4)), 995, 3214, 0)
        enc = Isaac((1, 2, 3, 4))
        self.assertEqual(actual, bytes([
            (87 + enc.next_int()) & 255,
            995 >> 8, (995 + 128) & 255,
            3214 >> 8, 3214 & 255, 0, 128,
        ]))
        self.assertEqual(len(actual), 7)
        for invalid in (-1, 65536):
            with self.assertRaises(ValueError):
                be_a(invalid)

    def test_take_packet236_exact_Y_LE_ITEM_BE_X_LE(self):
        actual = take_packet(Isaac((1, 2, 3, 4)), 995, 3089, 3495)
        enc = Isaac((1, 2, 3, 4))
        self.assertEqual(actual, bytes([
            (236 + enc.next_int()) & 255,
            3495 & 255, 3495 >> 8,
            995 >> 8, 995 & 255,
            3089 & 255, 3089 >> 8,
        ]))
        self.assertEqual(len(actual), 7)
        with self.assertRaises(ValueError):
            take_packet(enc, -1, 3089, 3495)

    def test_scene_spawn_remove_same_item_and_tile(self):
        coord = 0xA6
        spawn = bytes([
            (995 + 128) & 255, 995 >> 8,
            0, 1, coord,
        ])
        remove = bytes([
            (coord + 128) & 255,
            995 >> 8, 995 & 255,
        ])
        self.assertEqual(decode_spawn44(spawn), (995, 1, coord))
        self.assertEqual(decode_remove156(remove), (995, coord))
        with self.assertRaises(AssertionError):
            decode_spawn44(spawn[:-1])
        with self.assertRaises(AssertionError):
            decode_remove156(remove[:-1])


if __name__ == "__main__":
    unittest.main()
