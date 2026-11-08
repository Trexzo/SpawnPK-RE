#!/usr/bin/env python3
"""R26 host-side packaging ONLY of a guest-local owned server smoke tool.

R25 already stages an intact SHA-pinned 10,764-file static game cache,
certified v308 airgap client and private JDK, with two read-only host maps
and networking DISABLED. R26 adds a separate manual guest-only SERVER
test, never auto-runs Java or the VM and never changes the WSB network policy.

Actual guest-only R26 smoke requires the R25 private writable HOME,
private per-test 64-hex stdin token and explicit operator approval.
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

GUEST_SOURCE = "r26_guest_owned_server_smoke.ps1"
GUEST_TARGET = "guest-server-smoke.ps1"
WSB_NAME = "SpawnPK-R26-NetworkDisabled-OwnedServer-REVIEW-ONLY.wsb"


class R26Denied(RuntimeError):
    pass


def extend_package(base: dict[str, Any], source: Path,
                   expected_jdk: Path) -> dict[str, Any]:
    root = Path(base["package_directory"])
    assets = root / "assets"
    old_wsb = Path(base["sandbox_configuration"])
    if (not root.is_dir() or not assets.is_dir() or
            assets.parent != root or old_wsb.parent != root or
            old_wsb.name != r25.WSB_NAME):
        raise R26Denied("R26 not a newly prepared canonical R25 package")
    if (base.get("result") !=
            "R25_GUEST_WRITABLE_HOME_PACKAGE_PREPARED_NO_LAUNCH" or
            base.get("guest_home_created") is not False or
            base.get("game_started") is not False or
            base.get("static_cache_inventory_sha256") !=
            r24.R12_EXPECTED_INVENTORY):
        raise R26Denied("R26 R25 inherited state or cache inventory mismatch")
    if not source.is_file() or source.is_symlink():
        raise R26Denied("R26 guest server smoke script source missing or linked")
    dest = assets / GUEST_TARGET
    if dest.exists() or dest.is_symlink():
        raise R26Denied("R26 guest server smoke script destination exists")
    r23.assess_wsb(old_wsb.read_text(encoding="utf-8"),
                   assets.resolve(strict=True), expected_jdk)
    for path, recorded_digest in (
        (assets / r24.MANIFEST_NAME, base.get("static_cache_manifest_sha256")),
        (assets / r25.GUEST_TARGET, base.get("guest_home_hydration_sha256")),
        (assets / "SpawnPKLocalServer.jar", base.get("server_jar_sha256")),
    ):
        if (not path.is_file() or not isinstance(recorded_digest, str) or
                r23.digest(path) != recorded_digest):
            raise R26Denied("R26 inherited R25 asset digest no longer matches")

    shutil.copyfile(source, dest)
    if r23.digest(source) != r23.digest(dest):
        raise R26Denied("R26 guest server smoke script bytes changed in copy")
    next_wsb = root / WSB_NAME
    if next_wsb.exists():
        raise R26Denied("R26 output WSB already exists")
    old_wsb.rename(next_wsb)
    r23.assess_wsb(next_wsb.read_text(encoding="utf-8"),
                   assets.resolve(strict=True), expected_jdk)
    base.update({
        "result": "R26_OFFLINE_GUEST_LOCAL_SERVER_SMOKE_PACKAGE_PREPARED",
        "sandbox_configuration": str(next_wsb),
        "wsb_sha256": r23.digest(next_wsb),
        "guest_server_smoke_script": r23.GUEST_ASSETS
            + "\\" + GUEST_TARGET,
        "guest_server_smoke_sha256": r23.digest(dest),
        "guest_server_smoke_executed": False,
        "guest_home_created": False,
        "guest_java_server_started": False,
        "graphical_client_started": False,
        "authenticated_gameplay_verified": False,
        "host_java_processes_terminated": 0,
        "firewall_rules_changed": 0,
        "guest_network_isolation_verified": False,
        "note": (
            "R26 host-side SHA-pinned, no-launch offline package only. "
            "A future manual guest-only R26 smoke starts ONE private "
            "LocalLab server after R23/R24 preflights and R25 hydration. "
            "No Windows Sandbox or game client started here."
        ),
    })
    (root / "receipt.json").write_text(
        json.dumps(base, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    return base


def stage(values: dict[str, Any], r12_home: Path,
          files: list[tuple[str, Path, int]]) -> dict[str, Any]:
    created: Path | None = None
    try:
        base = r25.stage(values, r12_home, files)
        created = Path(base["package_directory"])
        source = values["repo"] / "tools" / "verification" / GUEST_SOURCE
        return extend_package(base, source, values["jdk"])
    except BaseException:
        # Remove ONLY an invocation-owned package created inside THIS stage.
        if created and created.name.startswith("spawnpk-r23-offline-"):
            shutil.rmtree(created)
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
                   "tools/verification/r26_guest_owned_server_smoke.ps1",
                   "tools/verification/r26_guest_server_package.py",
                   "tools/verification/r25_guest_home_package.py"):
            raise R26Denied("R26 source/guest smoke modified")
        guest_source = (values["repo"] / "tools" / "verification" /
                        GUEST_SOURCE)
        if not guest_source.is_file() or guest_source.is_symlink():
            raise R26Denied("R26 guest smoke source missing")
        home, files = r24.validate_r12_home(args.r12_isolated_home)
        if args.plan_only:
            receipt = {
                "result": "R26_GUEST_SERVER_PACKAGE_INPUT_PREFLIGHT_PASS",
                "static_cache_files": len(files),
                "static_cache_inventory_sha256": r24.R12_EXPECTED_INVENTORY,
                "files_copied": 0,
                "sandbox_started": False,
                "java_server_started": False,
            }
        else:
            receipt = stage(values, home, files)
        print(json.dumps(receipt, indent=2, sort_keys=True))
        return 0
    except (r23.AdmissionDenied, r24.CacheAdmissionDenied, r25.R25Denied,
            R26Denied, OSError, ValueError, KeyError) as exc:
        print(json.dumps({
            "result": "R26_GUEST_SERVER_PACKAGE_BLOCKED",
            "error": str(exc),
            "game_started": False,
            "sandbox_started": False,
            "server_started": False,
        }, sort_keys=True), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
