#!/usr/bin/env python3
"""R11: reconstruct pinned v308 localhost/airgap JARs only in a NEW private dir.

No original/proprietary JAR is stored in this repository. The exact-v308
derivative recipe lives in tools/runtime/build_v308_local_clients.py, and was
recovered from the owner's existing SpawnPK-Src source tree (source digest
6db991b3982b19a7ac11c1efc77a0ab6b21c0fe1f5d085cbb02c00bac7cc7cc8,
CRLF). Only the source recipe, not copyrighted client bytes, is committed.

The original patched builder intentionally only writes to the directory named
'local-client' alongside its owning repository. To avoid overwriting ANY
pre-existing user local-client directory, this driver COPIES that builder into
a fresh, invocation-owned temporary directory and runs the copy there. The
original input JAR is opened read-only and checked again after completion.

Neither script launches Java/Minecraft/game clients or touches live profiles.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import stat
import subprocess
import sys
import tempfile
from typing import Any
import zipfile


ORIGINAL_SHA = "854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6"
LOCALHOST_SHA = "15ceb89669ddfe0a666e65b4a5291705af692a50e479bbde17e751eceb7fd23e"
AIRGAP_SHA = "024fad774453430bb964076b98d460a6dae821ee31100322d104e30d9a9c97a7"
FORMAT = "spawnpk-v308-local-client-patch-v1"
FORBIDDEN = (b"spawnpk.net", b"spawnpk.org", b"cloudfront.net", b"execute-api.", b"amazonaws.com")


class AdmissionError(RuntimeError):
    pass


def digest(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for part in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(part)
    return h.hexdigest()


def verify_input(original: Path, expected: str = ORIGINAL_SHA) -> None:
    if not original.is_file() or original.is_symlink():
        raise AdmissionError("original v308 reference missing, not regular or symlink")
    found = digest(original)
    if found != expected:
        raise AdmissionError(f"R11_ORIGINAL_PIN_REJECT expected={expected} actual={found}")


def verified_new_parent(parent: Path) -> Path:
    if not parent.is_absolute():
        raise AdmissionError("private output parent must be an absolute path")
    try:
        item = parent.lstat()
    except OSError as e:
        raise AdmissionError("private output parent does not exist") from e
    if not stat.S_ISDIR(item.st_mode) or parent.is_symlink():
        raise AdmissionError("private output parent not an ordinary directory")
    return parent


def check_manifest(manifest: dict[str, Any]) -> None:
    scalar_expected = {
        "format": FORMAT,
        "inputSha256": ORIGINAL_SHA,
        "localhostSha256": LOCALHOST_SHA,
        "airgapSha256": AIRGAP_SHA,
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
    }
    if any(manifest.get(key) != val for key, val in scalar_expected.items()):
        raise AdmissionError("R11 reconstructed manifest incompatible with certified v308 authority")
    local = manifest.get("localhostChangedEntries")
    air = manifest.get("airgapChangedEntries")
    if not isinstance(local, dict) or set(local) != {"rs/Client.class", "rs/f/a.class"}:
        raise AdmissionError("R11 localhost class-set drift")
    if not isinstance(air, dict) or len(air) != 11 or not set(local).issubset(air):
        raise AdmissionError("R11 airgap changed class-set drift")
    for row in list(local.values()) + list(air.values()):
        if (not isinstance(row, dict) or
            not all(isinstance(row.get(key), str) and len(row[key]) == 64
                    for key in ("preSha256", "postSha256"))):
            raise AdmissionError("R11 malformed class pre/post identity")


def verify_jar_delta(
    original_jar: Path,
    derivative_jar: Path,
    expected_changes: dict[str, dict[str, str]],
) -> int:
    """Independently check JAR entry order, all changed pre/post and no extra edits."""
    with zipfile.ZipFile(original_jar) as original, zipfile.ZipFile(derivative_jar) as derivative:
        before = original.namelist()
        after = derivative.namelist()
        if before != after or len(before) != len(set(before)):
            raise AdmissionError("R11 JAR entry inventory/order differs from original")
        changed = 0
        for name in before:
            raw = original.read(name)
            new = derivative.read(name)
            if derivative.getinfo(name).compress_type != zipfile.ZIP_STORED:
                raise AdmissionError("R11 client derivative must use deterministic STORED ZIP")
            if raw == new:
                if name in expected_changes:
                    raise AdmissionError(f"R11 declared changed class was identical: {name}")
                continue
            if name not in expected_changes:
                raise AdmissionError(f"R11 undeclared JAR entry modification: {name}")
            record = expected_changes[name]
            if (hashlib.sha256(raw).hexdigest() != record["preSha256"]
                    or hashlib.sha256(new).hexdigest() != record["postSha256"]):
                raise AdmissionError(f"R11 class transformation digest mismatch: {name}")
            changed += 1
            if derivative_jar.name == "client-airgap.jar" and name.endswith(".class"):
                lower = new.lower()
                if any(token in lower for token in FORBIDDEN):
                    raise AdmissionError(f"R11 known external authority remains: {name}")
        if changed != len(expected_changes):
            raise AdmissionError(f"R11 changed class count mismatch: {changed}")
        return changed


def recover(original: Path, private_parent: Path, python: str) -> dict[str, Any]:
    verify_input(original)
    parent = verified_new_parent(private_parent)
    source_recipe = Path(__file__).resolve().parents[1] / "runtime" / "build_v308_local_clients.py"
    if not source_recipe.is_file() or source_recipe.is_symlink():
        raise AdmissionError("R11 versioned, unmodified deterministic client builder missing")

    # mkdtemp owns an entire previously absent private tree. NEVER request
    # builder output inside a user's existing LocalLab checkout.
    root = Path(tempfile.mkdtemp(prefix="spawnpk-r11-v308-private-", dir=str(parent)))
    try:
        recipe = root / "tools" / "runtime" / "build_v308_local_clients.py"
        recipe.parent.mkdir(parents=True)
        shutil.copyfile(source_recipe, recipe)
        source_hash = digest(source_recipe)
        if digest(recipe) != source_hash:
            raise AdmissionError("R11 private recipe snapshot changed")

        output = root / "local-client"
        command = [python, str(recipe), str(original), str(output)]
        try:
            finished = subprocess.run(
                command, capture_output=True, text=True, timeout=180,
                check=False, errors="replace",
            )
        except (OSError, subprocess.TimeoutExpired) as e:
            raise AdmissionError("R11 deterministic builder execution unavailable") from e

        if finished.returncode != 0 or (
            "V308_LOCAL_CLIENT_PATCH_PASS" not in finished.stdout or
            "V308_LOCAL_CLIENT_FINAL_VERIFY_PASS" not in finished.stdout
        ):
            raise AdmissionError(
                "R11 reconstruction did not pass original deterministic checks: "
                + (finished.stderr or finished.stdout)[-700:]
            )

        local = output / "client-localhost.jar"
        airgap = output / "client-airgap.jar"
        manifest_path = output / "v308-local-client-patch-manifest.json"
        if (not local.is_file() or not airgap.is_file() or
            not manifest_path.is_file()):
            raise AdmissionError("R11 private output missing required artifacts")
        if digest(local) != LOCALHOST_SHA or digest(airgap) != AIRGAP_SHA:
            raise AdmissionError("R11 private output pin mismatch")
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        check_manifest(manifest)

        verify_jar_delta(original, local, manifest["localhostChangedEntries"])
        verify_jar_delta(original, airgap, manifest["airgapChangedEntries"])
        verify_input(original)

        receipt = {
            "result": "R11_PRIVATE_V308_AIRGAP_RECONSTRUCTION_PASS",
            "gui_acceptance_performed": False,
            "game_processes_started": 0,
            "existing_processes_stopped": 0,
            "original_sha256": ORIGINAL_SHA,
            "localhost_sha256": LOCALHOST_SHA,
            "airgap_sha256": AIRGAP_SHA,
            "source_recipe_sha256": source_hash,
            "private_workspace": str(root),
            "private_airgap_jar": str(airgap),
            "private_localhost_jar": str(local),
            "manifest_sha256": digest(manifest_path),
            "external_game_endpoints_authorized": False,
            "original_rechecked_unchanged": True,
            "next_step": "R10 exact source/server and isolated GUI environment must still pass",
        }
        (root / "r11-reconstruction-receipt.json").write_text(
            json.dumps(receipt, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        return receipt
    except BaseException:
        # Only a new, invocation-owned workspace can be cleaned up here.
        # Never touch the original JAR, an existing local-client directory,
        # a user's account/profile, or a running game process.
        shutil.rmtree(root)
        raise


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--original-v308", required=True, type=Path)
    parser.add_argument("--private-parent", type=Path, default=Path(tempfile.gettempdir()))
    parser.add_argument("--python", default=sys.executable)
    args = parser.parse_args(argv)
    try:
        result = recover(
            args.original_v308.expanduser().resolve(),
            args.private_parent.expanduser().absolute(),
            args.python,
        )
        print(json.dumps(result, indent=2, sort_keys=True))
        return 0
    except (AdmissionError, OSError, ValueError, zipfile.BadZipFile) as e:
        print(json.dumps({
            "result": "R11_PRIVATE_V308_AIRGAP_RECONSTRUCTION_BLOCKED",
            "gui_acceptance_performed": False,
            "existing_processes_stopped": 0,
            "error": f"{type(e).__name__}: {e}",
        }, indent=2, sort_keys=True), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
