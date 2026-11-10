#!/usr/bin/env python3
"""R37 no-launch policy tests for guest-only owned GUI screenshot evidence.

These tests inspect script source. They do not prove a screenshot has been
captured or that the original graphical v308 client has ever run.
"""
from __future__ import annotations
from pathlib import Path
import re
import unittest


SCRIPT = Path(__file__).with_name("r27_guest_gui_window_smoke.ps1")


class R37GuestFrameEvidenceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.source = SCRIPT.read_text(encoding="utf-8")

    def test_capture_cannot_bypass_r27_isolated_guest_launch(self):
        source=self.source
        required=(
            "WDAGUtilityAccount",
            "r25-private-guest-home-receipt.json",
            "r26-owned-server-smoke-receipt.json",
            "guest-cache-preflight.ps1",
            "LAUNCH R27 GUEST V308 GUI WITH LOCAL SERVER",
            "if(-not $Launch)",
            "if($CaptureFrame)",
            "frame capture requires explicit -Launch and approval",
            "if($ExactApproval -cne $approval)",
            "Port-Free",
            "r27-v308-gui-bootstrap-audit.json",
        )
        for item in required:
            with self.subTest(gate=item):
                self.assertIn(item,source)
        self.assertLess(source.index("if($env:USERNAME -cne"),
                        source.index("if($CaptureFrame){\n        $evidence="))
        self.assertLess(source.index("if($ExactApproval -cne $approval)"),
                        source.index("$client=Start-PrivateJava"))
        self.assertLess(source.index("guest-cache-preflight.ps1"),
                        source.index("$client=Start-PrivateJava"))

    def test_frame_attributed_to_child_window_and_guest_home(self):
        source=self.source
        checks=(
            "Capture-GuestOwnedClientWindow",
            "GetWindowThreadProcessId",
            "IsWindowVisible",
            "GetWindowRect",
            "$owner -ne [uint32]$child.Id",
            "r37-v308-guest-window.png",
            "Join-Path $privateHome",
            "Get-FileHash -LiteralPath $file -Algorithm SHA256",
            "[Drawing.Graphics]::FromImage",
            "$graphics.CopyFromScreen(",
            "if($colours.Count -lt 5)",
            "System.Collections.Generic.HashSet[int]",
            "$bitmap.Dispose()",
            "$graphics.Dispose()",
        )
        for item in checks:
            with self.subTest(item=item):
                self.assertIn(item,source)

    def test_receipt_only_after_owned_process_cleanup(self):
        source=self.source
        self.assertIn("(-not $CaptureFrame -or $frameSaved)",source)
        self.assertIn("-not $forcedClientStop -and -not $forcedServerStop",source)
        self.assertLess(source.index("$server.Dispose()"),
                        source.index("R37_GUEST_WINDOW_FRAME_EVIDENCE_CAPTURED"))
        self.assertIn("r37-guest-gui-frame-receipt.json",source)
        self.assertIn("if(Test-Path -LiteralPath $receiptFile){exit 2}",source)
        self.assertIn("R37_GUEST_WINDOW_FRAME_EVIDENCE_AND_CLEANUP_PASS",source)
        for item in ("original_unmodified_v308_client_started=$false",
                     "authentic_login_verified=$false",
                     "original_game_rendering_verified=$false",
                     "gameplay_verified=$false"):
            with self.subTest(nonclaim=item):
                self.assertIn(item,source)

    def test_forbidden_host_process_and_network_changes(self):
        source=self.source
        for item in ("Stop-Process", "taskkill", "New-NetFirewallRule",
                     "Enable-WindowsOptionalFeature", "Set-NetFirewall",
                     "Invoke-WebRequest", "Start-Process",
                     "WindowsSandbox.exe", "SendKeys", "System.Net.WebClient"):
            with self.subTest(unsafe=item):
                self.assertNotIn(item,source)
        self.assertIsNone(re.search(r"\$\s*home\b",source,re.I))
        self.assertIsNone(re.search(r"\$\s*pid\b",source,re.I))


if __name__ == "__main__":
    unittest.main()
