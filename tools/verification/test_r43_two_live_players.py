#!/usr/bin/env python3
"""R43 offline player81 and packet104 negative controls (no socket or JVM)."""
from __future__ import annotations
import unittest

from synthetic_persistence_reconnect import ReconnectFailure
from synthetic_v308_two_live_players import (
    BitReader, decoded_player81, WALK_EAST_81,
)


def packet81(*fields):
    bits="".join(format(v,f"0{n}b") for v,n in fields)
    if not bits or any(v<0 or v >= 1<<n for v,n in fields):
        raise ValueError("bad test vector")
    return int(bits.ljust((len(bits)+7)//8*8,"0"),2).to_bytes(
        (len(bits)+7)//8,"big"
    )


class R43RemotePlayer81WireNegativeTests(unittest.TestCase):
    def test_exact_local_idle_and_walk_parity(self):
        idle=packet81((0,1),(0,8),(2047,11))
        self.assertEqual(idle,bytes.fromhex("007ff0"))
        result=decoded_player81(idle)
        self.assertEqual(result["remote_count"],0)
        self.assertEqual(result["added"],[])
        self.assertEqual(WALK_EAST_81,bytes.fromhex("b001ffc0"))
        self.assertEqual(decoded_player81(WALK_EAST_81)["remote_count"],0)
        self.assertEqual(decoded_player81(WALK_EAST_81)["local_motion"],("walk",4))
        # Actual multiplayer publisher merges local WALK and an existing
        # remote-player track into a *single* S2C81 payload. Requiring
        # standalone local-only b001ffc0 would be an invalid assertion.
        combined=packet81(
            (1,1),(1,2),(4,3),(0,1),  # local walk east
            (1,8),(0,1),              # one idle existing remote
            (2047,11)
        )
        self.assertNotEqual(combined,WALK_EAST_81)
        decoded=decoded_player81(combined)
        self.assertEqual(decoded["local_motion"],("walk",4))
        self.assertEqual(decoded["remote_count"],1)
        self.assertEqual(decoded["existing"],[("idle",None)])

    def test_remote_add_relative_offsets_and_sentinel(self):
        body=packet81(
            (0,1), # local idle
            (0,8), # old remote count
            (2,11),(1,1),(1,1),(0,5),(0,5), # add client index2, mask
            (2047,11)
        )+bytes([16,0])
        parsed=decoded_player81(body)
        self.assertEqual(parsed["added"],[(2,0,0,1,1)])
        self.assertEqual(parsed["existing"],[])
        self.assertEqual(parsed["remote_count"],0)

    def test_remote_east_walk_and_following_removal(self):
        walking=packet81(
            (0,1),(1,8), # local idle, one existing remote
            (1,1),(1,2),(4,3),(0,1), # movement type walk east; no mask
            (2047,11),
        )
        parsed=decoded_player81(walking)
        self.assertEqual(parsed["existing"],[("walk",4)])
        self.assertEqual(parsed["remote_count"],1)
        leaving=packet81(
            (0,1),(1,8),(1,1),(3,2),(2047,11)
        )
        self.assertEqual(decoded_player81(leaving)["existing"],[("remove",None)])

    def test_reject_malformed_truncated_without_creating_remote(self):
        with self.assertRaises(ReconnectFailure):
            BitReader(b"").read(1)
        with self.assertRaises(ReconnectFailure):
            decoded_player81(b"\x00")
        # No end-of-new-players sentinel
        bad=packet81((0,1),(0,8),(2,11),(1,1),(1,1),(0,5),(0,5))
        with self.assertRaises(ReconnectFailure):
            decoded_player81(bad)
        # Malformed oversized old-remote count
        excessive=packet81((0,1),(17,8),(2047,11))
        with self.assertRaises(ReconnectFailure):
            decoded_player81(excessive)


if __name__=="__main__":
    unittest.main()
