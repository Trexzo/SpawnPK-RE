"""R12 isolated cache builder: fail-closed, identity exclusion and no old-file changes."""
from __future__ import annotations
from pathlib import Path
import tempfile
import unittest
from unittest import mock
import os

import r12_seed_isolated_v308_cache as r12


def create_cache(root: Path):
    src = root / ".spawnpk"
    src.mkdir()
    for name in r12.REQUIRED:
        p = src / name
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_bytes(b"static-cache-asset:" + name.encode("ascii"))
    (src / "raw").mkdir()
    (src / "raw" / "12000").write_bytes(b"custom-model")
    (src / "sprites" / "gameframe").mkdir(parents=True)
    (src / "sprites" / "gameframe" / "background.png").write_bytes(b"image")
    (src / "configs" / "w.bin").write_bytes(b"client-config")
    (src / "configs" / "private.yaml").write_bytes(b"do-not-copy")
    (src / "uid.dat").write_bytes(b"private-user-id")
    (src / "market_prices.yaml").write_bytes(b"private-prices")
    (src / "custom-session.properties").write_bytes(b"private-session")
    return src


class CacheSeedTest(unittest.TestCase):
    def test_selects_static_cache_and_excludes_identities(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = create_cache(root)
            private = root / "isolated-home"
            snapshot = {p.relative_to(source).as_posix(): p.read_bytes()
                        for p in source.rglob("*") if p.is_file()}
            receipt = r12.seed(source, private)
            self.assertEqual(receipt["result"], "R12_ISOLATED_V308_CACHE_SEED_PASS")
            self.assertEqual(receipt["existing_processes_stopped"], 0)
            self.assertEqual(receipt["game_processes_started"], 0)
            self.assertFalse(receipt["gui_acceptance_performed"])
            self.assertTrue((private / ".spawnpk" / "raw" / "12000").is_file())
            self.assertTrue((private / ".spawnpk" / "sprites" / "gameframe" / "background.png").is_file())
            self.assertTrue((private / ".spawnpk" / "configs" / "i.bin").is_file())
            self.assertTrue((private / ".spawnpk-data").is_dir())
            self.assertFalse((private / ".spawnpk" / "uid.dat").exists())
            self.assertFalse((private / ".spawnpk" / "market_prices.yaml").exists())
            self.assertFalse((private / ".spawnpk" / "configs" / "private.yaml").exists())
            self.assertFalse((private / ".spawnpk" / "custom-session.properties").exists())
            self.assertEqual(
                snapshot,
                {p.relative_to(source).as_posix(): p.read_bytes()
                 for p in source.rglob("*") if p.is_file()},
            )
            self.assertEqual(receipt["source_cache_file_count"], len(r12.REQUIRED) + 3)
            self.assertEqual(len(receipt["sha256_inventory"]), 64)

    def test_preexisting_output_is_never_overwritten(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = create_cache(root)
            private = root / "isolated-home"
            private.mkdir()
            keep = private / "user-keeps-this.txt"
            keep.write_text("never overwrite", encoding="utf-8")
            with self.assertRaises(r12.SeedError):
                r12.seed(source, private)
            self.assertEqual(keep.read_text(encoding="utf-8"), "never overwrite")

    def test_missing_required_cache_blocks_before_destination_created(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = create_cache(root)
            (source / "main_file_cache.dat").unlink()
            private = root / "isolated-home"
            with self.assertRaisesRegex(r12.SeedError, "required"):
                r12.seed(source, private)
            self.assertFalse(private.exists())

    def test_symlink_is_rejected_without_writing_to_target(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = create_cache(root)
            outside = root / "outside.txt"
            outside.write_bytes(b"private-outside")
            (source / "raw" / "99999").symlink_to(outside)
            private = root / "isolated-home"
            with self.assertRaises(r12.SeedError):
                r12.seed(source, private)
            self.assertFalse(private.exists())
            self.assertEqual(outside.read_bytes(), b"private-outside")

    def test_source_modification_during_copy_fails_and_own_directory_is_removed(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = create_cache(root)
            private = root / "isolated-home"
            original_sha = r12.sha256
            key = str(source / "configs" / "i.bin")
            readings = {"n": 0}
            def changing_hash(path):
                got = original_sha(path)
                if str(path) == key:
                    readings["n"] += 1
                    if readings["n"] == 2:
                        return "0" * 64
                return got
            with mock.patch.object(r12, "sha256", changing_hash):
                with self.assertRaisesRegex(r12.SeedError, "SHA mismatch"):
                    r12.seed(source, private)
            self.assertFalse(private.exists())

    def test_source_must_be_dotspawnpk_and_home_not_source(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = create_cache(root)
            with self.assertRaises(r12.SeedError):
                r12.checked_paths(source, root)
            with self.assertRaises(r12.SeedError):
                r12.checked_paths(source, source / "nested")
            with self.assertRaises(r12.SeedError):
                r12.checked_paths(root, root / "isolated-home")


if __name__ == "__main__":
    unittest.main()
