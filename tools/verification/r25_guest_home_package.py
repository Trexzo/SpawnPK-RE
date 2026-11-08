#!/usr/bin/env python3
"""R25: stage guest-only writable-HOME hydration script into fresh R24 package.

This only writes a brand-new, SHA-pinned offline Windows Sandbox review
package inside invocation-owned Temp, after R24 independently rehashes
10,764 isolated static cache files. It does NOT run Windows Sandbox, Java,
LocalLab server, original graphical v308 client, or guest script.

The new PowerShell file must be invoked manually INSIDE a real offline guest.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import shutil
import sys
from typing import Any

import r23_windows_sandbox_offline_package as r23
import r24_stage_guest_static_cache as r24


GUEST_SOURCE = "r25_guest_writable_home.ps1"
GUEST_TARGET = "guest-private-home.ps1"
WSB_NAME = "SpawnPK-R25-NetworkDisabled-WritableGuestHome-REVIEW-ONLY.wsb"


class R25Denied(RuntimeError):
    pass


def extend_package(base: dict[str, Any], source: Path,
                   expected_jdk: Path) -> dict[str, Any]:
    """Only operates on an already newly-created R24 package it owns."""
    output = Path(base["package_directory"])
    assets = output / "assets"
    previous_wsb = Path(base["sandbox_configuration"])
    if (not output.is_dir() or not assets.is_dir() or
            output != assets.parent or previous_wsb.parent != output or
            previous_wsb.name !=
            "SpawnPK-R24-NetworkDisabled-StaticCache-REVIEW-ONLY.wsb"):
        raise R25Denied("R25 R24 package output location invalid")
    if (not source.is_file() or source.is_symlink() or
            (assets / GUEST_TARGET).exists()):
        raise R25Denied("R25 guest HOME script source/target unsafe")
    r23.assess_wsb(previous_wsb.read_text(encoding="utf-8"),
                   assets.resolve(strict=True), expected_jdk)
    if base.get("result") != (
        "R24_STATIC_GAME_CACHE_SANDBOX_PACKAGE_PREPARED_NO_LAUNCH"
    ) or base.get("static_cache_inventory_sha256") != r24.R12_EXPECTED_INVENTORY:
        raise R25Denied("R25 requires completed R24 cache package")
    if base.get("user_profile_or_cache_mapped") is True:
        raise R25Denied("R25 refuses any mapped user profile or live cache")
    # Older R23 receipts used an ambiguous cache field. R24 maps a
    # verified isolated COPY, never a live user cache; use explicit keys.
    base.pop("user_profile_or_cache_mapped", None)
    manifest = assets / r24.MANIFEST_NAME
    if (not manifest.is_file() or
            r23.digest(manifest) != base.get("static_cache_manifest_sha256")):
        raise R25Denied("R25 inherited R24 cache manifest pin mismatch")
    shutil.copyfile(source, assets / GUEST_TARGET)
    if r23.digest(source) != r23.digest(assets / GUEST_TARGET):
        raise R25Denied("R25 copied guest HOME script hash mismatch")

    target_wsb = output / WSB_NAME
    if target_wsb.exists():
        raise R25Denied("R25 WSB target already exists")
    previous_wsb.rename(target_wsb)
    r23.assess_wsb(target_wsb.read_text(encoding="utf-8"),
                   assets.resolve(strict=True), expected_jdk)
    base.update({
        "result": "R25_GUEST_WRITABLE_HOME_PACKAGE_PREPARED_NO_LAUNCH",
        "sandbox_configuration": str(target_wsb),
        "wsb_sha256": r23.digest(target_wsb),
        "guest_home_hydration_script": r23.GUEST_ASSETS
            + "\\" + GUEST_TARGET,
        "guest_home_hydration_sha256": r23.digest(assets / GUEST_TARGET),
        "guest_home_hydration_executed": False,
        "guest_home_created": False,
        "host_profile_or_live_cache_mapped": False,
        "guest_network_and_cache_preflight_executed": False,
        "graphical_client_started": False,
        "server_started": False,
        "game_started": False,
        "note": (
            "This is a HOST-side package only. Manual guest-only hydration "
            "requires R23/R24 guest network and cache checks, exact operator "
            "approval, and writable output inside disposable guest Temp. "
            "Windows Sandbox and the game have NOT been started."
        ),
    })
    (output / "receipt.json").write_text(
        json.dumps(base, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    return base


def stage(values: dict[str, Any], r12_home: Path,
          files: list[tuple[str, Path, int]]) -> dict[str, Any]:
    package = None
    try:
        package = r24.append_static_cache(values, r12_home, files)
        candidate = (values["repo"] / "tools" / "verification" /
                     GUEST_SOURCE)
        return extend_package(package, candidate, values["jdk"])
    except BaseException:
        # R24 creates a unique invocation-owned path; removal is allowed
        # ONLY for this same-stage newly created package after failure.
        if package is not None:
            root = Path(package["package_directory"])
            if root.name.startswith("spawnpk-r23-offline-"):
                shutil.rmtree(root)
        raise


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", required=True, type=Path)
    parser.add_argument("--source-head", required=True)
    parser.add_argument("--server-jar", required=True, type=Path)
    parser.add_argument("--server-sha256", required=True)
    parser.add_argument("--certified-airgap", required=True, type=Path)
    parser.add_argument("--private-jdk", required=True, type=Path)
    parser.add_argument("--output-parent", required=True, type=Path)
    parser.add_argument("--r12-isolated-home", required=True, type=Path)
    parser.add_argument("--plan-only", action="store_true")
    args = parser.parse_args(argv)
    try:
        values = r23.validate(
            args.repo, args.source_head, args.server_jar,
            args.server_sha256, args.certified_airgap, args.private_jdk,
            args.output_parent,
        )
        if r23.git(values["repo"], "diff", "--name-only", "HEAD", "--",
                   "tools/verification/r25_guest_home_package.py",
                   "tools/verification/r25_guest_writable_home.ps1",
                   "tools/verification/r24_stage_guest_static_cache.py"):
            raise R25Denied("R25 tracked guest hydration source modified")
        source = (values["repo"] / "tools" / "verification" /
                  GUEST_SOURCE)
        if not source.is_file() or source.is_symlink():
            raise R25Denied("R25 guest hydration source unavailable")
        home, files = r24.validate_r12_home(args.r12_isolated_home)
        if args.plan_only:
            answer = {
                "result": "R25_GUEST_HOME_PACKAGE_INPUT_PREFLIGHT_PASS",
                "static_cache_files": len(files),
                "static_cache_inventory_sha256": r24.R12_EXPECTED_INVENTORY,
                "files_copied": 0,
                "windows_sandbox_started": False,
                "guest_home_created": False,
            }
        else:
            answer = stage(values, home, files)
        print(json.dumps(answer, indent=2, sort_keys=True))
        return 0
    except (r23.AdmissionDenied, r24.CacheAdmissionDenied,
            R25Denied, OSError, ValueError, KeyError) as error:
        print(json.dumps({
            "result": "R25_GUEST_HOME_PACKAGE_BLOCKED",
            "error": str(error),
            "game_started": False, "sandbox_started": False,
        }, indent=2, sort_keys=True), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
