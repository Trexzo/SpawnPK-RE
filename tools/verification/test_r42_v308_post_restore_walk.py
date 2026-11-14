#!/usr/bin/env python3
"""R42 no-network, no-Java negative controls for three-JVM restored movement."""
from __future__ import annotations

import unittest

from synthetic_persistence_reconnect import ReconnectFailure
from synthetic_v308_post_restore_walk import (
    restore81, walk164, check_initial_restore
)


class R42ResumedMovementWireTests(unittest.TestCase):
    def test_exact_java_bootstrap_restore_81_vectors(self):
        # Independently fixed against BootstrapPackets.BitWriter and R40.
        self.assertEqual(restore81(3088, 3495), bytes.fromhex("e4ddc007ff"))
        self.assertEqual(restore81(3089, 3495), bytes.fromhex("e4ddc807ff"))
        self.assertEqual(len(restore81(3089,3495)), 5)
        self.assertNotEqual(restore81(3088,3495),restore81(3089,3495))

    def test_exact_le_a_one_tile_waypoint(self):
        self.assertEqual(walk164(3088),bytes.fromhex("900ca70d00"))
        self.assertEqual(walk164(3089),bytes.fromhex("910ca70d00"))
        self.assertNotEqual(walk164(3088),walk164(3089))

    def test_strict_immutable_home_position_and_duplicated_restore_denials(self):
        check_initial_restore([],None)
        check_initial_restore([bytes.fromhex("e4ddc007ff")],(3088,3495))
        check_initial_restore([bytes.fromhex("e4ddc807ff")],(3089,3495))
        for got,expected in (
            ([restore81(3088,3495)],None),
            ([],(3088,3495)),
            ([restore81(3089,3495)],(3088,3495)),
            ([restore81(3088,3495)]*2,(3088,3495)),
            ([restore81(3088,3495)],(3089,3495)),
        ):
            with self.subTest(got=got,expected=expected):
                with self.assertRaises(ReconnectFailure):
                    check_initial_restore(got,expected)

    def test_reject_invalid_home_coords_and_plane(self):
        for x,y,plane in (
            (3031,3495,0),(3136,3495,0),(3088,3439,0),
            (3088,3544,0),(3088,3495,1)
        ):
            with self.subTest(x=x,y=y,plane=plane):
                with self.assertRaises(ReconnectFailure):
                    restore81(x,y,plane)
        for x in (3032,3136,-1,70000):
            with self.subTest(x=x):
                with self.assertRaises(ReconnectFailure):
                    walk164(x)


if __name__ == "__main__":
    unittest.main()
