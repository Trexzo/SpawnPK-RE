#!/usr/bin/env python3
"""R20: reproducible, offline 179-test original-v308 classpath compatibility.

This invokes ONLY the sealed spk.local.*Test main classes, never the original
client's graphical main class or a game launcher. The original client JAR is
used solely as a SHA-pinned, read-only Java classpath dependency.

Every child is a fresh Java JVM with a newly owned private user.home.
No existing Java processes, firewall settings, live account profiles or
real client installations are modified or stopped.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import time

ORIGINAL_V308_SHA256 = (
    "854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6"
)
EXPECTED_TEST_COUNT = 179
EXPECTED_REPO_URL = "https://github.com/Trexzo/SpawnPK-RE.git"
SHA_RE = re.compile(r"[0-9a-f]{40,64}\Z")


class AdmissionDenied(RuntimeError):
    """Refuse an unverified or unsafe offline parity input."""


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def sealed_manifest(script: str) -> tuple[str, ...]:
    match = re.search(r"\$tests=@\((.*?)\r?\n\)\r?\n", script, re.DOTALL)
    if match is None:
        raise AdmissionDenied("R20 manifest block not found")
    names = re.findall(r"'([A-Za-z0-9_]+Test)'", match.group(1))
    if len(names) != EXPECTED_TEST_COUNT or len(set(names)) != EXPECTED_TEST_COUNT:
        raise AdmissionDenied(
            "R20 manifest changed: expected 179 distinct exact test names"
        )
    if names[0] != "NormalInventoryDragEquipTest" or (
        names[-1] != "R85GenericC2SProbeIntegrationTest"
    ):
        raise AdmissionDenied("R20 manifest boundaries unexpected")
    return tuple(names)


def git_output(repo: Path, *args: str) -> str:
    try:
        call = subprocess.run(
            ["git", "-C", str(repo), *args],
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
            timeout=12, check=False,
        )
    except (OSError, subprocess.TimeoutExpired) as error:
        raise AdmissionDenied(f"R20 unable to read Git identity: {error}") from error
    if call.returncode != 0:
        raise AdmissionDenied(
            "R20 Git validation failed: " + " ".join(args)
        )
    return call.stdout.strip()


def safe_private_parent(root: Path, repo: Path) -> Path:
    if not root.is_absolute() or not root.is_dir() or root.is_symlink():
        raise AdmissionDenied("R20 private parent must be a real absolute directory")
    canonical = root.resolve(strict=True)
    if canonical == Path.home().resolve() or canonical == repo:
        raise AdmissionDenied("R20 cannot use user home or repository as private parent")
    if str(canonical) == canonical.anchor or repo in canonical.parents:
        raise AdmissionDenied("R20 private parent must be outside repository subtree")
    # A temporary parent may be an ancestor of the isolated repository.\n    # Existing parent components can redirect paths, especially on Windows
    # junctions. Reject observable symlinks; not a race-free sandbox.
    for part in (root, *root.parents):
        if part.is_symlink():
            raise AdmissionDenied("R20 private parent has symlink ancestor")
    return canonical


def verify_inputs(
    repo: Path, expected_head: str, jar: Path, jar_sha: str,
    client: Path, java: Path, private_parent: Path,
) -> tuple[Path, Path, Path, Path, tuple[str, ...]]:
    if not repo.is_dir() or not (repo / ".git").exists():
        raise AdmissionDenied("R20 isolated Git repository not found")
    repo = repo.resolve(strict=True)
    if not re.fullmatch(r"[0-9a-f]{40}", expected_head):
        raise AdmissionDenied("R20 expected commit must be exact 40-hex SHA")
    if git_output(repo, "rev-parse", "HEAD") != expected_head:
        raise AdmissionDenied("R20 Git source head mismatch")
    if git_output(repo, "remote", "get-url", "origin") != EXPECTED_REPO_URL:
        raise AdmissionDenied("R20 repository origin mismatch")
    if git_output(repo, "diff", "--name-only", "HEAD", "--",
                  "server/src", "server/build.gradle", "RUN_V5185_FULL_SELFTEST.ps1"):
        raise AdmissionDenied("R20 relevant tracked source files modified")
    if not re.fullmatch(r"[0-9a-f]{64}", jar_sha):
        raise AdmissionDenied("R20 complete server JAR SHA256 required")
    expected_jar = repo / "server" / "build" / "SpawnPKLocalServer.jar"
    if not jar.is_file() or jar.resolve(strict=True) != expected_jar.resolve():
        raise AdmissionDenied("R20 server JAR not at canonical checkout build path")
    if sha256_file(jar) != jar_sha:
        raise AdmissionDenied("R20 server JAR hash mismatch")
    if not client.is_file() or client.is_symlink():
        raise AdmissionDenied("R20 original client JAR missing or symlink")
    if sha256_file(client) != ORIGINAL_V308_SHA256:
        raise AdmissionDenied("R20 exact original v308 SHA256 mismatch")
    if not java.is_file() or java.is_symlink() or java.name.lower() not in (
        "java", "java.exe"
    ):
        raise AdmissionDenied("R20 dedicated Java executable missing or not named java")
    if not (repo / "server" / "build" / "classes" / "java" / "test"
            / "spk" / "local" / "NormalInventoryDragEquipTest.class").is_file():
        raise AdmissionDenied("R20 compiled main-method test fixtures missing")
    parent = safe_private_parent(private_parent, repo)
    manifest = repo / "RUN_V5185_FULL_SELFTEST.ps1"
    names = sealed_manifest(manifest.read_text(encoding="utf-8"))
    try:
        version = subprocess.run(
            [str(java.resolve(strict=True)), "-version"],
            stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            text=True, timeout=12, check=False,
        )
    except (OSError, subprocess.TimeoutExpired) as error:
        raise AdmissionDenied(f"R20 private Java failed: {error}") from error
    if version.returncode != 0 or re.search(
        r'\bversion "11[."]|\bopenjdk 11\b', version.stderr + version.stdout,
    ) is None:
        raise AdmissionDenied("R20 requires a working private Java 11 runtime")
    return repo, expected_jar, client.resolve(strict=True), parent, names


def tail(path: Path, limit: int = 2500) -> str:
    # Bounded error diagnostic, never blindly publish large test logs.
    with path.open("rb") as stream:
        stream.seek(0, 2)
        stream.seek(max(0, stream.tell() - limit))
        return stream.read().decode("utf-8", errors="replace")


def run_suite(
    repo: Path, jar: Path, jar_sha: str,
    client: Path, java: Path, parent: Path,
    names: tuple[str, ...], max_tests: int | None,
    timeout_seconds: int,
) -> dict[str, object]:
    owned = Path(tempfile.mkdtemp(prefix="spawnpk-r20-offline-", dir=str(parent)))
    profile = owned / "private-user-home"
    logs = owned / "logs"
    profile.mkdir()
    logs.mkdir()
    # Never read or mutate the real user home. All subprocesses receive
    # their own USERPROFILE/HOME and Java -Duser.home.
    env = os.environ.copy()
    env["USERPROFILE"] = str(profile)
    env["HOME"] = str(profile)
    env["APPDATA"] = str(profile / "AppData" / "Roaming")
    env["LOCALAPPDATA"] = str(profile / "AppData" / "Local")
    env.pop("JAVA_TOOL_OPTIONS", None)
    env.pop("_JAVA_OPTIONS", None)
    env.pop("JDK_JAVA_OPTIONS", None)
    for folder in ("APPDATA", "LOCALAPPDATA"):
        Path(env[folder]).mkdir(parents=True, exist_ok=True)

    run_names = names[:max_tests] if max_tests else names
    classpath = os.pathsep.join((
        str(jar),
        str(repo / "server" / "build" / "classes" / "java" / "test"),
        str(client),
    ))
    started = time.monotonic()
    receipt_path = owned / "r20-offline-parity-receipt.json"
    result: dict[str, object] = {
        "result": "R20_ORIGINAL_V308_OFFLINE_UNVERIFIED",
        "tests_requested": len(run_names),
        "tests_total": EXPECTED_TEST_COUNT,
        "tests_passed": 0,
        "original_v308_sha256": ORIGINAL_V308_SHA256,
        "server_jar_sha256": jar_sha,
        "private_workspace": str(owned),
        "client_gui_started": False,
        "existing_client_processes_stopped": 0,
    }
    try:
        for index, name in enumerate(run_names, 1):
            args = [
                str(java), "-Duser.home=" + str(profile),
                "-Djava.net.preferIPv4Stack=true",
            ]
            if name in (
                "VoidglassR2ConfigPatchToolTest",
                "VoidglassR3ConfigPatchToolTest",
            ):
                args += [
                    "-Dspk.voidglass.configDir="
                    + str(profile / ".spawnpk" / "configs")
                ]
            args += ["-cp", classpath, "spk.local." + name]
            out = logs / (f"{index:03d}-{name}.stdout.log")
            err = logs / (f"{index:03d}-{name}.stderr.log")
            try:
                with out.open("wb") as output, err.open("wb") as errors:
                    done = subprocess.run(
                        args, cwd=repo, env=env,
                        stdin=subprocess.DEVNULL,
                        stdout=output, stderr=errors,
                        timeout=timeout_seconds, check=False,
                    )
            except (OSError, subprocess.TimeoutExpired) as failure:
                raise AdmissionDenied(
                    f"R20 test {index}/{len(run_names)} {name} failed to run: "
                    + type(failure).__name__
                ) from failure
            if done.returncode != 0:
                raise AdmissionDenied(
                    f"R20 failed test {index}/{len(run_names)} {name}: "
                    f"exit={done.returncode}; stdout={tail(out)}; stderr={tail(err)}"
                )
            result["tests_passed"] = index
            if index % 25 == 0:
                print(f"R20_ORIGINAL_V308_OFFLINE_PROGRESS {index}/{len(run_names)}",
                      flush=True)
        if len(run_names) == EXPECTED_TEST_COUNT:
            result["result"] = "R20_ORIGINAL_V308_OFFLINE_179_PASS"
        else:
            result["result"] = "R20_ORIGINAL_V308_OFFLINE_PARTIAL_PASS"
    except BaseException as failure:
        result["result"] = "R20_ORIGINAL_V308_OFFLINE_FAILED"
        result["error"] = str(failure)
        raise
    finally:
        result["elapsed_seconds"] = round(time.monotonic() - started, 2)
        result["input_unchanged"] = (
            sha256_file(client) == ORIGINAL_V308_SHA256
            and sha256_file(jar) == jar_sha
        )
        receipt_path.write_text(
            json.dumps(result, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        if not result["input_unchanged"]:
            raise AdmissionDenied("R20 input JAR changed during offline tests")
    return result


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, required=True)
    parser.add_argument("--source-head", required=True)
    parser.add_argument("--server-jar", type=Path, required=True)
    parser.add_argument("--server-sha256", required=True)
    parser.add_argument("--original-v308", type=Path, required=True)
    parser.add_argument("--java11", type=Path, required=True)
    parser.add_argument("--private-parent", type=Path, required=True)
    parser.add_argument("--plan-only", action="store_true",
                        help="Validate identities without running any test JVM")
    parser.add_argument("--max-tests", type=int,
                        help="Development smoke; never marks a full suite PASS")
    parser.add_argument("--test-timeout", type=int, default=35)
    args = parser.parse_args(argv)
    try:
        if args.max_tests is not None and not 1 <= args.max_tests <= 179:
            raise AdmissionDenied("R20 --max-tests range must be 1..179")
        if not 5 <= args.test_timeout <= 120:
            raise AdmissionDenied("R20 test timeout must be 5..120 seconds")
        repo, jar, client, parent, names = verify_inputs(
            args.repo, args.source_head, args.server_jar,
            args.server_sha256, args.original_v308,
            args.java11, args.private_parent,
        )
        if args.plan_only:
            print(json.dumps({
                "result": "R20_ORIGINAL_V308_OFFLINE_PREFLIGHT_PASS",
                "manifest_count": len(names),
                "client_sha256": ORIGINAL_V308_SHA256,
                "server_jar_sha256": args.server_sha256,
                "new_java_processes_started": 0,
            }, sort_keys=True, indent=2))
            return 0
        result = run_suite(
            repo, jar, args.server_sha256, client, args.java11,
            parent, names, args.max_tests, args.test_timeout,
        )
        print(json.dumps(result, sort_keys=True, indent=2))
        return 0
    except (AdmissionDenied, OSError, ValueError) as failure:
        print(json.dumps({
            "result": "R20_ORIGINAL_V308_OFFLINE_BLOCKED",
            "error": str(failure),
            "client_gui_started": False,
            "existing_client_processes_stopped": 0,
        }, sort_keys=True, indent=2), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
