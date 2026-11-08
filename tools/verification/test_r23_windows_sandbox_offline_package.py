#!/usr/bin/env python3
"""R23 static guest/host sandbox package and fail-closed admission tests."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock
import xml.etree.ElementTree as ET

sys.path.insert(0, str(Path(__file__).resolve().parent))
import r23_windows_sandbox_offline_package as r23


class R23OfflineSandboxPackageTests(unittest.TestCase):
    def test_wsb_disables_network_and_auto_execution(self):
        with tempfile.TemporaryDirectory() as root:
            parent = Path(root)
            assets = parent / "assets"
            jdk = parent / "jdk"
            xml = r23.wsb_xml(assets, jdk)
            r23.assess_wsb(xml, assets, jdk)
            tree = ET.fromstring(xml)
            self.assertEqual(tree.findtext("Networking"), "Disable")
            self.assertEqual(tree.findtext("ProtectedClient"), "Enable")
            self.assertEqual(tree.findtext("ClipboardRedirection"), "Disable")
            self.assertIsNone(tree.find("LogonCommand"))
            self.assertEqual(
                [n.text for n in tree.findall(".//ReadOnly")],
                ["true", "true"])
            self.assertEqual(
                [n.text for n in tree.findall(".//SandboxFolder")],
                [r23.GUEST_ASSETS, r23.GUEST_JDK])

    def test_wsb_rejects_enabled_network_even_with_readonly_maps(self):
        with tempfile.TemporaryDirectory() as root:
            home = Path(root)
            xml = r23.wsb_xml(home / "assets", home / "jdk")
            with self.assertRaises(r23.AdmissionDenied):
                r23.assess_wsb(xml.replace(
                    "<Networking>Disable</Networking>",
                    "<Networking>Enable</Networking>"),
                    home / "assets", home / "jdk")

    def test_wsb_rejects_writable_host_mapping(self):
        with tempfile.TemporaryDirectory() as root:
            home = Path(root)
            xml = r23.wsb_xml(home / "assets", home / "jdk")
            with self.assertRaises(r23.AdmissionDenied):
                r23.assess_wsb(xml.replace(
                    "<ReadOnly>true</ReadOnly>",
                    "<ReadOnly>false</ReadOnly>", 1),
                    home / "assets", home / "jdk")

    def test_wsb_rejects_logon_command_and_extra_mappings(self):
        with tempfile.TemporaryDirectory() as root:
            home = Path(root)
            xml = r23.wsb_xml(home / "assets", home / "jdk")
            xml = xml.replace("</Configuration>",
                              "<LogonCommand><Command>calc.exe</Command>"
                              "</LogonCommand></Configuration>")
            with self.assertRaises(r23.AdmissionDenied):
                r23.assess_wsb(xml, home / "assets", home / "jdk")

    def test_hash_rejects_wrong_airgap_bytes(self):
        with tempfile.TemporaryDirectory() as root:
            fake = Path(root) / "client.jar"
            fake.write_bytes(b"wrong client content")
            with self.assertRaises(r23.AdmissionDenied):
                r23.validated_file(fake.resolve(), r23.AIRGAP_SHA,
                                   "certified airgap")

    def test_stage_copies_only_two_jars_and_guest_preflight(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            server = root / "test-server.jar"
            server.write_bytes(b"synthetic server fixture")
            client = root / "test-client.jar"
            client.write_bytes(b"synthetic airgap fixture")
            guest = root / "guest.ps1"
            guest.write_text("Write-Host 'test fixture'", encoding="utf8")
            jdk = root / "jdk"
            jdk.mkdir()
            server_sha = hashlib.sha256(server.read_bytes()).hexdigest()
            values = {
                "repo": root, "head": "a" * 40, "server": server,
                "server_sha": server_sha, "airgap": client,
                "jdk": jdk, "parent": root, "guest_script": guest,
            }
            actual_digest = r23.digest

            def fake_digest(path: Path) -> str:
                return (r23.AIRGAP_SHA if path.name == "client-airgap.jar"
                        else actual_digest(path))

            with mock.patch.object(r23, "digest", side_effect=fake_digest):
                record = r23.build_package(values)
            self.assertEqual(
                record["result"], "R23_OFFLINE_SANDBOX_PACKAGE_PREPARED_NO_LAUNCH")
            self.assertFalse(record["sandbox_started"])
            self.assertFalse(record["game_started"])
            self.assertFalse(record["full_host_guest_isolation_proven"])
            self.assertEqual(record["read_only_host_mappings"], 2)
            work = Path(record["package_directory"])
            xml = (work / r23.OFFLINE_WSB).read_text(encoding="utf8")
            r23.assess_wsb(xml, work / "assets", jdk)
            assets = sorted(x.name for x in (work / "assets").iterdir())
            self.assertEqual(assets, sorted((
                "SpawnPKLocalServer.jar", "client-airgap.jar",
                "guest-preflight.ps1", "asset-pins.json")))
            pins = json.loads((work / "assets" / "asset-pins.json").read_text())
            self.assertEqual(pins["server_jar_sha256"], server_sha)
            self.assertEqual(pins["source_head"], "a" * 40)
            self.assertNotIn("LogonCommand", xml)

    def test_guest_script_has_no_client_launcher(self):
        script = (Path(__file__).resolve().parent /
                  "r23_guest_preflight.ps1").read_text(encoding="utf-8")
        for forbidden in ("Start-Process", "New-NetFirewallRule", "Enable-WindowsOptionalFeature",
                          "java -jar", "javaw.exe -jar", "Invoke-WebRequest", "curl "):
            self.assertNotIn(forbidden, script)
        for required in (
            "WDAGUtilityAccount", "Get-NetAdapter", "Get-NetRoute",
            "TcpListener", "TcpClient", "Get-FileHash", "asset-pins.json",
        ):
            self.assertIn(required, script)
        self.assertIn("Get-NetRoute -AddressFamily IPv4 -ErrorAction Stop", script)
        self.assertIn("Get-NetRoute -AddressFamily IPv6 -ErrorAction Stop", script)
        self.assertNotIn("-ErrorAction SilentlyContinue", script)

    def test_package_tool_does_not_spawn_sandbox_or_modify_host_firewall(self):
        script = Path(r23.__file__).read_text(encoding="utf-8")
        for forbidden in (
            "Start-Process", "WindowsSandbox.exe", "Enable-WindowsOptionalFeature",
            "New-NetFirewallRule", "subprocess.Popen", "shell=True",
            "os.system(", "subprocess.run([str(java)",
        ):
            self.assertNotIn(forbidden, script)


if __name__ == "__main__":
    unittest.main()
