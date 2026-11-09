"""Focused offline checks for the independent revision-317 wire fixture."""
import struct
import unittest

from synthetic_revision317_login import Isaac, bank_action_145, login_body, u32, v308_username_hash5


class SyntheticRevision317FixtureTests(unittest.TestCase):
    def test_v308_prelogin_real_username_hash5_vectors(self):
        self.assertEqual(v308_username_hash5("local"), 2)
        self.assertEqual(v308_username_hash5("localtest"), 29)
        self.assertEqual(v308_username_hash5("myau"), 10)
        self.assertEqual(v308_username_hash5("opensrc"), 12)
        self.assertEqual(v308_username_hash5("spawnpk"), 24)
        self.assertEqual(v308_username_hash5("ab😀cd"), 27)
        self.assertEqual(v308_username_hash5("LocalTest"), 29)
        self.assertEqual(v308_username_hash5("LOCAL"), 2)
        self.assertEqual(v308_username_hash5(""), 0)
        self.assertEqual(v308_username_hash5("___"), 0)
        self.assertEqual(v308_username_hash5("local  "), 2)
        self.assertEqual(v308_username_hash5("abcdefghijkl_ignored"),
                         v308_username_hash5("abcdefghijkl"))
        for name in ("local", "localtest", "123456789012", "A", "z", ""):
            self.assertIn(v308_username_hash5(name), range(32))
        with self.assertRaises(TypeError):
            v308_username_hash5(None)

    def test_login_frame_lengths_and_revision(self):
        frame, seeds = login_body(0x0123456789ABCDEF)
        self.assertEqual(seeds, (
            0x01020304, 0x11223344, 0x01234567, 0x89ABCDEF
        ))
        self.assertEqual(frame[0], 16)
        self.assertEqual(frame[1], len(frame) - 2)
        self.assertEqual(frame[2], 255)
        self.assertEqual(struct.unpack(">H", frame[3:5])[0], 317)
        self.assertEqual(frame[5], 0)
        self.assertEqual(frame[6:42], b"\x00" * 36)
        self.assertEqual(frame[42], len(frame) - 43)
        self.assertEqual(frame[43], 10)
        self.assertTrue(frame.endswith(b"LOCAL-CLIENT\n"))

    def test_rejected_revision_fixture_not_admitted_as_valid(self):
        frame, _ = login_body(0x0123456789ABCDEF, revision=316)
        self.assertEqual(struct.unpack(">H", frame[3:5])[0], 316)

    def test_bank_action_bea_fixture_and_cipher_progress(self):
        cipher = Isaac((1, 2, 3, 4))
        verifier = Isaac((1, 2, 3, 4))
        raw = bank_action_145(cipher, 5382, 0, 995)
        self.assertEqual(raw, bytes([
            (145 + verifier.next_int()) & 255,
            5382 >> 8, (5382 + 128) & 255,
            0, 128,
            995 >> 8, (995 + 128) & 255,
        ]))
        self.assertEqual(len(raw), 7)
        second = bank_action_145(cipher, 5382, 0, 995)
        self.assertEqual(second[0], (145 + verifier.next_int()) & 255)
        with self.assertRaises(ValueError):
            bank_action_145(cipher, -1, 0, 995)
        with self.assertRaises(ValueError):
            bank_action_145(cipher, 5382, 0, 65536)

    def test_isaac_seed_determinism_and_256_word_refill(self):
        seeds = (1, 2, 3, 4)
        a = Isaac(seeds)
        b = Isaac(seeds)
        sequence = [a.next_int() for _ in range(520)]
        self.assertEqual(sequence, [b.next_int() for _ in range(520)])
        self.assertEqual(len(set(sequence[:256])), 256)
        self.assertTrue(all(0 <= x <= 0xFFFFFFFF for x in sequence))
        self.assertNotEqual(sequence[:4], sequence[256:260])
        self.assertNotEqual(sequence[:4], [Isaac((1, 2, 3, 5)).next_int()] * 4)
        self.assertEqual(u32(-1), 0xFFFFFFFF)


if __name__ == "__main__":
    unittest.main()
