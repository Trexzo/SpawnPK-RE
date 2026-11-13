#!/usr/bin/env python3
"""R38 static guest/manual-authentication policy checks; never launches Java."""
from __future__ import annotations

from pathlib import Path
import re
import unittest


SCRIPT = Path(__file__).with_name("r27_guest_gui_window_smoke.ps1")


class R38ManualGuestLoginTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = SCRIPT.read_text(encoding="utf-8")

    def test_old_gui_window_mode_remains_default_and_manual_is_opt_in(self):
        s = self.source
        for required in (
            "[switch]$ManualLoginMovement",
            "[string]$ManualApproval=''",
            "VERIFY R38 GUEST SYNTHETIC LOGIN AND MOVEMENT",
            "if($ManualLoginMovement -and",
            "-not $CaptureFrame",
            "$ManualApproval -cne $manualCaptureApproved",
            "manual login proof requires explicit -Launch and approval",
            "if(-not $Launch)",
            "if($ExactApproval -cne $approval)",
            "if($env:USERNAME -cne 'WDAGUtilityAccount')",
            "r25-private-guest-home-receipt.json",
            "r26-owned-server-smoke-receipt.json",
            "guest-cache-preflight.ps1",
            "R27_GUEST_GUI_WINDOW_ONLY_NO_LOGIN_REQUESTED",
        ):
            with self.subTest(gate=required):
                self.assertIn(required, s)
        self.assertLess(s.index("if($env:USERNAME -cne"),
                        s.index("$client=Start-PrivateJava"))
        self.assertLess(s.index("$ManualApproval -cne $manualCaptureApproved"),
                        s.index("$server=Start-PrivateJava"))
        self.assertLess(s.index("if($ExactApproval -cne $approval)"),
                        s.index("$server=Start-PrivateJava"))

    def test_proof_requires_same_local_peer_world_player_and_owned_tcp(self):
        s = self.source
        for required in (
            "function Read-R38LocalGameTrace",
            "CLIENT_PACKET seq=1 opcode=185 len=2 widget=912",
            "V512_WORLD_REGISTER playerId=",
            "LOGIN_SUCCESS_LOCAL rank=205",
            "persistent=true",
            "M5_MOVEMENT_REQUEST MovementRequest",
            "opcode=164",
            "action=ACCEPTED",
            "M5_AUTHORITATIVE_TICK mode=WALK",
            "$widget.Groups['peer'].Value",
            "$registered.Groups['id'].Value",
            "$request.Index -lt $walk.Index",
            "function Test-R38OwnedClientGameConnection",
            "$_.OwningProcess -eq $child.Id",
            "$_.RemoteAddress -eq '127.0.0.1'",
            "$_.RemotePort -eq 43594",
            "$manualProof.Verified -and",
            "(Test-R38OwnedClientGameConnection $client)",
            "$manualVerified=$true",
        ):
            with self.subTest(proof=required):
                self.assertIn(required, s)

    def test_bounded_async_local_logs_and_clean_process_ownership(self):
        s = self.source
        for required in (
            "[ValidateRange(30,240)][int]$ManualProofSeconds=120",
            "$manualDeadline=[DateTime]::UtcNow.AddSeconds($ManualProofSeconds)",
            "[IO.FileMode]::CreateNew",
            "[IO.FileShare]::ReadWrite",
            "$server.StandardOutput.BaseStream.CopyToAsync($serverOutStream)",
            "$server.StandardError.BaseStream.CopyToAsync($serverErrStream)",
            "$serverOutTask.Wait(5000)",
            "$serverErrTask.Wait(5000)",
            "5242880",
            "$traceCopyComplete",
            "-not $forcedClientStop -and -not $forcedServerStop",
            "r38-guest-login-walk-receipt.json",
            "R38_GUEST_SYNTHETIC_LOGIN_AND_WALK_EVIDENCE_PASS",
        ):
            with self.subTest(bounded=required):
                self.assertIn(required, s)
        self.assertLess(s.index("$server.Dispose()"),
                        s.index("R38_GUEST_SYNTHETIC_LOGIN_AND_WALK_EVIDENCE_PASS"))

    def test_no_live_credentials_host_launch_or_misleading_success(self):
        s = self.source
        for required in (
            "NEVER use live credentials",
            "unmodified_original_v308_launched=$false",
            "real_production_credentials_collected_by_harness=$false",
            "actual_gui_login_e2e_verified=$false",
            "gameplay_feature_completeness_verified=$false",
            "client_binary='certified-airgap-v308-derivative'",
            "guest_client_pid_owns_local_43594_socket=true",
        ):
            with self.subTest(nonclaim=required):
                self.assertIn(required, s)
        for forbidden in ("SendKeys", "Set-NetFirewall", "New-NetFirewallRule",
                          "Enable-WindowsOptionalFeature", "WindowsSandbox.exe",
                          "Stop-Process", "taskkill"):
            with self.subTest(forbidden=forbidden):
                self.assertNotIn(forbidden, s)
        self.assertIsNone(re.search(r"\$\s*home\b", s, re.I))
        self.assertIsNone(re.search(r"\$\s*pid\b", s, re.I))


if __name__ == "__main__":
    unittest.main()
