#!/usr/bin/env python3
"""R27: READ-ONLY, pinned v308 graphical bootstrap structure audit.

This never loads, extracts or executes a JAR. It verifies that the exact
original/airgap clients really contain the manifest-selected Java GUI entry,
Guice wiring, Swing UI, and Windows native graphics dependencies.

Potential bytecode references are *not* runtime reachability or verified
graphics. This is a stricter launch *precondition* for a future guest-only
manual GUI trial, NOT a green light to run any client on the host.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import struct
import sys
import zipfile

from r22_v308_classfile_native_egress_inventory import parse_class

ORIGINAL_SHA = "854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6"
AIRGAP_SHA = "024fad774453430bb964076b98d460a6dae821ee31100322d104e30d9a9c97a7"
MAIN_CLASS = "rs.gui.Launcher"
MAIN_PATH = "rs/gui/Launcher.class"
CLASS_PATHS = (
    MAIN_PATH, "rs/p/a.class", "rs/ui/f.class", "rs/Client.class",
)
WINDOWS_AMD64_NATIVE = (
    "windows/x64/org/lwjgl/lwjgl.dll",
    "windows/x64/org/lwjgl/opengl/lwjgl_opengl.dll",
    "com/sun/jna/win32-x86-64/jnidispatch.dll",
    "net/runelite/rlawt/windows-amd64/rlawt.dll",
)
EXPECTED_CLASSES = 10472
EXPECTED_CLASS_MAJOR = 53


class BootstrapDenied(RuntimeError):
    pass


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def manifest_main(data: bytes) -> str:
    if len(data) > 16384:
        raise BootstrapDenied("R27 manifest unreasonably large")
    # Java manifest continuations are deliberately disallowed in this
    # exact pinned v308 format so there is no ambiguous Main-Class.
    text = data.decode("utf-8", errors="strict")
    if any(line.startswith(" ") for line in text.splitlines()):
        raise BootstrapDenied("R27 ambiguous continued manifest")
    fields = [line.split(": ", 1)[1] for line in text.splitlines()
              if line.startswith("Main-Class: ")]
    if len(fields) != 1:
        raise BootstrapDenied("R27 manifest must contain exactly one Main-Class")
    return fields[0].strip()


def inspect(path: Path, expected_sha: str) -> dict:
    if not path.is_absolute() or not path.is_file() or path.is_symlink():
        raise BootstrapDenied("R27 pinned JAR unavailable/linked")
    if sha256_file(path) != expected_sha:
        raise BootstrapDenied("R27 pinned v308 SHA256 differs")
    with zipfile.ZipFile(path, "r") as jar:
        names = jar.namelist()
        if len(names) != len(set(names)):
            raise BootstrapDenied("R27 duplicate ZIP entry")
        if "META-INF/MANIFEST.MF" not in names:
            raise BootstrapDenied("R27 no manifest")
        entry = manifest_main(jar.read("META-INF/MANIFEST.MF"))
        if entry != MAIN_CLASS:
            raise BootstrapDenied("R27 incorrect GUI Main-Class")
        classes = [n for n in names if n.endswith(".class")]
        if len(classes) != EXPECTED_CLASSES:
            raise BootstrapDenied("R27 unexpected number of v308 classes")
        for name in (*CLASS_PATHS, *WINDOWS_AMD64_NATIVE):
            if name not in names:
                raise BootstrapDenied("R27 missing GUI/core/native entry: " + name)
        links = {}
        majors = {}
        for cls in CLASS_PATHS:
            info = jar.getinfo(cls)
            if info.file_size > 16 * 1024 * 1024:
                raise BootstrapDenied("R27 impossible large GUI class")
            raw = jar.read(cls)
            if raw[:4] != b"\xca\xfe\xba\xbe":
                raise BootstrapDenied("R27 invalid GUI class header")
            major = struct.unpack(">H", raw[6:8])[0]
            if major != EXPECTED_CLASS_MAJOR:
                raise BootstrapDenied("R27 unexpected GUI Java class major")
            refs, _ = parse_class(raw)
            links[cls] = refs
            majors[cls] = major
        native_info = {
            n: {"size": jar.getinfo(n).file_size,
                "sha256": hashlib.sha256(jar.read(n)).hexdigest()}
            for n in WINDOWS_AMD64_NATIVE
        }
        if any(info["size"] < 10_000 for info in native_info.values()):
            raise BootstrapDenied("R27 Windows native library implausibly small")
        if ("rs/gui/M", "a", "()V") not in links[MAIN_PATH]:
            raise BootstrapDenied("R27 launcher lacks Swing initialization")
        if ("rs/p/a", "a", "()V") not in links[MAIN_PATH]:
            raise BootstrapDenied("R27 launcher lacks Guice bootstrap call")
        if not any(owner == "com/google/inject/Guice" and
                   method == "createInjector"
                   for owner, method, _ in links["rs/p/a.class"]):
            raise BootstrapDenied("R27 Guice injector call absent")
        if not any(owner == "com/google/inject/Injector" and
                   method == "getInstance"
                   for owner, method, _ in links["rs/p/a.class"]):
            raise BootstrapDenied("R27 DI GUI creation call absent")
        if not any(owner == "javax/swing/SwingUtilities" and
                   method in ("invokeAndWait", "invokeLater")
                   for owner, method, _ in links["rs/ui/f.class"]):
            raise BootstrapDenied("R27 Swing UI entry call absent")
        if sha256_file(path) != expected_sha:
            raise BootstrapDenied("R27 JAR changed during read-only inspection")
        return {
            "sha256": expected_sha,
            "main_class": entry,
            "class_count": len(classes),
            "class_major": majors,
            "windows_amd64_native": native_info,
            "bootstrap_references_present": True,
            "client_gui_started": False,
        }


def compare(original: dict, airgap: dict) -> dict:
    if original["main_class"] != airgap["main_class"]:
        raise BootstrapDenied("R27 original/airgap GUI entry mismatch")
    if original["class_major"] != airgap["class_major"]:
        raise BootstrapDenied("R27 original/airgap GUI class bytecode mismatch")
    if original["class_count"] != airgap["class_count"]:
        raise BootstrapDenied("R27 original/airgap class inventory count mismatch")
    # These binary dependencies must not silently change with the airgap
    # networking patch. Classfile content may intentionally differ.
    if original["windows_amd64_native"] != airgap["windows_amd64_native"]:
        raise BootstrapDenied("R27 airgap unexpectedly changed Windows native libraries")
    return {
        "result": "R27_PINNED_V308_REAL_GUI_BOOTSTRAP_STATIC_AUDIT_PASS",
        "original_v308_sha256": ORIGINAL_SHA,
        "certified_airgap_v308_sha256": AIRGAP_SHA,
        "main_class": MAIN_CLASS,
        "class_count": airgap["class_count"],
        "gui_class_major": EXPECTED_CLASS_MAJOR,
        "java11_class_compatible": True,
        "injected_gui_chain": [
            "rs.gui.Launcher.main",
            "rs.gui.M.a",
            "rs.p.a.a",
            "com.google.inject.Guice.createInjector",
            "com.google.inject.Injector.getInstance",
            "rs.ui.f SwingUtilities",
        ],
        "required_windows_amd64_native_files": list(WINDOWS_AMD64_NATIVE),
        "native_entries_unchanged_by_airgap": True,
        "windows_sandbox_started": False,
        "client_gui_started": False,
        "opengl_rendering_verified": False,
        "local_game_login_verified": False,
        "network_isolation_verified": False,
        "note": (
            "Classfile/manifest structure only. Does not establish dynamic "
            "execution, any graphics rendering or sandbox isolation."
        ),
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--original-v308", type=Path, required=True)
    parser.add_argument("--certified-airgap-v308", type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        report = compare(
            inspect(args.original_v308, ORIGINAL_SHA),
            inspect(args.certified_airgap_v308, AIRGAP_SHA),
        )
        print(json.dumps(report, indent=2, sort_keys=True))
        return 0
    except (BootstrapDenied, ValueError, OSError, zipfile.BadZipFile) as err:
        print(json.dumps({
            "result": "R27_PINNED_V308_GUI_BOOTSTRAP_AUDIT_BLOCKED",
            "error": str(err),
            "client_gui_started": False,
            "network_isolation_verified": False,
        }, indent=2, sort_keys=True), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
