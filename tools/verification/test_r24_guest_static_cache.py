#!/usr/bin/env python3
"""R24 synthetic cache/no-launch package regressions; no client JAR in CI."""
from __future__ import annotations

from contextlib import contextmanager
import hashlib
import json
from pathlib import Path
import shutil
import tempfile
import unittest
from unittest import mock

import sys
sys.path.insert(0, str(Path(__file__).resolve().parent))
import r12_seed_isolated_v308_cache as r12
import r23_windows_sandbox_offline_package as r23
import r24_stage_guest_static_cache as r24


@contextmanager
def fake_r12_source():
    with tempfile.TemporaryDirectory(
            prefix="SpawnPK-R12-IsolatedV308-fixture-",
            dir=tempfile.gettempdir()) as folder:
        home = Path(folder)
        cache = home / ".spawnpk"
        cache.mkdir()
        (home / ".spawnpk-data").mkdir()
        for name in sorted(r12.REQUIRED):
            file = cache.joinpath(*name.split("/"))
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_bytes(("fixture:" + name).encode("ascii"))
        selected = r12.enumerate_assets(cache)
        rows = [(p, size, r12.sha256(path))
                for p, path, size in selected]
        total = sum(size for _, size, _ in rows)
        pin = r24.inventory(rows)
        record = {
            "result": "R12_ISOLATED_V308_CACHE_SEED_PASS",
            "isolated_home": str(home.resolve()),
            "source_cache_file_count": len(rows),
            "copied_bytes": total,
            "sha256_inventory": pin,
            "user_identity_copied": False,
            "existing_processes_stopped": 0,
            "game_processes_started": 0,
        }
        (home / "r12-cache-seed-receipt.json").write_text(
            json.dumps(record), encoding="utf-8")
        with mock.patch.object(r24, "R12_EXPECTED_COUNT", len(rows)), \
             mock.patch.object(r24, "R12_EXPECTED_BYTES", total), \
             mock.patch.object(r24, "R12_EXPECTED_INVENTORY", pin):
            yield home, selected, rows


