"""R8 exact client S2C134 attack-XP codec and guard tests."""
import tempfile
from pathlib import Path
import unittest

from synthetic_mortal_respawn import (
    AttackXpProbe, DEFAULT_ATTACK_XP, decode_skill134,
    require_persisted_xp, R8_OPTION,
)
from synthetic_persistence_reconnect import ReconnectFailure, start_server


def skill_wire(skill, xp, level=99):
    return bytes([
        skill, (xp >> 8) & 255, xp & 255,
        (xp >> 24) & 255, (xp >> 16) & 255,
        level,
    ])


class PvmXpWireTests(unittest.TestCase):
    def test_exact_skill134_endianness_and_levels(self):
        payload = skill_wire(0, DEFAULT_ATTACK_XP + 10)
        self.assertEqual(
            decode_skill134(payload),
            (0, DEFAULT_ATTACK_XP + 10, 99),
        )
        with self.assertRaises(ReconnectFailure):
            decode_skill134(payload[:-1])

    def test_progression_and_no_wrong_skill(self):
        p = AttackXpProbe()
        p.observe((134, skill_wire(0, DEFAULT_ATTACK_XP)))
        for step in range(1, 7):
            p.observe((134, skill_wire(0, DEFAULT_ATTACK_XP + 10 * step)))
        p.observe((134, skill_wire(1, 20)))
        p.require_progression(1, (1, 2, 3, 4, 5, 6))
        with self.assertRaises(ReconnectFailure):
            p.require_progression(4, (4, 5, 7))
        with self.assertRaises(ReconnectFailure):
            p.observe((134, skill_wire(0, 123, 1)))

    def test_snapshot_xp_exact_single_key(self):
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / "account.properties"
            path.write_text(
                "skill.0.xp=13034491\nskill.1.xp=13034431\n",
                encoding="iso-8859-1",
            )
            require_persisted_xp(path, DEFAULT_ATTACK_XP + 60)
            with self.assertRaises(ReconnectFailure):
                require_persisted_xp(path, DEFAULT_ATTACK_XP + 50)

    def test_xp_flag_fails_closed_without_both_prerequisites(self):
        with tempfile.TemporaryDirectory() as d:
            home = Path(d)
            for opts in (
                (R8_OPTION,),
                (R8_OPTION, "-Dspk.local.r6MortalFixture=true"),
                (R8_OPTION, "-Dspk.local.r7LifecycleFixture=true"),
                (R8_OPTION,)*2,
            ):
                with self.assertRaises(ReconnectFailure):
                    start_server(
                        "java", home / "dummy.jar",
                        home, home / "account.properties",
                        "flag-invalid", fixture_options=opts,
                    )


if __name__=="__main__":
    unittest.main()
