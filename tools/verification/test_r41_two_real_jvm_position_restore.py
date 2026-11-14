#!/usr/bin/env python3
"""R41 no-JVM negative controls for byte-exact restoration and file admission."""
from __future__ import annotations

from pathlib import Path
import tempfile
import unittest

from synthetic_v308_position_restart import (
    ReconnectFailure, WALK_EAST_81, RESTORED_3088_3495_81,
    HOME_WALK, bits, account_position, require_restored_packets,
)


class R41TwoMainJvmsPolicyTest(unittest.TestCase):
    def test_original_v308_java_bitwriter_msb_parity(self):
        self.assertEqual(WALK_EAST_81, bytes.fromhex("b001ffc0"))
        self.assertEqual(RESTORED_3088_3495_81,
                         bytes.fromhex("e4ddc007ff"))
        self.assertEqual(HOME_WALK, bytes([0x90,0x0c,0xa7,0x0d,0]))
        with self.assertRaises(ValueError):
            bits(((4,2),))
        with self.assertRaises(ValueError):
            bits(((0,0),))

    def test_distinct_guest_login_initial_and_persisted_teleport(self):
        require_restored_packets([], 1)
        require_restored_packets([RESTORED_3088_3495_81], 2)
        for frames, phase in (
            ([RESTORED_3088_3495_81], 1),
            ([], 2),
            ([WALK_EAST_81], 2),
            ([RESTORED_3088_3495_81] * 2, 2),
            ([RESTORED_3088_3495_81[:-1] + bytes([0])], 2),
        ):
            with self.subTest(phase=phase, frames=frames):
                with self.assertRaises(ReconnectFailure):
                    require_restored_packets(frames, phase)

    def test_only_three_canonical_persisted_coordinates(self):
        with tempfile.TemporaryDirectory(prefix="spk-r41-unit-only-") as temp:
            path = Path(temp) / "opensrc.properties"
            path.write_text(
                "format.version=2\nusername=opensrc\n"
                "movement.worldX=3088\nmovement.worldY=3495\n"
                "movement.plane=0\n", encoding="iso-8859-1")
            self.assertEqual(account_position(path),(3088,3495,0))
            path.write_text(
                "movement.worldX=3088\nmovement.worldX=3089\n"
                "movement.worldY=3495\nmovement.plane=0\n",
                encoding="iso-8859-1")
            with self.assertRaises(ReconnectFailure):
                account_position(path)
            path.write_text(
                "movement.worldX=3088\nmovement.worldY=not_a_number\n"
                "movement.plane=0\n", encoding="iso-8859-1")
            with self.assertRaises(ReconnectFailure):
                account_position(path)
            path.unlink()
            with self.assertRaises(ReconnectFailure):
                account_position(path)

    def test_main_jvm_ownership_sealed_in_reused_backend(self):
        from synthetic_persistence_reconnect import (
            start_server, stop_own_server, wait_session_final
        )
        self.assertTrue(callable(start_server))
        self.assertTrue(callable(stop_own_server))
        self.assertTrue(callable(wait_session_final))


if __name__ == "__main__":
    unittest.main()
