"""R4 profile isolation and exact snapshot contract unit checks."""
from pathlib import Path
import tempfile
import unittest

from synthetic_persistence_reconnect import ReconnectFailure, require_snapshot, snapshot_slots
from synthetic_revision317_login import login_body


class FreshJvmPersistenceFixtureTests(unittest.TestCase):
    def test_persistent_alias_is_explicit_not_previous_nonpersistent_local(self):
        frame, seeds = login_body(0x0123456789ABCDEF, username="localtest")
        self.assertIn(b"localtest\nlocalpass\n", frame)
        self.assertNotIn(b"\x0alocal\nlocalpass", frame)
        self.assertEqual(len(seeds), 4)
        with self.assertRaises(ValueError):
            login_body(1, username="../../real-profile")

    def test_net_change_is_checked_in_real_file_not_just_logs(self):
        with tempfile.TemporaryDirectory() as d:
            target = Path(d) / "opensrc.properties"
            target.write_text(
                "# isolated fixture\nbank.0=995,99999,0\n"
                "inventory.0=995,1,0\nsaved.at=2026-01-01\n",
                encoding="ascii",
            )
            self.assertEqual(
                snapshot_slots(target),
                {"bank.0": "995,99999,0", "inventory.0": "995,1,0"},
            )
            self.assertEqual(len(require_snapshot(target, 99999, 1)), 64)
            with self.assertRaises(ReconnectFailure):
                require_snapshot(target, 100000, 0)
            target.write_text("bank.0=995,99999,0\ninventory.0=\n", encoding="ascii")
            with self.assertRaises(ReconnectFailure):
                require_snapshot(target, 99999, 1)

    def test_duplicate_or_missing_snapshot_keys_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            target = Path(d) / "opensrc.properties"
            target.write_text("bank.0=995,99999,0\n", encoding="ascii")
            with self.assertRaises(ReconnectFailure):
                snapshot_slots(target)
            target.write_text(
                "bank.0=995,99999,0\nbank.0=995,1,0\ninventory.0=995,1,0\n",
                encoding="ascii",
            )
            with self.assertRaises(ReconnectFailure):
                snapshot_slots(target)


if __name__ == "__main__":
    unittest.main()
