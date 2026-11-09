#!/usr/bin/env python3
"""Offline R29 regression for POSIX Gradle bootstrap retry and SHA safeguards.

Serves a tiny synthetic ZIP from an ephemeral 127.0.0.1 HTTP listener.
Never downloads Gradle, invokes Java, or reaches a public network. The
modified copy of gradlew executes only a fake local 'gradle' shell script.
"""
from __future__ import annotations

from contextlib import contextmanager
import hashlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from io import BytesIO
from pathlib import Path
import shutil
import subprocess
import tempfile
import threading
import unittest
import zipfile


WRAPPER = Path(__file__).resolve().parents[2] / "server" / "gradlew"


def synthetic_zip() -> bytes:
    contents = b"#!/bin/sh\nprintf 'R29_FAKE_GRADLE_EXECUTED args=%s\\n' \"$*\"\n"
    archive = BytesIO()
    with zipfile.ZipFile(archive, "w", compression=zipfile.ZIP_STORED) as z:
        executable = zipfile.ZipInfo("gradle-8.10.2/bin/gradle")
        executable.create_system = 3
        executable.external_attr = 0o100755 << 16
        z.writestr(executable, contents)
    return archive.getvalue()


@contextmanager
def local_http_zip(payload: bytes, fail_first: int = 0):
    state = {"requests": 0}

    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            state["requests"] += 1
            if self.path != "/gradle-8.10.2-bin.zip":
                self.send_error(404)
                return
            if state["requests"] <= fail_first:
                self.send_response(504)
                self.end_headers()
                self.wfile.write(b"synthetic transient 504")
                return
            self.send_response(200)
            self.send_header("Content-Type", "application/zip")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)

        def log_message(self, *_args):
            pass

    httpd = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    thread = threading.Thread(target=httpd.serve_forever, daemon=True)
    thread.start()
    try:
        yield f"http://127.0.0.1:{httpd.server_port}/gradle-8.10.2-bin.zip", state
    finally:
        httpd.shutdown()
        httpd.server_close()
        thread.join(timeout=5)


def fixture_script(home: Path, url: str, sha: str) -> Path:
    source = WRAPPER.read_text(encoding="utf-8")
    old_url = 'DIST_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"'
    old_sha = (
        "GRADLE_SHA256=31c55713e40233a8303827ceb42ca48a47267a0ad4bab9177123121e71524c26"
    )
    if source.count(old_url) != 1 or source.count(old_sha) != 1:
        raise AssertionError("R29 changed pinned Gradle wrapper source unexpectedly")
    source = source.replace(old_url, 'DIST_URL="' + url + '"')
    source = source.replace(old_sha, "GRADLE_SHA256=" + sha)
    path = home / "gradlew"
    path.write_text(source, encoding="utf-8")
    return path


@unittest.skipUnless(
    shutil.which("curl") and shutil.which("unzip") and shutil.which("sh"),
    "R29 offline bootstrap fixture requires POSIX sh, unzip and curl",
)
class R29GradleDownloadTests(unittest.TestCase):
    def run_wrapper(self, script: Path, home: Path):
        import os
        env = os.environ.copy()
        env["HOME"] = str(home / "isolated-user-home")
        (home / "isolated-user-home").mkdir(exist_ok=True)
        return subprocess.run(
            ["sh", str(script), "offline-argument"],
            cwd=home, env=env, capture_output=True, text=True, timeout=40,
        )

    def test_transient_http504_recovers_and_never_reuses_partial(self):
        blob = synthetic_zip()
        sha = hashlib.sha256(blob).hexdigest()
        with tempfile.TemporaryDirectory(prefix="r29-gradle-retry-") as td:
            root = Path(td)
            with local_http_zip(blob, fail_first=2) as (url, state):
                script = fixture_script(root, url, sha)
                result = self.run_wrapper(script, root)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(state["requests"], 3)
                self.assertIn("R29_FAKE_GRADLE_EXECUTED", result.stdout)
                cached = root / "isolated-user-home/.gradle/locallab-bootstrap"
                self.assertEqual(hashlib.sha256(
                    (cached / "gradle-8.10.2-bin.zip").read_bytes()
                ).hexdigest(), sha)
                self.assertFalse(list(cached.glob("*.part-*")))
                # A second run must use the verified installed fake Gradle.
                second = self.run_wrapper(script, root)
                self.assertEqual(second.returncode, 0, second.stderr)
                self.assertEqual(state["requests"], 3)

    def test_invalid_sha_never_executes_any_distribution(self):
        blob = synthetic_zip()
        wrong_sha = "0" * 64
        with tempfile.TemporaryDirectory(prefix="r29-gradle-mismatch-") as td:
            root = Path(td)
            with local_http_zip(blob) as (url, state):
                script = fixture_script(root, url, wrong_sha)
                result = self.run_wrapper(script, root)
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(state["requests"], 1)
                self.assertIn("SHA-256 mismatch", result.stderr)
                self.assertNotIn("R29_FAKE_GRADLE_EXECUTED", result.stdout)
                cache = root / "isolated-user-home/.gradle/locallab-bootstrap"
                self.assertFalse((cache / "gradle-8.10.2-bin.zip").exists())
                self.assertFalse(list(cache.glob("*.part-*")))


if __name__ == "__main__":
    unittest.main()
