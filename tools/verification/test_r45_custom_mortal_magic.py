#!/usr/bin/env python3
"""R45 offline controls for sole opt-in rune-free Air surge mortal NPC."""
from __future__ import annotations
import unittest
from pathlib import Path

from synthetic_revision317_login import Isaac
from synthetic_persistence_reconnect import ReconnectFailure, start_server
from synthetic_v308_custom_mortal_magic import FLAGS,npc_spell131


class R45CustomMortalMagicWireAndAdmissionTests(unittest.TestCase):
    def test_exact_v308_c2s131_short_a_wire(self):
        seed=[1,2,3,4]
        c=Isaac(seed)
        one=npc_spell131(c,16382,19100)
        self.assertEqual(len(one),5)
        # LE scene short-A (16382 = 0x3ffe), BE spell short-A (0x4a9c).
        self.assertEqual(one[1:],bytes([0x7e,0x3f,0x4a,0x1c]))
        self.assertEqual(npc_spell131(c,103,1152)[1:],
                         bytes([231,0,4,128]))
        for index,widget in ((-1,19100),(16383,19100),
                             (12,-1),(12,65536)):
            with self.subTest(index=index,widget=widget):
                with self.assertRaises(ValueError):
                    npc_spell131(c,index,widget)

    def test_spell_feature_is_explicitly_paired_with_mortal_actor(self):
        self.assertEqual(set(FLAGS),{
            "-Dspk.local.r6MortalFixture=true",
            "-Dspk.local.r45MortalMagic=true",
        })
        with self.assertRaises(ReconnectFailure):
            start_server("none",Path("absent"),Path("."),Path("absent"),
                         "nope",fixture_options=(
                             "-Dspk.local.r45MortalMagic=true",))
        with self.assertRaises(ReconnectFailure):
            start_server("none",Path("absent"),Path("."),Path("absent"),
                         "nope",fixture_options=FLAGS+(
                             "-Dspk.local.r45MortalMagic=true",))

    def test_default_spell_targets_and_original_authority_unchanged(self):
        root=Path(__file__).resolve().parents[2]/"server"/"src"/"spk"/"local"
        handler=(root/"LocalSpellTargetHandler.java").read_text()
        self.assertIn("Boolean.getBoolean(\"spk.local.r45MortalMagic\")",handler)
        self.assertIn("TARGET_NPC_VISIBLE def=",handler)
        self.assertIn("effect=UNIMPLEMENTED_SERVER_AUTHORITY",handler)
        coordinator=(root/"LocalWorldTickCoordinator.java").read_text()
        self.assertIn("spell.widget!=19100",coordinator)
        self.assertIn("spell.resources.length!=0",coordinator)
        self.assertIn("R45_REJECTED_CUSTOM_CAST_COOLDOWN",coordinator)
        self.assertIn("R45_REJECTED_TARGET_OUT_OF_RANGE",coordinator)
        self.assertIn("LocalLabMortalNpcFixture.applyPublishedDamage",coordinator)
        self.assertIn("pvmKillOutcomes.publishCompletedDeath",coordinator)
        self.assertNotIn("spellXp=ORIGINAL",coordinator)

    def test_magic_cannot_fake_registered_world_or_mutate_before_checks(self):
        root=Path(__file__).resolve().parents[2]/"server"/"src"/"spk"/"local"
        source=(root/"LocalWorldTickCoordinator.java").read_text()
        start=source.index("String castLocalLabMortalAirSurge(")
        end=source.index("private void tickTransientRegion(",start)
        method=source[start:end]
        self.assertLess(method.index("worldPlayer.registered()"),
                        method.index("npcs.sendMask("))
        self.assertLess(method.index("spell.resources.length!=0"),
                        method.index("npcs.sendMask("))
        self.assertLess(method.index("R45_REJECTED_TARGET_OUT_OF_RANGE"),
                        method.index("npcs.sendMask("))
        self.assertLess(method.index("R45_REJECTED_CUSTOM_CAST_COOLDOWN"),
                        method.index("npcs.sendMask("))


if __name__=="__main__":
    unittest.main()
