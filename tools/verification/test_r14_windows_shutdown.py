"""R14 Windows acceptance private-JVM shutdown: no broad Java process termination."""
from __future__ import annotations

import io
import unittest

from synthetic_persistence_reconnect import (
    ReconnectFailure, request_owned_server_shutdown
)


class RecordingPipe:
    def __init__(self):
        self.contents = bytearray()
        self.flushed = False
        self.closed = False

    def write(self, data):
        self.contents.extend(data)

    def flush(self):
        self.flushed = True

    def close(self):
        self.closed = True


class OwnedProcess:
    def __init__(self, token=None, with_pipe=True):
        self._spk_acceptance_token = token
        self.stdin = RecordingPipe() if with_pipe else None
        self.terminations = 0

    def terminate(self):
        self.terminations += 1


class R14ShutdownTest(unittest.TestCase):
    def test_windows_only_writes_token_to_owned_pipe(self):
        token = "f" * 64
        process = OwnedProcess(token)
        request_owned_server_shutdown(process, windows=True)
        self.assertEqual(bytes(process.stdin.contents), token.encode("ascii") + b"\n")
        self.assertTrue(process.stdin.flushed)
        self.assertTrue(process.stdin.closed)
        self.assertEqual(process.terminations, 0)

    def test_windows_rejects_missing_pipe_without_killing(self):
        process = OwnedProcess("a" * 64, with_pipe=False)
        with self.assertRaises(ReconnectFailure):
            request_owned_server_shutdown(process, windows=True)
        self.assertEqual(process.terminations, 0)

    def test_windows_rejects_wrong_credential_without_killing(self):
        for token in (None, "", "b" * 63, "z" * 64, "A" * 64):
            with self.subTest(token=token):
                process = OwnedProcess(token)
                with self.assertRaises(ReconnectFailure):
                    request_owned_server_shutdown(process, windows=True)
                self.assertEqual(bytes(process.stdin.contents), b"")
                self.assertEqual(process.terminations, 0)

    def test_nonwindows_existing_sigterm_path_unchanged(self):
        process = OwnedProcess()
        request_owned_server_shutdown(process, windows=False)
        self.assertEqual(process.terminations, 1)

if __name__ == "__main__":
    unittest.main()
