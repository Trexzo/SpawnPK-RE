#!/usr/bin/env python3
"""R30: prevent reintroduction of arbitrary prelogin hash7 in legacy fixtures.

These are TEST clients, never original/proprietary v308 classes. All values
must be calculated from the *same* name passed later in the login NL field.
Strict equality is still opt-in on the LocalLab server pending real GUI proof.
"""
from __future__ import annotations

from pathlib import Path
import re
import unittest

ROOT=Path(__file__).resolve().parents[2]/"server"/"src"/"spk"/"local"

EXPECTED={
    "BankRuntimeIntegrationTest":"local",
    "BankV4AdvancedRuntimeIntegrationTest":"local",
    "BootstrapRuntimeIntegrationTest":"local",
    "ClientBuildRevision308CompatibilityTest":"local",
    "CompCapeCustomizationTest":"opensrc",
    "DualProfileSocketIntegrationTest":"user",
    "EngineR41TradeApproachSocketTest":"user",
    "EngineR5ItemLibrarySocketTest":"user",
    "EngineR6RegionSocketCommandTest":"user",
    "EngineR7DevPanelSocketTest":"user",
    "EquipmentMultiSlotRuntimeIntegrationTest":"local",
    "EquipmentRuntimeIntegrationTest":"local",
    "HandshakeIntegrationTest":"local",
    "ItemCommandRuntimeIntegrationTest":"local",
    "LogoutButtonIntegrationTest":"opensrc",
    "NormalInventoryDragRuntimeIntegrationTest":"local",
    "PetReplacementRuntimeTest":"opensrc",
    "PetRuntimeIntegrationTest":"opensrc",
    "PrayerMagicStyleRuntimeIntegrationTest":"local",
    "SharedWorldSocketIntegrationTest":"user",
    "SpawnTabRuntimeIntegrationTest":"local",
    "TypedRequestWorldInboxSocketTest":"user",
}

class R30FixtureMigrationTests(unittest.TestCase):
    def test_exact_22_migrated_synthetic_socket_client_callsites(self):
        self.assertEqual(len(EXPECTED),22)
        for name,user in EXPECTED.items():
            with self.subTest(name=name):
                path=ROOT/(name+".java")
                self.assertTrue(path.is_file(),f"missing fixture {path}")
                content=path.read_text(encoding="utf-8")
                self.assertNotRegex(content,r"out\s*\.\s*write\(\s*7\s*\)")
                value=user if user=="user" else '"'+user+'"'
                expected="out.write(V308PreloginNameHash.hash5("+value+"))"
                self.assertEqual(content.count(expected),1)
                self.assertTrue(
                    "out.write(14)" in content or
                    "out.write(LocalLoginTransport.PRELOGIN_REQUEST)" in content,
                    "fixture must send prelogin request 14"
                )
                if user!="user":
                    self.assertIn(
                        '"'+user+'"',content,
                        "static hash must be derived from the actual NL login username"
                    )
                else:
                    self.assertTrue(
                        "loginPayload(seeds,user)" in content
                        or "loginPayload(seeds, user)" in content
                        or "loginPayload(seeds,user)" in content.replace("\n",""),
                        "dynamic hash uses same user variable as login payload"
                    )

    def test_strict_mode_opt_in_and_no_identity_in_error(self):
        content=(ROOT/"LocalLoginTransport.java").read_text(encoding="utf-8")
        self.assertIn('Boolean.getBoolean("spk.local.prelogin.enforceUsernameHash")',content)
        self.assertIn(
            'userHash5!=V308PreloginNameHash.hash5(frame.username)',content
        )
        self.assertIn(
            'throw new IOException("v308 prelogin username hash5 mismatch")',content
        )
        self.assertNotRegex(
            content,
            r'throw new IOException\([^\n]*username\+'
        )

    def test_legacy_test_explicitly_remains_a_negative_control(self):
        src=(ROOT/"R29PreloginHashClientParityTest.java").read_text(encoding="utf-8")
        self.assertIn('valid("local", 7, 16)',src)
        strict=(ROOT/"R30OptionalStrictPreloginNameHashTest.java").read_text(encoding="utf-8")
        self.assertIn('exchange("local", 7, true, false)',strict)
        self.assertIn('exchange("local", 7, false, true)',strict)
        self.assertIn('exchangeWithDefault("local", 7, false)',strict)


if __name__=="__main__":
    unittest.main()
