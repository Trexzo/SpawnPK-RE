#!/usr/bin/env python3
"""R52 no-launch policy checks. Does not execute the proprietary client."""
from pathlib import Path
import unittest

SOURCE = Path(__file__).with_name("r52_review_v308_gui_guest_host.ps1")
TEXT = SOURCE.read_text(encoding="utf-8")

class R52HostReviewPolicy(unittest.TestCase):
    def test_guest_and_real_account_never_started(self):
        banned = (
            "Start-Process", "Invoke-Expression", "Start-VM",
            "Enable-WindowsOptionalFeature", "Get-WindowsOptionalFeature",
            "Set-NetFirewallRule", "New-NetFirewallRule",
            "Invoke-WebRequest", "WebClient", "HttpWebRequest",
            "Stop-Process", ".Kill(", ".Start()",
            "Remove-Item", "Set-Content", "Out-File",
        )
        for token in banned:
            with self.subTest(token=token):
                self.assertNotIn(token, TEXT)

    def test_exact_source_binary_and_no_network_rules(self):
        for token in (
            "ExpectedSourceHead",
            "ExpectedServerSha256",
            "Get-FileHash",
            "024fad774453430bb964076b98d460a6dae821ee31100322d104e30d9a9c97a7",
            "b2484c2889365b93fb48434e12c8f9bc21ca6cd5d4eb2eae39a46b17ced3c8ef",
            "Networking", "ClipboardRedirection", "VGpu",
            "ProtectedClient", "LogonCommand", "ReadOnly",
            "C:\\SPKR23\\assets", "C:\\SPKR23\\jdk",
        ):
            with self.subTest(token=token):
                self.assertIn(token, TEXT)

    def test_no_false_sandbox_or_gui_certification(self):
        for token in (
            "WindowsSandbox.exe",
            "UNKNOWN_NOT_QUERIED",
            "R52_STATIC_PINS_PASS_SANDBOX_BINARY_MISSING",
            "R52_STATIC_PINS_PASS_SANDBOX_BINARY_PRESENT_GUEST_UNVERIFIED",
            "guest_isolation_verified=$false",
            "graphical_client_started=$false",
            "gui_login_or_walk_verified=$false",
            "host_mutations=$false",
            "R52_HOST_READINESS_REVIEW_BLOCKED",
        ):
            with self.subTest(token=token):
                self.assertIn(token, TEXT)

    def test_guest_excluded_from_host_audit(self):
        self.assertIn("WDAGUtilityAccount", TEXT)
        self.assertIn("review-only receipt", TEXT)
        self.assertIn("stage source commit mismatch", TEXT)

if __name__ == "__main__":
    unittest.main()
