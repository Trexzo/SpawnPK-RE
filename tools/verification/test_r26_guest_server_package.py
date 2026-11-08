#!/usr/bin/env python3
"""R26 synthetic offline server smoke package tests (no proprietary assets)."""
from __future__ import annotations

import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
import r23_windows_sandbox_offline_package as r23
import r24_stage_guest_static_cache as r24
import r25_guest_home_package as r25
import r26_guest_server_package as r26


class R26OwnedGuestServerTests(unittest.TestCase):
    def fixture(self, root: Path):
        pkg = root / "spawnpk-r23-offline-testfixture"
        assets = pkg / "assets"
        assets.mkdir(parents=True)
        jdk = root / "jdk"
        jdk.mkdir()
        ws = pkg / r25.WSB_NAME
        ws.write_text(r23.wsb_xml(assets.resolve(), jdk.resolve()),
                      encoding="utf-8")
        files = (
            (r24.MANIFEST_NAME, '{"format":"synthetic-only"}'),
            (r25.GUEST_TARGET, "synthetic R25 preflight source"),
            ("SpawnPKLocalServer.jar", "not a real JAR"),
        )
        for name, data in files:
            (assets / name).write_text(data, encoding="utf-8")
        source = root / r26.GUEST_SOURCE
        source.write_text("Write-Output 'synthetic R26 script only'",
                          encoding="utf-8")
        base = {
            "result": "R25_GUEST_WRITABLE_HOME_PACKAGE_PREPARED_NO_LAUNCH",
            "package_directory": str(pkg),
            "sandbox_configuration": str(ws),
            "guest_home_created": False,
            "game_started": False,
            "static_cache_inventory_sha256": r24.R12_EXPECTED_INVENTORY,
            "static_cache_manifest_sha256":
                r23.digest(assets / r24.MANIFEST_NAME),
            "guest_home_hydration_sha256":
                r23.digest(assets / r25.GUEST_TARGET),
            "server_jar_sha256":
                r23.digest(assets / "SpawnPKLocalServer.jar"),
        }
        return base, source, jdk, assets

    def test_keeps_network_disabled_and_host_mappings_readonly(self):
        with tempfile.TemporaryDirectory(prefix="r26-test-") as d:
            base, source, jdk, assets = self.fixture(Path(d))
            receipt = r26.extend_package(base, source, jdk)
            self.assertEqual(receipt["result"],
                             "R26_OFFLINE_GUEST_LOCAL_SERVER_SMOKE_PACKAGE_PREPARED")
            self.assertFalse(receipt["guest_java_server_started"])
            self.assertFalse(receipt["guest_home_created"])
            self.assertFalse(receipt["graphical_client_started"])
            wsb = Path(receipt["sandbox_configuration"])
            xml = wsb.read_text(encoding="utf-8")
            r23.assess_wsb(xml, assets.resolve(), jdk.resolve())
            self.assertIn("<Networking>Disable</Networking>", xml)
            self.assertNotIn("LogonCommand", xml)
            self.assertEqual(receipt["wsb_sha256"], r23.digest(wsb))
            self.assertEqual(receipt["guest_server_smoke_sha256"],
                             r23.digest(assets / r26.GUEST_TARGET))
            stored = json.loads((wsb.parent / "receipt.json").read_text())
            self.assertFalse(stored["guest_server_smoke_executed"])

    def test_network_enabled_refused_before_copy(self):
        with tempfile.TemporaryDirectory(prefix="r26-test-") as d:
            base, source, jdk, assets = self.fixture(Path(d))
            ws = Path(base["sandbox_configuration"])
            ws.write_text(ws.read_text().replace(
                "<Networking>Disable</Networking>",
                "<Networking>Enable</Networking>"))
            with self.assertRaises(r23.AdmissionDenied):
                r26.extend_package(base, source, jdk)
            self.assertFalse((assets / r26.GUEST_TARGET).exists())

    def test_inherited_server_digest_tampering_refused(self):
        with tempfile.TemporaryDirectory(prefix="r26-test-") as d:
            base, source, jdk, assets = self.fixture(Path(d))
            (assets / "SpawnPKLocalServer.jar").write_text("tampered bytes")
            with self.assertRaises(r26.R26Denied):
                r26.extend_package(base, source, jdk)
            self.assertFalse((assets / r26.GUEST_TARGET).exists())

    def test_missing_hydration_or_stale_state_refused(self):
        with tempfile.TemporaryDirectory(prefix="r26-test-") as d:
            base, source, jdk, assets = self.fixture(Path(d))
            base["guest_home_created"] = True
            with self.assertRaises(r26.R26Denied):
                r26.extend_package(base, source, jdk)
            base["guest_home_created"] = False
            (assets / r25.GUEST_TARGET).unlink()
            with self.assertRaises(r26.R26Denied):
                r26.extend_package(base, source, jdk)

    def test_no_existing_output_overwritten(self):
        with tempfile.TemporaryDirectory(prefix="r26-test-") as d:
            base, source, jdk, assets = self.fixture(Path(d))
            target = assets / r26.GUEST_TARGET
            target.write_text("existing")
            with self.assertRaises(r26.R26Denied):
                r26.extend_package(base, source, jdk)
            self.assertEqual(target.read_text(), "existing")

    def test_guest_script_has_owned_process_and_shutdown_gate(self):
        text = (Path(__file__).resolve().parent /
                r26.GUEST_SOURCE).read_text(encoding="utf-8")
        for requirement in (
            "WDAGUtilityAccount",
            "guest-cache-preflight.ps1",
            "r25-private-guest-home-receipt.json",
            "RUN R26 OWNED GUEST LOCAL SERVER SMOKE",
            "RedirectStandardInput=$true",
            "stdinShutdownEnabled=true",
            "stdinShutdownToken=",
            "Get-OwnedListeners",
            "127.0.0.1:43594",
            "127.0.0.1:43595",
            "r26-owned-server-smoke-receipt.json",
            "R26_GUEST_OWNED_LOCALLAB_SERVER_LOOPBACK_SMOKE_PASS",
        ):
            self.assertIn(requirement, text)
        # Never kill/terminate arbitrary user JVMs, install firewall rule,
        # or launch graphics. The owned .Kill() cleanup is allowed.
        for forbidden in (
            "Stop-Process", "taskkill", "New-NetFirewallRule",
            "Enable-WindowsOptionalFeature", "WindowsSandbox.exe",
            "Invoke-WebRequest", "Start-Process",
        ):
            self.assertNotIn(forbidden, text)

    def test_host_side_generator_no_unapproved_execution(self):
        source = Path(r26.__file__).read_text(encoding="utf-8")
        for forbidden in (
            "WindowsSandbox.exe", "subprocess.Popen", "os.system(",
            "shell=True", "java -jar", "New-NetFirewallRule",
        ):
            self.assertNotIn(forbidden, source)


if __name__ == "__main__":
    unittest.main()
