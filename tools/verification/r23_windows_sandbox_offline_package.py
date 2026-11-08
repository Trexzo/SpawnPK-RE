#!/usr/bin/env python3
"""R23: prepare (NEVER start) a network-disabled Windows Sandbox test package.

The Windows Sandbox host configuration maps only an invocation-owned staging
directory and a dedicated R13 private JDK directory, both READ-ONLY. It
disables networking, clipboard, GPU sharing and peripheral redirection.

No original/proprietary JAR or real user-profile/cache directory is mapped.
The R11 certified airgap derivative is copied into a temporary staging
directory after exact SHA256 verification. The server JAR is SHA-pinned.
Windows Sandbox must be installed/enabled by an authorized administrator
separately; this tool cannot enable it, launch it, or certify guest networking.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path, PureWindowsPath
import re
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET

AIRGAP_SHA = "024fad774453430bb964076b98d460a6dae821ee31100322d104e30d9a9c97a7"
JAVA_SHA = "b2484c2889365b93fb48434e12c8f9bc21ca6cd5d4eb2eae39a46b17ced3c8ef"
JAVAW_SHA = "0a4656c826032ffe28d58ebfa93e85d4c099c50a1857f29416461848f0c6adaa"
OFFLINE_WSB = "SpawnPK-R23-NetworkDisabled-REVIEW-ONLY.wsb"
GUEST_ASSETS = r"C:\SPKR23\assets"
GUEST_JDK = r"C:\SPKR23\jdk"
HEX40 = re.compile(r"[0-9a-f]{40}\Z")
HEX64 = re.compile(r"[0-9a-f]{64}\Z")


class AdmissionDenied(RuntimeError):
    pass


def digest(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def reject_symlink_ancestors(path: Path) -> None:
    for p in (path, *path.parents):
        if p.is_symlink():
            raise AdmissionDenied("R23 symlink ancestor refused: " + str(p))


def validated_file(file: Path, expected: str, name: str) -> Path:
    if not file.is_absolute() or not file.is_file():
        raise AdmissionDenied("R23 missing absolute input " + name)
    reject_symlink_ancestors(file)
    resolved = file.resolve(strict=True)
    if digest(resolved) != expected:
        raise AdmissionDenied("R23 SHA256 mismatch: " + name)
    return resolved


def validated_dir(folder: Path, label: str) -> Path:
    if not folder.is_absolute() or not folder.is_dir():
        raise AdmissionDenied("R23 missing absolute directory " + label)
    reject_symlink_ancestors(folder)
    return folder.resolve(strict=True)


def git(repo: Path, *args: str) -> str:
    result = subprocess.run(
        ["git", "-C", str(repo), *args], stdout=subprocess.PIPE,
        stderr=subprocess.PIPE, text=True, timeout=15, check=False,
    )
    if result.returncode:
        raise AdmissionDenied("R23 failed Git source identity validation")
    return result.stdout.strip()


def validate(
    repo: Path, head: str, server: Path, server_sha: str,
    airgap: Path, jdk: Path, parent: Path,
) -> dict:
    repo = validated_dir(repo, "source repo")
    jdk = validated_dir(jdk, "R13 private JDK")
    parent = validated_dir(parent, "temporary output parent")

    if not HEX40.fullmatch(head) or not HEX64.fullmatch(server_sha):
        raise AdmissionDenied("R23 exact 40/64-character source/JAR pin required")
    if (repo / ".git").is_dir() is False or (
        git(repo, "rev-parse", "HEAD") != head or
        git(repo, "remote", "get-url", "origin") !=
        "https://github.com/Trexzo/SpawnPK-RE.git"
    ):
        raise AdmissionDenied("R23 source checkout identity refused")
    if git(repo, "diff", "--name-only", "HEAD", "--",
           "server/src", "server/build.gradle",
           "tools/verification/r23_windows_sandbox_offline_package.py",
           "tools/verification/r23_guest_preflight.ps1"):
        raise AdmissionDenied("R23 tracked execution source is modified")
    expected_server = repo / "server" / "build" / "SpawnPKLocalServer.jar"
    server = validated_file(server, server_sha, "local server JAR")
    if server != expected_server.resolve():
        raise AdmissionDenied("R23 server JAR not under selected source checkout")
    airgap = validated_file(airgap, AIRGAP_SHA, "certified v308 airgap JAR")
    # Private JDK already created and pinned by R13 / R21. Never map
    # a shared or globally installed Java that could affect live v312 PIDs.
    if jdk.name != "jdk" or not jdk.parent.name.startswith(
        "spawnpk-r13-owned-jdk-"
    ):
        raise AdmissionDenied("R23 selected JDK is not a private R13 snapshot")
    validated_file(jdk / "bin" / "java.exe", JAVA_SHA, "private java.exe")
    validated_file(jdk / "bin" / "javaw.exe", JAVAW_SHA, "private javaw.exe")
    if (parent == Path.home().resolve() or parent == repo
            or repo in parent.parents or str(parent) == parent.anchor):
        raise AdmissionDenied("R23 output parent cannot be user home/repo/root")
    guest_script = repo / "tools" / "verification" / "r23_guest_preflight.ps1"
    if not guest_script.is_file() or guest_script.is_symlink():
        raise AdmissionDenied("R23 guest preflight source missing")
    return {
        "repo": repo, "head": head, "server": server,
        "server_sha": server_sha, "airgap": airgap, "jdk": jdk,
        "parent": parent, "guest_script": guest_script,
    }


def wsb_xml(host_assets: Path, host_jdk: Path) -> str:
    if not host_assets.is_absolute() or not host_jdk.is_absolute():
        raise AdmissionDenied("R23 WSB mapping paths must be absolute")
    root = ET.Element("Configuration")
    for name, value in (
        ("Networking", "Disable"),
        ("VGpu", "Disable"),
        ("AudioInput", "Disable"),
        ("VideoInput", "Disable"),
        ("PrinterRedirection", "Disable"),
        ("ClipboardRedirection", "Disable"),
        ("ProtectedClient", "Enable"),
        ("MemoryInMB", "6144"),
    ):
        ET.SubElement(root, name).text = value
    mappings = ET.SubElement(root, "MappedFolders")
    for host, guest in ((host_assets, GUEST_ASSETS), (host_jdk, GUEST_JDK)):
        entry = ET.SubElement(mappings, "MappedFolder")
        ET.SubElement(entry, "HostFolder").text = str(host)
        ET.SubElement(entry, "SandboxFolder").text = guest
        ET.SubElement(entry, "ReadOnly").text = "true"
    # NO LogonCommand: guest cannot start Java or GUI by opening .wsb.
    ET.indent(root, space="  ")
    return '<?xml version="1.0" encoding="utf-8"?>\n' + ET.tostring(
        root, encoding="unicode") + "\n"


def assess_wsb(xml: str, expected_assets: Path, expected_jdk: Path) -> None:
    tree = ET.fromstring(xml)
    if tree.tag != "Configuration":
        raise AdmissionDenied("R23 bad WSB root")
    needed = {
        "Networking": "Disable", "VGpu": "Disable",
        "AudioInput": "Disable", "VideoInput": "Disable",
        "PrinterRedirection": "Disable", "ClipboardRedirection": "Disable",
        "ProtectedClient": "Enable", "MemoryInMB": "6144",
    }
    for key, value in needed.items():
        if [node.text for node in tree.findall(key)] != [value]:
            raise AdmissionDenied("R23 dangerous WSB option: " + key)
    if tree.find("LogonCommand") is not None:
        raise AdmissionDenied("R23 auto-start command prohibited")
    if set(x.tag for x in tree) != set(needed) | {"MappedFolders"}:
        raise AdmissionDenied("R23 unexpected WSB configuration setting")
    folders = tree.findall("./MappedFolders/MappedFolder")
    if len(folders) != 2:
        raise AdmissionDenied("R23 expected two read-only mappings")
    expected = ((str(expected_assets), GUEST_ASSETS),
                (str(expected_jdk), GUEST_JDK))
    for item, (host, guest) in zip(folders, expected):
        fields = {x.tag: x.text for x in item}
        if fields != {
            "HostFolder": host, "SandboxFolder": guest, "ReadOnly": "true"
        }:
            raise AdmissionDenied("R23 unsafe host-folder mapping")


def build_package(values: dict) -> dict:
    root = Path(tempfile.mkdtemp(prefix="spawnpk-r23-offline-", dir=values["parent"]))
    # The tool owns only this newly created directory; no automatic launch.
    assets = root / "assets"
    assets.mkdir()
    client_dest = assets / "client-airgap.jar"
    server_dest = assets / "SpawnPKLocalServer.jar"
    shutil.copyfile(values["airgap"], client_dest)
    shutil.copyfile(values["server"], server_dest)
    if digest(client_dest) != AIRGAP_SHA or digest(server_dest) != values["server_sha"]:
        raise AdmissionDenied("R23 staged client/server copy mismatch")
    shutil.copyfile(values["guest_script"], assets / "guest-preflight.ps1")
    xml = wsb_xml(assets.resolve(), values["jdk"])
    assess_wsb(xml, assets.resolve(), values["jdk"])
    wsb = root / OFFLINE_WSB
    wsb.write_text(xml, encoding="utf-8")
    record = {
        "result": "R23_OFFLINE_SANDBOX_PACKAGE_PREPARED_NO_LAUNCH",
        "source_head": values["head"],
        "client_airgap_sha256": AIRGAP_SHA,
        "server_jar_sha256": values["server_sha"],
        "java_sha256": JAVA_SHA,
        "javaw_sha256": JAVAW_SHA,
        "sandbox_configuration": str(wsb),
        "package_directory": str(root),
        "networking": "Disable",
        "read_only_host_mappings": 2,
        "no_logon_command": True,
        "clipboard_sharing": False,
        "original_client_jar_included": False,
        "user_profile_or_cache_mapped": False,
        "sandbox_started": False,
        "game_started": False,
        "feature_installed": False,
        "guest_loopback_connectivity_verified": False,
        "full_host_guest_isolation_proven": False,
        "manual_guest_preflight": GUEST_ASSETS + r"\guest-preflight.ps1",
        "note": (
            "This config is reviewed but UNTESTED inside actual Windows Sandbox. "
            "The host's Windows Sandbox feature is unavailable; no process is "
            "started. The guest must be preflighted separately before future "
            "LocalLab server/client admission; the actual client cache is not "
            "bundled in this first package."
        ),
    }
    (root / "receipt.json").write_text(
        json.dumps(record, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return record


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, required=True)
    parser.add_argument("--source-head", required=True)
    parser.add_argument("--server-jar", type=Path, required=True)
    parser.add_argument("--server-sha256", required=True)
    parser.add_argument("--certified-airgap", type=Path, required=True)
    parser.add_argument("--private-jdk", type=Path, required=True)
    parser.add_argument("--output-parent", type=Path, required=True)
    parser.add_argument("--plan-only", action="store_true")
    args = parser.parse_args(argv)
    try:
        values = validate(args.repo, args.source_head,
                          args.server_jar, args.server_sha256,
                          args.certified_airgap, args.private_jdk,
                          args.output_parent)
        if args.plan_only:
            result = {
                "result": "R23_OFFLINE_SANDBOX_PACKAGE_PREFLIGHT_PASS",
                "sources_sha_verified": True,
                "networking": "Disable",
                "only_read_only_host_mappings": True,
                "files_copied": 0,
                "windows_sandbox_started": False,
            }
        else:
            result = build_package(values)
        print(json.dumps(result, indent=2, sort_keys=True))
        return 0
    except (AdmissionDenied, OSError, ValueError, ET.ParseError,
            subprocess.TimeoutExpired) as failure:
        print(json.dumps({
            "result": "R23_OFFLINE_SANDBOX_PACKAGE_BLOCKED",
            "error": str(failure),
            "sandbox_started": False, "game_started": False,
        }, indent=2, sort_keys=True), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
