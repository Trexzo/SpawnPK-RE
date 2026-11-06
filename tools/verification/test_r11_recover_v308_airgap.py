"""R11 client recovery: fail-closed source, metadata and private publication."""
from pathlib import Path
import hashlib
import json
import socket
import tempfile
import unittest
from unittest import mock
import zipfile

import r11_recover_v308_airgap as r11


def zip_entries(path, entries):
    with zipfile.ZipFile(path, "w") as z:
        for name, data in entries:
            z.writestr(name, data, compress_type=zipfile.ZIP_STORED)


def class_pair(a: bytes, b: bytes):
    return {
        "preSha256": hashlib.sha256(a).hexdigest(),
        "postSha256": hashlib.sha256(b).hexdigest(),
    }


class RecoveryTests(unittest.TestCase):
    def test_wrong_original_rejected_without_running_or_overwriting(self):
        with tempfile.TemporaryDirectory() as d:
            parent = Path(d)
            original = parent / "reference.jar"
            original.write_bytes(b"not the actual pinned v308 client")
            user_output = parent / "local-client"
            user_output.mkdir()
            marker = user_output / "client-airgap.jar"
            marker.write_bytes(b"live user version")
            with mock.patch("subprocess.run") as child:
                with self.assertRaises(r11.AdmissionError):
                    r11.recover(original, parent, "python")
                child.assert_not_called()
            self.assertEqual(marker.read_bytes(), b"live user version")
            self.assertEqual(
                sorted(p.name for p in parent.iterdir()),
                ["local-client", "reference.jar"],
            )

    def test_bad_parent_denied(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            self.assertEqual(r11.verified_new_parent(root), root)
            with self.assertRaises(r11.AdmissionError):
                r11.verified_new_parent(root / "missing")
            with self.assertRaises(r11.AdmissionError):
                r11.verified_new_parent(Path("relative"))

    def test_manifest_requires_exact_provenance_and_complete_change_sets(self):
        good = {
            "format": r11.FORMAT,
            "inputSha256": r11.ORIGINAL_SHA,
            "localhostSha256": r11.LOCALHOST_SHA,
            "airgapSha256": r11.AIRGAP_SHA,
            "airgapExternalEndpointAuthority": False,
            "loopbackHost": "127.0.0.1",
            "gamePort": 43594,
            "auxPort": 43595,
            "updaterBase": "http://127.0.0.1:43595/spk_live/",
            "jarEntryCompression": "stored",
            "walkHereFallbackRadius": 2,
            "walkHereFallbackByteLengthNeutral": True,
            "unchangedEntryPayloadIdentity": True,
            "entryInventoryAndOrderPreserved": True,
            "transactionalPublication": True,
            "localhostChangedEntries": {
                "rs/Client.class": class_pair(b"a", b"b"),
                "rs/f/a.class": class_pair(b"c", b"d"),
            },
            "airgapChangedEntries": {
                name: class_pair(b"a", b"b") for name in (
                    "rs/Client.class", "rs/f/a.class",
                    "rs/cache/b/e.class", "rs/f/a$a.class",
                    "rs/l/d/d.class", "rs/l/d/e.class",
                    "rs/gui/m.class", "rs/gui/n.class",
                    "rs/gui/o.class", "rs/s/b/n.class", "rs/s/t/l.class",
                )
            },
        }
        r11.check_manifest(good)
        for key, wrong in [
            ("airgapSha256", "f"*64),
            ("airgapExternalEndpointAuthority", True),
            ("loopbackHost", "spawnpk.net"),
            ("walkHereFallbackRadius", 1),
            ("jarEntryCompression", "deflate"),
        ]:
            with self.subTest(key=key):
                bad = dict(good, **{key: wrong})
                with self.assertRaises(r11.AdmissionError):
                    r11.check_manifest(bad)

        for key in ("airgapChangedEntries", "localhostChangedEntries"):
            with self.subTest(key=key):
                bad = dict(good, **{key: {}})
                with self.assertRaises(r11.AdmissionError):
                    r11.check_manifest(bad)

    def test_class_delta_allows_only_declared_mutations(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            original = root / "original.jar"
            derived = root / "client-localhost.jar"
            zip_entries(original, [
                ("rs/Client.class", b"old"),
                ("rs/f/a.class", b"no change"),
                ("data/cache.bin", b"unchanged"),
            ])
            zip_entries(derived, [
                ("rs/Client.class", b"new"),
                ("rs/f/a.class", b"no change"),
                ("data/cache.bin", b"unchanged"),
            ])
            expected = {"rs/Client.class": class_pair(b"old", b"new")}
            self.assertEqual(r11.verify_jar_delta(original, derived, expected), 1)
            zip_entries(derived, [
                ("rs/Client.class", b"new"),
                ("rs/f/a.class", b"no change"),
                ("data/cache.bin", b"tampered"),
            ])
            with self.assertRaises(r11.AdmissionError):
                r11.verify_jar_delta(original, derived, expected)
            zip_entries(derived, [
                ("data/cache.bin", b"unchanged"),
                ("rs/Client.class", b"new"),
                ("rs/f/a.class", b"no change"),
            ])
            with self.assertRaises(r11.AdmissionError):
                r11.verify_jar_delta(original, derived, expected)

    def test_wrong_class_preimage_or_drift_is_denied(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            original, derivative = root / "ref.jar", root / "client-localhost.jar"
            zip_entries(original, [("rs/Client.class", b"old")])
            zip_entries(derivative, [("rs/Client.class", b"new")])
            wrong = {"rs/Client.class": class_pair(b"wrong", b"new")}
            with self.assertRaises(r11.AdmissionError):
                r11.verify_jar_delta(original, derivative, wrong)
            declared_but_equal = {"rs/Client.class": class_pair(b"old", b"old")}
            zip_entries(derivative, [("rs/Client.class", b"old")])
            with self.assertRaises(r11.AdmissionError):
                r11.verify_jar_delta(original, derivative, declared_but_equal)


if __name__ == "__main__":
    unittest.main()
