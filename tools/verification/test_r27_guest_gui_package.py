#!/usr/bin/env python3
"""R27 no-GUI synthetic guest-package and guest-launch policy tests."""
from __future__ import annotations

import json
from pathlib import Path
import sys
import tempfile
import unittest
from copy import deepcopy

sys.path.insert(0, str(Path(__file__).resolve().parent))
import r23_windows_sandbox_offline_package as r23
import r24_stage_guest_static_cache as r24
import r25_guest_home_package as r25
import r26_guest_server_package as r26
import r27_guest_gui_package as r27

GOOD_AUDIT = {
    "result": "R27_PINNED_V308_REAL_GUI_BOOTSTRAP_STATIC_AUDIT_PASS",
    "client_gui_started": False,
    "certified_airgap_v308_sha256":
        "024fad774453430bb964076b98d460a6dae821ee31100322d104e30d9a9c97a7",
    "main_class": "rs.gui.Launcher",
}


class R27GuestGuiPackageTests(unittest.TestCase):
    def fixture(self, root: Path):
        pkg = root / "spawnpk-r23-offline-synthetic"
        assets = pkg / "assets"
        assets.mkdir(parents=True)
        jdk = root / "private-jdk"
        jdk.mkdir()
        old = pkg / r26.WSB_NAME
        old.write_text(r23.wsb_xml(assets, jdk), encoding="utf8")
        for name in (r24.MANIFEST_NAME, r25.GUEST_TARGET,
                     r26.GUEST_TARGET, "SpawnPKLocalServer.jar"):
            (assets / name).write_text("synthetic-only:" + name)
        # R27 pin check must not be bypassed for a changed client:
        # patch digest only in fixture, never in production.
        client = assets / "client-airgap.jar"
        client.write_text("synthetic pinned client bytes")
        base = {
            "result": "R26_OFFLINE_GUEST_LOCAL_SERVER_SMOKE_PACKAGE_PREPARED",
            "package_directory": str(pkg),
            "sandbox_configuration": str(old),
            "guest_java_server_started": False,
            "guest_home_created": False,
            "static_cache_inventory_sha256": r24.R12_EXPECTED_INVENTORY,
            "static_cache_manifest_sha256":
                r23.digest(assets / r24.MANIFEST_NAME),
            "guest_home_hydration_sha256":
                r23.digest(assets / r25.GUEST_TARGET),
            "guest_server_smoke_sha256":
                r23.digest(assets / r26.GUEST_TARGET),
            "server_jar_sha256":
                r23.digest(assets / "SpawnPKLocalServer.jar"),
        }
        source = root / "r27_guest_gui_window_smoke.ps1"
        source.write_text("Write-Output 'fixture no execution'", encoding="utf8")
        return base, source, jdk, assets

    def test_offline_package_keeps_two_readonly_maps_and_no_autostart(self):
        from unittest import mock
        with tempfile.TemporaryDirectory(prefix="r27-pkg-test-") as td:
            base, script, jdk, assets = self.fixture(Path(td))
            clienthash = r23.digest(assets / "client-airgap.jar")
            audit = {**GOOD_AUDIT, "certified_airgap_v308_sha256": clienthash}
            with mock.patch.object(r27.r27, "AIRGAP_SHA", clienthash):
                record = r27.extend_package(base, script, jdk, audit)
            self.assertEqual(
                record["result"],
                "R27_OFFLINE_V308_GUI_WINDOW_TRIAL_PACKAGE_PREPARED_NO_LAUNCH")
            self.assertFalse(record["graphical_client_started"])
            self.assertFalse(record["guest_gui_trial_executed"])
            self.assertFalse(record["guest_network_isolation_verified"])
            wsb = Path(record["sandbox_configuration"])
            r23.assess_wsb(wsb.read_text(), assets.resolve(), jdk.resolve())
            self.assertIn("<Networking>Disable</Networking>",
                          wsb.read_text())
            self.assertNotIn("<LogonCommand>", wsb.read_text())
            self.assertEqual(record["wsb_sha256"], r23.digest(wsb))
            self.assertEqual(record["gui_bootstrap_audit_sha256"],
                             r23.digest(assets / r27.AUDIT_TARGET))
            self.assertEqual(record["guest_gui_script_sha256"],
                             r23.digest(assets / r27.GUEST_TARGET))
            persisted = json.loads((wsb.parent / "receipt.json").read_text())
            self.assertFalse(persisted["opengl_rendering_verified"])

    def test_refuses_enabled_network_before_copy(self):
        with tempfile.TemporaryDirectory(prefix="r27-pkg-test-") as td:
            base, script, jdk, assets = self.fixture(Path(td))
            wsb = Path(base["sandbox_configuration"])
            wsb.write_text(wsb.read_text().replace(
                "<Networking>Disable</Networking>",
                "<Networking>Enable</Networking>"))
            with self.assertRaises(r23.AdmissionDenied):
                r27.extend_package(base, script, jdk, GOOD_AUDIT)
            self.assertFalse((assets / r27.GUEST_TARGET).exists())

    def test_refuses_modified_server_and_stale_r26(self):
        with tempfile.TemporaryDirectory(prefix="r27-pkg-test-") as td:
            base, script, jdk, assets = self.fixture(Path(td))
            (assets / "SpawnPKLocalServer.jar").write_text("changed")
            with self.assertRaises(r27.R27PackageDenied):
                r27.extend_package(base, script, jdk, GOOD_AUDIT)
            self.assertFalse((assets / r27.GUEST_TARGET).exists())
            base["result"] = "NOT_R26"
            with self.assertRaises(r27.R27PackageDenied):
                r27.extend_package(base, script, jdk, GOOD_AUDIT)

    def test_refuses_missing_audit_or_claimed_runtime_success(self):
        with tempfile.TemporaryDirectory(prefix="r27-pkg-test-") as td:
            base, script, jdk, assets = self.fixture(Path(td))
            with self.assertRaises(r27.R27PackageDenied):
                r27.extend_package(base, script, jdk, {})
            audit = deepcopy(GOOD_AUDIT)
            audit["client_gui_started"] = True
            with self.assertRaises(r27.R27PackageDenied):
                r27.extend_package(base, script, jdk, audit)

    def test_guest_script_is_guest_only_and_manual_client_observation(self):
        script = (Path(__file__).resolve().parent /
                  r27.GUEST_SOURCE).read_text(encoding="utf8")
        for required in (
            "WDAGUtilityAccount", "r25-private-guest-home-receipt.json",
            "r26-owned-server-smoke-receipt.json",
            "guest-cache-preflight.ps1",
            "LAUNCH R27 GUEST V308 GUI WITH LOCAL SERVER",
            "if(-not $Launch)",
            "stdinShutdownEnabled=true", "RedirectStandardInput=$pipeStdin",
            "MainWindowHandle", "CloseMainWindow", "Port-Free",
            "java.awt.headless=false", "client-airgap.jar",
            "R27_GUEST_GUI_WINDOW_AND_OWNED_PROCESS_CLEANUP_PASS",
        ):
            self.assertIn(required, script)
        import re
        # PowerShell's built-in $HOME and $PID are readonly; assign to
        # dedicated variables in manual guest scripts instead.
        self.assertIsNone(re.search(r"\\$home\\b", script, re.IGNORECASE))
        self.assertIsNone(re.search(r"\\$pid\\b", script, re.IGNORECASE))
        for disallowed in (
            "Stop-Process", "taskkill", "New-NetFirewallRule",
            "Enable-WindowsOptionalFeature", "Invoke-WebRequest",
            "WindowsSandbox.exe", "curl.exe",
        ):
            self.assertNotIn(disallowed, script)

    def test_packager_never_starts_guest_or_java(self):
        script = Path(r27.__file__).read_text(encoding="utf8")
        for disallowed in (
            "WindowsSandbox.exe", "subprocess.Popen", "os.system(",
            "shell=True", "Start-Process", "New-NetFirewallRule",
        ):
            self.assertNotIn(disallowed, script)


if __name__ == "__main__":
    unittest.main()
