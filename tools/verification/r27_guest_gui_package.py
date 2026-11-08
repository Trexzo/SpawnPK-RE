#!/usr/bin/env python3
"""R27 read-only v308 GUI bootstrap auditing + no-launch guest packaging.

Builds a NEW R26-compatible Windows Sandbox package and stages an exact
GUI bootstrap audit receipt plus a manual GUI-window smoke PowerShell.
The host never runs the original or airgap GUI, nor installs/opens
Windows Sandbox. It is impossible to equate a static pass with real GUI
rendering or actual login/gameplay.
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
import r25_guest_home_package as r25
import r26_guest_server_package as r26
import r27_v308_gui_bootstrap_audit as r27

GUEST_SOURCE = "r27_guest_gui_window_smoke.ps1"
GUEST_TARGET = "guest-gui-window-smoke.ps1"
AUDIT_TARGET = "r27-v308-gui-bootstrap-audit.json"
WSB_NAME = "SpawnPK-R27-NetworkDisabled-GuiWindow-REVIEW-ONLY.wsb"


class R27PackageDenied(RuntimeError):
    pass


def extend_package(base: dict[str, Any], source: Path,
                   expected_jdk: Path, audit: dict) -> dict:
    root = Path(base["package_directory"])
    assets = root / "assets"
    previous = Path(base["sandbox_configuration"])
    if (not root.is_dir() or not assets.is_dir()
            or assets.parent != root
            or previous.parent != root
            or previous.name != r26.WSB_NAME
            or base.get("result") !=
            "R26_OFFLINE_GUEST_LOCAL_SERVER_SMOKE_PACKAGE_PREPARED"
            or base.get("guest_java_server_started") is not False
            or base.get("guest_home_created") is not False
            or base.get("static_cache_inventory_sha256") !=
                r24.R12_EXPECTED_INVENTORY):
        raise R27PackageDenied("R27 inherited R26 no-launch package invalid")
    if not source.is_file() or source.is_symlink():
        raise R27PackageDenied("R27 guest GUI script missing/linked")
    guest = assets / GUEST_TARGET
    target_audit = assets / AUDIT_TARGET
    next_wsb = root / WSB_NAME
    if any(x.exists() or x.is_symlink()
           for x in (guest, target_audit, next_wsb)):
        raise R27PackageDenied("R27 destination already present")
    r23.assess_wsb(previous.read_text(encoding="utf-8"),
                   assets.resolve(strict=True), expected_jdk)
    for filename, pin in (
        (r24.MANIFEST_NAME, "static_cache_manifest_sha256"),
        (r25.GUEST_TARGET, "guest_home_hydration_sha256"),
        (r26.GUEST_TARGET, "guest_server_smoke_sha256"),
        ("SpawnPKLocalServer.jar", "server_jar_sha256"),
    ):
        path = assets / filename
        if (not path.is_file() or path.is_symlink()
                or r23.digest(path) != base.get(pin)):
            raise R27PackageDenied("R27 inherited asset SHA pin changed: " + filename)
    if (audit.get("result") !=
        "R27_PINNED_V308_REAL_GUI_BOOTSTRAP_STATIC_AUDIT_PASS"
            or audit.get("client_gui_started") is not False
            or audit.get("certified_airgap_v308_sha256") != r27.AIRGAP_SHA):
        raise R27PackageDenied("R27 static original/airgap audit absent")
    if r23.digest(assets / "client-airgap.jar") != r27.AIRGAP_SHA:
        raise R27PackageDenied("R27 staged GUI JAR is not certified airgap")
    shutil.copyfile(source, guest)
    if r23.digest(source) != r23.digest(guest):
        raise R27PackageDenied("R27 GUI guest script changed while copying")
    target_audit.write_text(
        json.dumps(audit, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    previous.rename(next_wsb)
    r23.assess_wsb(next_wsb.read_text(encoding="utf-8"),
                   assets.resolve(strict=True), expected_jdk)
    base.update({
        "result": "R27_OFFLINE_V308_GUI_WINDOW_TRIAL_PACKAGE_PREPARED_NO_LAUNCH",
        "sandbox_configuration": str(next_wsb),
        "wsb_sha256": r23.digest(next_wsb),
        "gui_bootstrap_audit_sha256": r23.digest(target_audit),
        "guest_gui_script_sha256": r23.digest(guest),
        "guest_gui_script": r23.GUEST_ASSETS + "\\" + GUEST_TARGET,
        "guest_gui_trial_executed": False,
        "gui_window_observed": False,
        "graphical_client_started": False,
        "guest_java_server_started": False,
        "guest_home_created": False,
        "guest_network_isolation_verified": False,
        "opengl_rendering_verified": False,
        "authenticated_game_login_verified": False,
        "no_new_host_mapping": True,
        "host_clients_terminated": 0,
        "note": (
            "Only static original/airgap v308 GUI bootstrap contract and "
            "offline guest package creation were verified. GUI launch "
            "requires a future network-disabled Windows Sandbox guest, "
            "guest R25/R26 receipts and separate exact manual approval. "
            "No graphical/gameplay acceptance or VM egress proof claimed."
        ),
    })
    (root / "receipt.json").write_text(
        json.dumps(base, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    return base


def stage(values: dict[str, Any], home: Path, files: list,
          audit: dict) -> dict:
    created: Path | None = None
    try:
        base = r26.stage(values, home, files)
        created = Path(base["package_directory"])
        source = values["repo"] / "tools" / "verification" / GUEST_SOURCE
        return extend_package(base, source, values["jdk"], audit)
    except BaseException:
        if created and created.name.startswith("spawnpk-r23-offline-"):
            shutil.rmtree(created)
        raise


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, required=True)
    parser.add_argument("--source-head", required=True)
    parser.add_argument("--server-jar", type=Path, required=True)
    parser.add_argument("--server-sha256", required=True)
    parser.add_argument("--original-v308", type=Path, required=True)
    parser.add_argument("--certified-airgap", type=Path, required=True)
    parser.add_argument("--private-jdk", type=Path, required=True)
    parser.add_argument("--output-parent", type=Path, required=True)
    parser.add_argument("--r12-isolated-home", type=Path, required=True)
    parser.add_argument("--plan-only", action="store_true")
    args = parser.parse_args(argv)
    try:
        values = r23.validate(
            args.repo, args.source_head, args.server_jar, args.server_sha256,
            args.certified_airgap, args.private_jdk, args.output_parent,
        )
        if r23.git(values["repo"], "diff", "--name-only", "HEAD", "--",
                   "tools/verification/r27_v308_gui_bootstrap_audit.py",
                   "tools/verification/r27_guest_gui_window_smoke.ps1",
                   "tools/verification/r27_guest_gui_package.py"):
            raise R27PackageDenied("R27 launch or audit tracked source modified")
        guest = values["repo"] / "tools" / "verification" / GUEST_SOURCE
        if not guest.is_file() or guest.is_symlink():
            raise R27PackageDenied("R27 guest GUI script source missing")
        static = r27.compare(
            r27.inspect(args.original_v308, r27.ORIGINAL_SHA),
            r27.inspect(args.certified_airgap, r27.AIRGAP_SHA),
        )
        home, files = r24.validate_r12_home(args.r12_isolated_home)
        if args.plan_only:
            report = {
                "result": "R27_V308_GUI_BOOTSTRAP_GUEST_PACKAGE_PREFLIGHT_PASS",
                "gui_bootstrap_static": static["result"],
                "static_cache_files": len(files),
                "files_copied": 0,
                "gui_started": False,
                "sandbox_started": False,
            }
        else:
            report = stage(values, home, files, static)
        print(json.dumps(report, sort_keys=True, indent=2))
        return 0
    except (r23.AdmissionDenied, r24.CacheAdmissionDenied,
            r25.R25Denied, r26.R26Denied, r27.BootstrapDenied,
            R27PackageDenied, OSError, ValueError, KeyError) as err:
        print(json.dumps({
            "result": "R27_GUI_BOOTSTRAP_GUEST_PACKAGE_BLOCKED",
            "error": str(err), "gui_started": False,
            "sandbox_started": False,
        }, sort_keys=True), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
