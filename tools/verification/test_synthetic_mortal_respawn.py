"""Pinned R7 multi-hit lifecycle wire and fixture isolation regressions."""
from pathlib import Path
import tempfile
import unittest

from synthetic_mortal_respawn import (
    OPTIONS, await_new_generation, find_initial_actor,
)
from synthetic_persistence_reconnect import ReconnectFailure, start_server


class MortalRespawnFixtureTests(unittest.TestCase):
    def test_two_vm_flags_exact(self):
        self.assertEqual(
            OPTIONS,
            (
                "-Dspk.local.r6MortalFixture=true",
                "-Dspk.local.r7LifecycleFixture=true",
            ),
        )
        with tempfile.TemporaryDirectory() as dirname:
            home = Path(dirname)
            for opts in (
                ("-Dspk.local.r7LifecycleFixture=true",),
                ("-Dspk.local.r6MortalFixture=true",)*2,
                ("-Dspk.local.r6MortalFixture=true", "-Dspk.local.foo=true"),
            ):
                with self.assertRaises(ReconnectFailure):
                    start_server(
                        "java", home / "does-not-exist.jar",
                        home, home / "account", "test",
                        fixture_options=opts,
                    )

    def test_first_spawn_hp25_and_east_home(self):
        with tempfile.TemporaryDirectory() as dirname:
            log = Path(dirname) / "test.log"
            log.write_text(
                "R6_MORTAL_NPC_BOOTSTRAP scene=16382 def=1 "
                "world=3088,3495 hp=25 loot=995x1 "
                "authority=CUSTOM_LOCALLAB_OPT_IN_FIXTURE\n",
                encoding="ascii",
            )
            self.assertEqual(find_initial_actor(log), (16382, 3088, 3495))

    def test_nonlethal_HP_contract_is_not_10HP_fixture(self):
        with tempfile.TemporaryDirectory() as dirname:
            log = Path(dirname) / "test.log"
            log.write_text(
                "R6_MORTAL_NPC_BOOTSTRAP scene=16382 def=1 "
                "world=3088,3495 hp=10 loot=995x1\n",
                encoding="ascii",
            )
            with self.assertRaises(ReconnectFailure):
                find_initial_actor(log)


if __name__ == "__main__":
    unittest.main()
