"""R6 custom mortal NPC wire-contract and isolated fixture-option checks."""
import tempfile
from pathlib import Path
import unittest

from synthetic_mortal_pvm_loot import (
    attack_npc72, walk164, discover_fixture,
)
from synthetic_persistence_reconnect import ReconnectFailure, start_server
from synthetic_revision317_login import Isaac


class MortalNpcWireTests(unittest.TestCase):
    def test_client_npc_attack_opcode72_be_a_and_cipher(self):
        seed = (1, 2, 3, 4)
        actual = attack_npc72(Isaac(seed), 16382)
        cipher = Isaac(seed)
        self.assertEqual(actual, bytes([
            (72 + cipher.next_int()) & 255,
            16382 >> 8, (16382 + 128) & 255,
        ]))
        self.assertEqual(len(actual), 3)
        for value in (-1, 16383, 65535):
            with self.assertRaises(ValueError):
                attack_npc72(Isaac(seed), value)

    def test_client_walk164_exact_to_loot(self):
        seed = (1, 2, 3, 4)
        actual = walk164(Isaac(seed), 3088, 3495)
        cipher = Isaac(seed)
        self.assertEqual(actual, bytes([
            (164 + cipher.next_int()) & 255, 5,
            (3088 + 128) & 255, 3088 >> 8,
            3495 & 255, 3495 >> 8, 0,
        ]))

    def test_mortal_spawn_log_requires_exact_hp_tile_and_provenance(self):
        with tempfile.TemporaryDirectory() as directory:
            logfile = Path(directory) / "synthetic.log"
            logfile.write_text(
                "R6_MORTAL_NPC_BOOTSTRAP scene=16382 def=1 "
                "world=3088,3495 hp=10 loot=995x1 "
                "authority=CUSTOM_LOCALLAB_OPT_IN_FIXTURE\n",
                encoding="ascii",
            )
            self.assertEqual(discover_fixture(logfile), 16382)
            logfile.write_text(
                "R6_MORTAL_NPC_BOOTSTRAP scene=16382 def=1 "
                "world=1234,3495 hp=10 loot=995x1\n",
                encoding="ascii",
            )
            with self.assertRaises(ReconnectFailure):
                discover_fixture(logfile)

    def test_reject_nonfixture_vm_flags_before_spawn(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self.assertRaises(ReconnectFailure):
                start_server(
                    "java", root / "dummy.jar", root,
                    root / "account.properties", "test",
                    fixture_options=("-Dspk.local.r6MortalFixture=false",),
                )


if __name__ == "__main__":
    unittest.main()
