#!/usr/bin/env python3
"""R48 source-bound negative controls for World-owned lethal-spell preflight.

The end-to-end real-JVM test is synthetic_v308_mortal_loot_conflict_recovery.py.
"""
from __future__ import annotations

from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[2]/"server"/"src"/"spk"/"local"


def section(filename:str,begin:str,end:str)->str:
    text=(ROOT/filename).read_text(encoding="utf-8")
    start=text.index(begin)
    return text[start:text.index(end,start)]


class R48LethalPredebitPolicyTest(unittest.TestCase):
    def test_wind_before_rune_debit_and_npc_hit(self):
        s=section("LocalWorldTickCoordinator.java",
                  "String castLocalLabMortalWindStrike(",
                  "private void tickTransientRegion(")
        preflight=s.index("LocalLabMortalNpcFixture.preflightConfirmedDamage(")
        self.assertLess(preflight,s.index("cost.consume(bank,writer)"))
        self.assertLess(preflight,s.index("npcs.sendMask("))
        self.assertLess(preflight,s.index("applyPublishedDamage("))
        self.assertLess(s.index("if(mortalConflict!=null)return mortalConflict;"),
                        s.index("cost.consume(bank,writer)"))
        self.assertIn("R47_REJECTED_STALE_OR_NONMORTAL_TARGET",s)
        self.assertIn("R47_REJECTED_SHARED_CUSTOM_MAGIC_COOLDOWN",s)

    def test_air_surge_before_hit_and_optional_xp(self):
        s=section("LocalWorldTickCoordinator.java",
                  "String castLocalLabMortalAirSurge(",
                  "String castLocalLabMortalWindStrike(")
        p=s.index("LocalLabMortalNpcFixture.preflightConfirmedDamage(")
        self.assertLess(p,s.index("LocalLabMortalMagicExperience.preflight("))
        self.assertLess(p,s.index("npcs.sendMask("))
        self.assertLess(p,s.index("awardPublishedDamage("))
        self.assertIn("if(mortalConflict!=null)return mortalConflict;",s)

    def test_canonical_mortal_does_not_set_hp_zero_before_preflight(self):
        s=section("LocalLabMortalNpcFixture.java",
                  "static String applyPublishedDamage(",
                  "private LocalLabMortalNpcFixture()")
        self.assertLess(
            s.index("String conflict=preflightConfirmedDamage("),
            s.index("npc.applyLocalLabMortalDamage(damage)"))
        self.assertIn("if(conflict!=null)",s)

    def test_readonly_preflight_rejects_known_loot_scene_and_respawn_conflicts(self):
        s=section("LocalLabMortalNpcFixture.java",
                  "static String preflightConfirmedDamage(",
                  "static String applyPublishedDamage(")
        for marker in (
            "npc.localLabMortalHp()<=0",
            "npcs.scene(npc.sceneIndex)!=npc",
            "R48_REJECTED_NO_LOOT_OWNER",
            "npcs.canRemoveDevOwnedNpc(npc)",
            "R48_REJECTED_OWNER_LOOT_TILE_OCCUPIED",
            "world.groundItems().findOwned(",
            "npcs.canScheduleLocalLabMortalDeath(worldTick)",
            "R48_REJECTED_R7_RESPAWN_UNSCHEDULABLE",
            "if(damage<npc.localLabMortalHp())",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker,s)
        for forbidden in (
            "sendMask(", "writer.", "applyLocalLabMortalDamage(",
            "groundItems().add(", "publishCompletedDeath(",
            "consumeInventoryAmountSemantic(", "scheduleLocalLabMortalRespawn(",
        ):
            with self.subTest(forbidden=forbidden):
                self.assertNotIn(forbidden,s)
        registry=(ROOT/"NpcRegistry.java").read_text(encoding="utf-8")
        self.assertIn("boolean canRemoveDevOwnedNpc(NpcEntity npc)",registry)
        self.assertIn("boolean canScheduleLocalLabMortalDeath(long worldTick)",registry)
        self.assertIn("devOwnedSceneIndexes.contains(npc.sceneIndex)",registry)


if __name__=="__main__":
    unittest.main()
