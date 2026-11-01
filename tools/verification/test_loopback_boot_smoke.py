#!/usr/bin/env python3
"""Unit tests for isolated boot smoke hash, HTTP and fail-closed checks."""
import hashlib
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
import socket
import tempfile
import threading
import unittest

from loopback_boot_smoke import (
    SmokeFailure, port_accepts, probe_aux, run_smoke, sha256,
)


class VersionEndpoint(BaseHTTPRequestHandler):
    def do_GET(self):
        self.send_response(200 if self.path == "/spk_live/versions.txt" else 404)
        self.end_headers()
        self.wfile.write(b"cache_version = 67.0\n")

    def log_message(self, *_):
        pass


class LoopbackBootTests(unittest.TestCase):
    def test_exact_sha_and_invalid_timeout(self):
        with tempfile.TemporaryDirectory() as folder:
            jar = Path(folder) / "artifact.jar"
            jar.write_bytes(b"test")
            self.assertEqual(
                sha256(jar), hashlib.sha256(b"test").hexdigest()
            )
            with self.assertRaises(SmokeFailure):
                run_smoke(jar, "java", 0)

    def test_http_versions_probe_and_ports(self):
        server = HTTPServer(("127.0.0.1", 0), VersionEndpoint)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            self.assertTrue(
                port_accepts("127.0.0.1", server.server_port)
            )
            self.assertTrue(
                probe_aux("127.0.0.1", server.server_port)
            )
        finally:
            server.shutdown()
            server.server_close()
            thread.join(2)

    def test_non_http_listener_not_accepted(self):
        listener = socket.socket()
        listener.bind(("127.0.0.1", 0))
        listener.listen(1)

        def work():
            conn, _ = listener.accept()
            with conn:
                conn.recv(1024)
                conn.sendall(b"NOT_HTTP\r\n\r\n")

        thread = threading.Thread(target=work, daemon=True)
        thread.start()
        try:
            self.assertFalse(
                probe_aux("127.0.0.1", listener.getsockname()[1])
            )
        finally:
            thread.join(2)
            listener.close()


if __name__ == "__main__":
    unittest.main()