class R24StaticCacheTests(unittest.TestCase):
    def test_sealed_R12_receipt_and_byte_inventory_accept(self):
        with fake_r12_source() as (home, selected, _):
            actual, files = r24.validate_r12_home(home)
            self.assertEqual(actual, home.resolve())
            self.assertEqual(len(files), len(selected))

    def test_identity_uid_file_rejected_even_if_R12_would_skip_it(self):
        with fake_r12_source() as (home, _, _):
            (home / ".spawnpk" / "uid.dat").write_bytes(b"NOT REAL DATA")
            with self.assertRaises(r24.CacheAdmissionDenied):
                r24.validate_r12_home(home)

    def test_existing_user_data_directory_rejected(self):
        with fake_r12_source() as (home, _, _):
            (home / ".spawnpk-data" / "user-state.properties").write_text("x")
            with self.assertRaises(r24.CacheAdmissionDenied):
                r24.validate_r12_home(home)

    def test_tampered_binary_rejected_without_receipt_change(self):
        with fake_r12_source() as (home, _, _):
            file = home / ".spawnpk" / "configs" / "i.bin"
            file.write_bytes(file.read_bytes() + b"malicious")
            with self.assertRaises(r24.CacheAdmissionDenied):
                r24.validate_r12_home(home)

    def test_forged_receipt_digest_rejected(self):
        with fake_r12_source() as (home, _, _):
            file = home / "r12-cache-seed-receipt.json"
            record = json.loads(file.read_text())
            record["sha256_inventory"] = "f" * 64
            file.write_text(json.dumps(record))
            with self.assertRaises(r24.CacheAdmissionDenied):
                r24.validate_r12_home(home)

    def test_guest_script_checks_network_first_and_no_game_launch(self):
        guest = (Path(__file__).resolve().parent /
                 r24.GUEST_SCRIPT).read_text(encoding="utf-8")
        self.assertIn("WDAGUtilityAccount", guest)
        self.assertIn("guest-preflight.ps1", guest)
        self.assertIn("Get-FileHash", guest)
        self.assertIn("R24_GUEST_R12_STATIC_CACHE_10764_SHA256_PASS", guest)
        for forbidden in (
            "Start-Process", "java -jar", "javaw.exe -jar",
            "New-NetFirewallRule", "Enable-WindowsOptionalFeature",
            "Invoke-WebRequest",
        ):
            self.assertNotIn(forbidden, guest)

    def test_staging_keeps_only_readonly_R23_maps_and_no_gui(self):
        with fake_r12_source() as (home, files, _):
            with tempfile.TemporaryDirectory(prefix="spk-r24-synthetic-") as root:
                folder = Path(root)
                repo = folder / "repo"
                tool_dir = repo / "tools" / "verification"
                tool_dir.mkdir(parents=True)
                shutil.copyfile(
                    Path(__file__).resolve().parent / r24.GUEST_SCRIPT,
                    tool_dir / r24.GUEST_SCRIPT,
                )
                (tool_dir / "r23_guest_preflight.ps1").write_text("exit 2")
                server = folder / "server.jar"
                server.write_bytes(b"synthetic server not run")
                client = folder / "client.jar"
                client.write_bytes(b"synthetic airgap not run")
                jdk = folder / "jdk"
                jdk.mkdir()
                server_sha = hashlib.sha256(server.read_bytes()).hexdigest()
                values = {
                    "repo": repo, "head": "a" * 40,
                    "server": server, "server_sha": server_sha,
                    "airgap": client, "jdk": jdk, "parent": folder,
                    "guest_script": tool_dir / "r23_guest_preflight.ps1",
                }
                real_digest = r23.digest

                def synthetic_pin(path):
                    if path.name == "client-airgap.jar":
                        return r23.AIRGAP_SHA
                    return real_digest(path)

                with mock.patch.object(r23, "digest", side_effect=synthetic_pin):
                    receipt = r24.append_static_cache(values, home, files)
                self.assertEqual(
                    receipt["result"],
                    "R24_STATIC_GAME_CACHE_SANDBOX_PACKAGE_PREPARED_NO_LAUNCH")
                self.assertFalse(receipt["game_started"])
                self.assertFalse(receipt["sandbox_started"])
                self.assertFalse(
                    receipt["opaque_binary_content_privacy_certified"])
                work = Path(receipt["package_directory"])
                manifest = json.loads(
                    (work / "assets" / r24.MANIFEST_NAME).read_text())
                self.assertEqual(
                    manifest["inventory_sha256"], r24.R12_EXPECTED_INVENTORY)
                self.assertEqual(
                    len(manifest["file_entries"]), len(files))
                self.assertTrue(
                    (work / "assets" / "guest-cache-preflight.ps1").is_file())
                wsb = work / "SpawnPK-R24-NetworkDisabled-StaticCache-REVIEW-ONLY.wsb"
                xml = wsb.read_text(encoding="utf-8")
                r23.assess_wsb(xml, work / "assets", jdk)
                self.assertNotIn("<LogonCommand>", xml)
                self.assertIn("<Networking>Disable</Networking>", xml)
                self.assertFalse(receipt["real_user_profile_or_live_cache_mapped"])
                self.assertNotIn("user_profile_or_cache_mapped", receipt)
                self.assertEqual(
                    receipt["wsb_sha256"], r23.digest(wsb))
                self.assertEqual(
                    receipt["static_cache_manifest_sha256"],
                    r23.digest(work / "assets" / r24.MANIFEST_NAME))

    def test_source_does_not_spawn_game_or_mutate_firewall(self):
        source = Path(r24.__file__).read_text(encoding="utf-8")
        for token in (
            "Start-Process", "WindowsSandbox.exe", "New-NetFirewallRule",
            "subprocess.Popen", "os.system(", "java -jar",
            "shell=True",
        ):
            self.assertNotIn(token, source)


if __name__ == "__main__":
    unittest.main()
