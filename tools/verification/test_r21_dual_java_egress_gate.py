#!/usr/bin/env python3
"""R21 negative controls: no firewall mutation, GUI launch or live Java stop."""
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent))
import r21_windows_dual_java_egress_gate as r21
from r13_windows_gui_egress_gate import GateCheck


class R21DualJavaFirewallTests(unittest.TestCase):
    def audit_with(self, java_rule: bool, javaw_rule: bool,
                   javaw_hash: bool = True, java_unused: bool = True):
        root = Path(tempfile.gettempdir()) / "spawnpk-r13-owned-jdk-fixture" / "jdk"

        def pin(exe, expected):
            allowed = exe.name != "javaw.exe" or javaw_hash
            return r21.Check("hash", allowed, "test-pin")

        def firewall(exe, rule):
            self.assertEqual(
                rule, r21.RULE_JAVA if exe.name == "java.exe"
                else r21.RULE_JAVAW)
            return GateCheck("fw", java_rule if exe.name == "java.exe"
                             else javaw_rule, "test-only")

        with mock.patch.object(r21, "executable_pin", side_effect=pin), \
             mock.patch.object(r21, "basic_private_exe", return_value=
                               GateCheck("private", True, "test-only")), \
             mock.patch.object(r21, "no_existing_java_uses", return_value=
                               GateCheck("unused", java_unused, "test-only")), \
             mock.patch.object(r21, "firewall_record", side_effect=firewall):
            return r21.audit(root)

    def test_readiness_requires_two_exact_outbound_rules(self):
        ready = self.audit_with(True, True)
        self.assertEqual(
            ready["result"], "R21_DUAL_PRIVATE_JAVA_OS_EGRESS_READY")
        self.assertEqual(len(ready["checks"]), 8)
        self.assertFalse(ready["graphical_client_started"])
        self.assertFalse(ready["graphical_acceptance"])
        self.assertEqual(ready["firewall_rules_changed"], 0)

    def test_missing_java_rule_denies(self):
        blocked = self.audit_with(False, True)
        self.assertEqual(blocked["result"],
                         "R21_DUAL_PRIVATE_JAVA_OS_EGRESS_BLOCKED")
        self.assertFalse([x for x in blocked["checks"]
                          if x["name"] == "active_block_java.exe"][0]["ok"])

    def test_missing_javaw_rule_denies(self):
        blocked = self.audit_with(True, False)
        self.assertEqual(blocked["result"],
                         "R21_DUAL_PRIVATE_JAVA_OS_EGRESS_BLOCKED")

    def test_unpinned_javaw_denies_even_with_both_rules(self):
        blocked = self.audit_with(True, True, javaw_hash=False)
        self.assertEqual(blocked["result"],
                         "R21_DUAL_PRIVATE_JAVA_OS_EGRESS_BLOCKED")

    def test_in_use_executable_denies(self):
        blocked = self.audit_with(True, True, java_unused=False)
        self.assertEqual(blocked["result"],
                         "R21_DUAL_PRIVATE_JAVA_OS_EGRESS_BLOCKED")

    def test_missing_binaries_are_not_verified(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "javaw.exe"
            check = r21.executable_pin(path, r21.EXE_SHA256["javaw.exe"])
            self.assertFalse(check.ok)

    def test_pin_constants_are_valid_distinct_sha256(self):
        self.assertEqual(set(r21.EXE_SHA256), {"java.exe", "javaw.exe"})
        self.assertNotEqual(*r21.EXE_SHA256.values())
        self.assertTrue(all(len(x) == 64 and
                            all(c in "0123456789abcdef" for c in x)
                            for x in r21.EXE_SHA256.values()))

    def test_operator_install_script_default_read_only_and_no_shared_process_kill(self):
        ps = (Path(__file__).resolve().parent /
              "r21_plan_or_install_private_java_firewall.ps1").read_text(
                  encoding="utf-8")
        lower = ps.lower()
        for token in ("stop-process", "taskkill", "remove-netfirewallrule",
                      "disable-netfirewallprofile", "set-netfirewallprofile",
                      "start-process", "start-job"):
            self.assertNotIn(token, lower)
        self.assertIn("if (-not $Apply) {", ps)
        self.assertIn("New-NetFirewallRule", ps)
        self.assertLess(ps.index("if (-not $Apply) {"),
                        ps.index("New-NetFirewallRule -PolicyStore"))
        for opt in ("-Program $item.FullPath", "-Direction Outbound",
                    "-Action Block", "-Profile Any", "-RemoteAddress Any",
                    "-Protocol Any"):
            self.assertIn(opt, ps)
        self.assertIn("INSTALL R21 TWO PRIVATE JAVA OUTBOUND BLOCK RULES", ps)
        self.assertIn("IsInRole", ps)
        self.assertIn("Get-CimInstance Win32_Process", ps)
        self.assertIn("r21_windows_dual_java_egress_gate.py", ps)


if __name__ == "__main__":
    unittest.main()
