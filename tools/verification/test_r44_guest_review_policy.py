#!/usr/bin/env python3
"""R44 offline safety/source-policy checks: no guest, GUI, Java or network used."""
from __future__ import annotations
import re
from pathlib import Path
import unittest

SCRIPT=Path(__file__).with_name("r44_prepare_v308_gui_guest_review.ps1")


class R44GuiGuestReviewOnlyPolicyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.s=SCRIPT.read_text(encoding="utf-8")

    def test_exact_provenance_and_owned_input_reading(self):
        s=self.s
        for key in (
            "SourceAssets", "SourceWsb", "GuestJdkRoot",
            "ServerJar", "ExpectedServerSha256", "RepositoryHead",
            "DestinationRoot", "VerifiedHash",
            "asset-pins.json",
            "SpawnPKLocalServer.jar",
            "client-airgap.jar",
            "r27-v308-gui-bootstrap-audit.json",
            "024fad774453430bb964076b98d460a6dae821ee31100322d104e30d9a9c97a7",
            "b2484c2889365b93fb48434e12c8f9bc21ca6cd5d4eb2eae39a46b17ced3c8ef",
            "$pins.source_head=$RepositoryHead",
            "$pins.server_jar_sha256=$newServerSha",
            "[void](VerifiedHash (Join-Path $source 'SpawnPKLocalServer.jar')",
        ):
            with self.subTest(key=key):
                self.assertIn(key,s)

    def test_output_is_new_temp_only_and_separate_source(self):
        s=self.s
        for key in (
            "spawnpk-r44-guest-", "Test-Path -LiteralPath $dest",
            "$assetDest=Join-Path $dest 'assets'",
            "Copy-Item -Path (Join-Path $source '*') -Destination $assetDest",
            "Copy-Item -LiteralPath $server -Destination",
            "$assetMapping[0].SelectSingleNode('HostFolder').InnerText",
            "$jdkMapping[0].SelectSingleNode('HostFolder').InnerText",
            "source_r38_assets_unchanged=$true",
        ):
            with self.subTest(key=key):
                self.assertIn(key,s)
        self.assertLess(s.index("if(-not $dest.StartsWith"),
                        s.index("[void][IO.Directory]::CreateDirectory($assetDest)"))
        self.assertLess(s.index("R44_REFUSED: WSB template"),
                        s.index("[void][IO.Directory]::CreateDirectory($assetDest)"))

    def test_guest_network_and_manual_launch_guards_unchanged(self):
        s=self.s
        for key in (
            "$cfg.Networking -cne 'Disable'",
            "$cfg.ClipboardRedirection -cne 'Disable'",
            "$cfg.VGpu -cne 'Disable'",
            "$cfg.ProtectedClient -cne 'Enable'",
            "$template.SelectNodes('//LogonCommand').Count -ne 0",
            "$folders.Count -ne 2",
            "Where-Object{$_.ReadOnly -cne 'true'}",
            "SpawnPK-R44-NetworkDisabled-REVIEW-ONLY.wsb",
            "windows_sandbox_started=$false",
            "original_v308_gui_started=$false",
            "actual_gui_login_verified=$false",
        ):
            with self.subTest(key=key):
                self.assertIn(key,s)
        self.assertNotIn("<LogonCommand>",s)

    def test_no_process_launch_service_change_or_host_delete(self):
        s=self.s
        for forbidden in (
            r"\bStart-Process\b", r"\bStop-Process\b",
            r"\bStart-VM\b", r"\bEnable-WindowsOptionalFeature\b",
            r"\bNew-NetFirewallRule\b", r"\bSet-NetFirewallRule\b",
            r"\bRemove-Item\b", r"\btaskkill\b",
            r"\bInvoke-WebRequest\b", r"\bInvoke-RestMethod\b",
            r"\bjava\.exe\s+-jar\b", r"\bWindowsSandbox\.exe\b",
        ):
            with self.subTest(forbidden=forbidden):
                self.assertIsNone(re.search(forbidden,s,re.IGNORECASE))


if __name__=="__main__":
    unittest.main()
