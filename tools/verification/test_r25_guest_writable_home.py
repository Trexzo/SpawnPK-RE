#!/usr/bin/env python3
"""R25 guest-only writable HOME package; synthetic CI regression fixtures."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent))
import r23_windows_sandbox_offline_package as r23
import r24_stage_guest_static_cache as r24
import r25_guest_home_package as r25


class R25GuestWritableHomeTests(unittest.TestCase):
    def new_fixture(self, root: Path):
        owned = root / "spawnpk-r23-offline-test-owned"
        owned.mkdir()
        assets = owned / "assets"
        assets.mkdir()
        jdk = root / "private-jdk"
        jdk.mkdir()
        wsb = owned / "SpawnPK-R24-NetworkDisabled-StaticCache-REVIEW-ONLY.wsb"
        wsb.write_text(r23.wsb_xml(assets.resolve(), jdk.resolve()),
                       encoding="utf8")
        manifest = assets / r24.MANIFEST_NAME
        manifest.write_text(
            json.dumps({"format": "synthetic-r24-no-real-cache"}),
            encoding="utf8")
        base = {
            "result": "R24_STATIC_GAME_CACHE_SANDBOX_PACKAGE_PREPARED_NO_LAUNCH",
            "package_directory": str(owned),
            "sandbox_configuration": str(wsb),
            "static_cache_inventory_sha256": r24.R12_EXPECTED_INVENTORY,
            "static_cache_manifest_sha256": r23.digest(manifest),
            "user_profile_or_cache_mapped": False,
            "sandbox_started": False,
            "game_started": False,
        }
        source = root / r25.GUEST_SOURCE
        source.write_text(
            "Write-Output 'R25 SYNTHETIC not a client launcher'",
            encoding="utf8",
        )
        return base, source, jdk, assets

    def test_extends_R24_without_additional_host_mappings(self):
        with tempfile.TemporaryDirectory(prefix="r25-fixture-") as folder:
            base, source, jdk, assets = self.new_fixture(Path(folder))
            receipt = r25.extend_package(base, source, jdk)
            self.assertEqual(
                receipt["result"],
                "R25_GUEST_WRITABLE_HOME_PACKAGE_PREPARED_NO_LAUNCH")
            self.assertFalse(receipt["sandbox_started"])
            self.assertFalse(receipt["game_started"])
            self.assertFalse(receipt["guest_home_created"])
            self.assertFalse(receipt["host_profile_or_live_cache_mapped"])
            self.assertFalse(receipt["guest_home_hydration_executed"])
            self.assertNotIn("user_profile_or_cache_mapped", receipt)
            wsb = Path(receipt["sandbox_configuration"])
            self.assertEqual(wsb.name, r25.WSB_NAME)
            xml = wsb.read_text(encoding="utf8")
            r23.assess_wsb(xml, assets.resolve(), jdk.resolve())
            self.assertNotIn("<LogonCommand>", xml)
            self.assertIn("<Networking>Disable</Networking>", xml)
            self.assertEqual(receipt["wsb_sha256"], r23.digest(wsb))
            self.assertEqual(
                receipt["guest_home_hydration_sha256"],
                r23.digest(assets / r25.GUEST_TARGET))
            self.assertEqual(
                json.loads((wsb.parent / "receipt.json").read_text())["result"],
                receipt["result"])

    def test_denies_modified_R24_manifest_without_guest_copy(self):
        with tempfile.TemporaryDirectory(prefix="r25-fixture-") as folder:
            base, source, jdk, assets = self.new_fixture(Path(folder))
            (assets / r24.MANIFEST_NAME).write_text(
                "tampered receipt content", encoding="utf8")
            with self.assertRaises(r25.R25Denied):
                r25.extend_package(base, source, jdk)
            self.assertFalse((assets / r25.GUEST_TARGET).exists())

    def test_denies_network_enabled_before_copy(self):
        with tempfile.TemporaryDirectory(prefix="r25-fixture-") as folder:
            base, source, jdk, assets = self.new_fixture(Path(folder))
            wsb = Path(base["sandbox_configuration"])
            wsb.write_text(
                wsb.read_text().replace(
                    "<Networking>Disable</Networking>",
                    "<Networking>Enable</Networking>"),
                encoding="utf8")
            with self.assertRaises(r23.AdmissionDenied):
                r25.extend_package(base, source, jdk)
            self.assertFalse((assets / r25.GUEST_TARGET).exists())

    def test_denies_existing_target_instead_of_overwrite(self):
        with tempfile.TemporaryDirectory(prefix="r25-fixture-") as folder:
            base, source, jdk, assets = self.new_fixture(Path(folder))
            (assets / r25.GUEST_TARGET).write_text("existing")
            with self.assertRaises(r25.R25Denied):
                r25.extend_package(base, source, jdk)
            self.assertEqual(
                (assets / r25.GUEST_TARGET).read_text(), "existing")

    def test_denies_missing_guest_script_and_wrong_R24_receipt(self):
        with tempfile.TemporaryDirectory(prefix="r25-fixture-") as folder:
            base, source, jdk, assets = self.new_fixture(Path(folder))
            source.unlink()
            with self.assertRaises(r25.R25Denied):
                r25.extend_package(base, source, jdk)
            source.write_text("test")
            base["result"] = "R24_NOT_COMPLETE"
            with self.assertRaises(r25.R25Denied):
                r25.extend_package(base, source, jdk)

    def test_guest_script_requires_manual_approval_and_R24_first(self):
        text = (Path(__file__).resolve().parent /
                r25.GUEST_SOURCE).read_text(encoding="utf8")
        self.assertIn("WDAGUtilityAccount", text)
        self.assertIn("guest-cache-preflight.ps1", text)
        self.assertIn("if (-not $Hydrate)", text)
        self.assertIn("CREATE R25 PRIVATE GUEST HOME", text)
        self.assertIn("Get-FileHash", text)
        self.assertIn("R25_GUEST_PRIVATE_WRITABLE_HOME_10764_SHA256_PASS",
                      text)
        self.assertIn("opaque_binary_content_privacy_certified = $false",
                      text)
        self.assertLess(text.index("guest-cache-preflight.ps1"),
                        text.index("[IO.Directory]::CreateDirectory($root)"))
        self.assertNotIn("java -jar", text.lower())
        for forbidden in (
            "Start-Process", "taskkill", "Stop-Process",
            "New-NetFirewallRule", "Enable-WindowsOptionalFeature",
            "WindowsSandbox.exe", "Invoke-WebRequest", "curl.exe",
        ):
            self.assertNotIn(forbidden, text)

    def test_package_source_never_invokes_guest_or_host_game(self):
        source = Path(r25.__file__).read_text(encoding="utf8")
        for forbidden in (
            "WindowsSandbox.exe", "subprocess.Popen",
            "shell=True", "os.system(", "java -jar",
            "New-NetFirewallRule",
        ):
            self.assertNotIn(forbidden, source)


if __name__ == "__main__":
    unittest.main()
