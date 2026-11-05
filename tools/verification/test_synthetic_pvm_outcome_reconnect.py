"""R9 typed PvM kill outcome log contract and generation-proof tests."""
from pathlib import Path
import tempfile
import unittest

from synthetic_mortal_respawn import verify_outcome_log
from synthetic_persistence_reconnect import ReconnectFailure

AUTH="CUSTOM_LOCALLAB_PVM_KILL_AFTER_REMOVED_LOOT_V1"


def line(scene,tick):
    return (
        "[session] R9_PVM_KILL_OUTCOME_EMITTED type=NPC_KILL "
        "context=NPC_PVM attacker=EntityId{type=PLAYER,id=2} "
        f"victim=locallab:npc:def=1:scene={scene} "
        f"scene={scene} def=1 worldTick={tick} "
        f"delivery=WORLD_DOMAIN_BUS authority={AUTH} "
        "originalSpawnpkKillCount=false"
    )


class OutcomeLogContractTest(unittest.TestCase):
    def test_only_two_distinct_kill_generations(self):
        with tempfile.TemporaryDirectory() as home:
            log=Path(home)/"test.log"
            log.write_text(
                line(16382,8)+"\n"+line(16381,15)+"\n",
                encoding="ascii",
            )
            self.assertEqual(verify_outcome_log(log,[16382,16381]),[8,15])

    def test_stale_scene_replay_and_duplicates_rejected(self):
        with tempfile.TemporaryDirectory() as home:
            log=Path(home)/"test.log"
            for malformed in (
                [line(16382,8),line(16382,15)],
                [line(16382,8),line(16381,15),line(16381,16)],
                [line(16382,8)],
                [line(16382,8),line(16381,7)],
                [line(16382,8),line(16381,8)],
                [line(16382,8),line(16380,15)],
            ):
                with self.subTest(malformed=malformed):
                    log.write_text("\n".join(malformed)+"\n",encoding="ascii")
                    with self.assertRaises(ReconnectFailure):
                        verify_outcome_log(log,[16382,16381])

    def test_failed_observer_cannot_be_reported_as_success(self):
        with tempfile.TemporaryDirectory() as home:
            log=Path(home)/"test.log"
            log.write_text(
                line(16382,8)+"\n"+
                "R9_PVM_KILL_OBSERVER_FAILURE_ISOLATED scene=16381\n"+
                line(16381,15)+"\n",encoding="ascii"
            )
            with self.assertRaises(ReconnectFailure):
                verify_outcome_log(log,[16382,16381])


if __name__=="__main__":
    unittest.main()
