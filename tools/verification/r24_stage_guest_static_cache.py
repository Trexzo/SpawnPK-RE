#!/usr/bin/env python3
"""R24: stage only R12's pinned static v308 cache into an R23 offline guest.

Host-side only. It does NOT launch Windows Sandbox, a game, or a Java JVM.
The selected R12 profile must be an isolated, receipt-verified copy of the
allowlisted static game cache; NEVER map a live user's .spawnpk directory.

The staged cache is read-only via R23's existing mapped assets directory.
The future guest must independently verify assets before any client launch.
This cannot establish that arbitrary opaque binary asset contents are free
of private material; filename filtering and exact inventories are not
content-level privacy or security attestations.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import stat
import sys
import tempfile
from typing import Any

import r12_seed_isolated_v308_cache as r12
import r23_windows_sandbox_offline_package as r23

R12_EXPECTED_COUNT = 10764
R12_EXPECTED_BYTES = 209188225
R12_EXPECTED_INVENTORY = (
    "37cf52440fb46c44c8671470776e5950b5bdc6a8ecf470bd52bf71c87d5208f3"
)
STAGED_RELATIVE_ROOT = "cache/.spawnpk"
MANIFEST_NAME = "r24-static-cache-manifest.json"
GUEST_SCRIPT = "r24_guest_cache_preflight.ps1"


class CacheAdmissionDenied(RuntimeError):
    pass


def validate_r12_home(home: Path) -> tuple[Path, list[tuple[str, Path, int]]]:
    if not home.is_absolute():
        raise CacheAdmissionDenied("R24 isolated R12 HOME path must be absolute")
    temp_parent = Path(tempfile.gettempdir()).resolve(strict=True)
    if not home.is_dir() or home.is_symlink():
        raise CacheAdmissionDenied("R24 R12 isolated HOME missing or symlink")
    for ancestor in (home, *home.parents):
        if ancestor.is_symlink():
            raise CacheAdmissionDenied("R24 R12 HOME has symlink ancestor")
    home = home.resolve(strict=True)
    if home.parent != temp_parent or not home.name.startswith(
        "SpawnPK-R12-IsolatedV308-"
    ):
        raise CacheAdmissionDenied("R24 source must be direct private R12 Temp HOME")
    if home == Path.home().resolve():
        raise CacheAdmissionDenied("R24 cannot read user's actual HOME")
    if {x.name for x in home.iterdir()} != {
        ".spawnpk", ".spawnpk-data", "r12-cache-seed-receipt.json"
    }:
        raise CacheAdmissionDenied("R24 unexpected R12 HOME top-level entries")
    cache = home / ".spawnpk"
    user_data = home / ".spawnpk-data"
    r12.safe_entry(cache, True)
    r12.safe_entry(user_data, True)
    if list(user_data.iterdir()):
        raise CacheAdmissionDenied("R24 R12 user-data directory must be empty")
    record = json.loads(
        (home / "r12-cache-seed-receipt.json").read_text(encoding="utf-8")
    )
    if not (
        record.get("result") == "R12_ISOLATED_V308_CACHE_SEED_PASS"
        and Path(record.get("isolated_home", "")).resolve(strict=False) == home
        and record.get("source_cache_file_count") == R12_EXPECTED_COUNT
        and record.get("copied_bytes") == R12_EXPECTED_BYTES
        and record.get("sha256_inventory") == R12_EXPECTED_INVENTORY
        and record.get("user_identity_copied") is False
        and record.get("existing_processes_stopped") == 0
        and record.get("game_processes_started") == 0
    ):
        raise CacheAdmissionDenied("R24 R12 provenance receipt mismatch")

    files = r12.enumerate_assets(cache)
    if len(files) != R12_EXPECTED_COUNT:
        raise CacheAdmissionDenied("R24 wrong R12 static asset count")
    if sum(size for _, _, size in files) != R12_EXPECTED_BYTES:
        raise CacheAdmissionDenied("R24 R12 static asset byte count mismatch")
    allowed = {name for name, _, _ in files}
    # R12 selects filenames by allowlist, but R24 also refuses ANY excluded or
    # unknown extra file, regardless of whether a normal R12 scan skips it.
    dirs = set(r12.DIR_RULES)
    for path in cache.rglob("*"):
        relative = path.relative_to(cache).as_posix()
        if path.is_dir():
            r12.safe_entry(path, True)
            if path.relative_to(cache).parts[0] not in dirs:
                raise CacheAdmissionDenied("R24 unknown cache directory " + relative)
        else:
            r12.safe_entry(path, False)
            if relative not in allowed:
                raise CacheAdmissionDenied("R24 unknown cache file " + relative)
    # Do not trust a text receipt alone. Independently verify every
    # selected cache binary and the complete R12 inventory BEFORE staging.
    checked = []
    for relative, origin, size in files:
        r12.safe_entry(origin, False)
        if origin.stat().st_size != size:
            raise CacheAdmissionDenied("R24 input file changed size")
        checked.append((relative, size, r12.sha256(origin)))
    if inventory(checked) != R12_EXPECTED_INVENTORY:
        raise CacheAdmissionDenied("R24 source asset bytes differ from R12 pin")
    return home, files


def inventory(records: list[tuple[str, int, str]]) -> str:
    digest = hashlib.sha256()
    for relative, size, sha in records:
        digest.update(f"{relative}\0{size}\0{sha}\n".encode("utf-8"))
    return digest.hexdigest()


def append_static_cache(
    values: dict[str, Any], r12_home: Path,
    files: list[tuple[str, Path, int]],
) -> dict[str, Any]:
    # This creates a fresh R23 package in Temp, never updates a previous
    # R23 package or live user's HOME/cache. R23 maps only its own assets.
    parent_root: Path | None = None
    try:
        receipt = r23.build_package(values)
        parent_root = Path(receipt["package_directory"])
        assets = parent_root / "assets"
        cache_target = assets / "cache" / ".spawnpk"
        cache_target.mkdir(parents=True)
        entries: list[tuple[str, int, str]] = []
        for relative, original, expected_size in files:
            if Path(relative).is_absolute() or ".." in Path(relative).parts:
                raise CacheAdmissionDenied("R24 unsafe cache relative path")
            r12.safe_entry(original, False)
            if original.stat().st_size != expected_size:
                raise CacheAdmissionDenied("R24 input cache file changed size")
            before = r12.sha256(original)
            dest = cache_target.joinpath(*relative.split("/"))
            dest.parent.mkdir(parents=True, exist_ok=True)
            with original.open("rb") as source, dest.open("xb") as output:
                shutil.copyfileobj(source, output, length=1024 * 1024)
                output.flush()
                os.fsync(output.fileno())
            if (r12.sha256(original) != before or
                    r12.sha256(dest) != before):
                raise CacheAdmissionDenied("R24 cache file changed during stage")
            entries.append((relative, expected_size, before))
        if inventory(entries) != R12_EXPECTED_INVENTORY:
            raise CacheAdmissionDenied("R24 R12 inventory hash mismatch")
        # Re-verify the original R12 assets after the copy in case another
        # process silently changed them while this long staging was running.
        for (_, origin, size), (_, _, sha) in zip(files, entries):
            r12.safe_entry(origin, False)
            if origin.stat().st_size != size or r12.sha256(origin) != sha:
                raise CacheAdmissionDenied("R24 R12 source changed after stage")
        guest_source = values["repo"] / "tools" / "verification" / GUEST_SCRIPT
        if not guest_source.is_file() or guest_source.is_symlink():
            raise CacheAdmissionDenied("R24 guest static cache preflight missing")
        shutil.copyfile(guest_source, assets / "guest-cache-preflight.ps1")
        manifest = {
            "format": "SpawnPK-R24-Static-Game-Cache-Manifest-v1",
            "cache_file_count": R12_EXPECTED_COUNT,
            "cache_total_bytes": R12_EXPECTED_BYTES,
            "inventory_sha256": R12_EXPECTED_INVENTORY,
            "cache_root": r23.GUEST_ASSETS + r"\cache\.spawnpk",
            "file_entries": entries,
            "copy_of_r12_isolated_cache": True,
            "known_identity_files_copied": False,
            "opaque_binary_content_privacy_certified": False,
            "source_head": values["head"],
        }
        (assets / MANIFEST_NAME).write_text(
            json.dumps(manifest, separators=(",", ":"), ensure_ascii=False)
            + "\n", encoding="utf-8",
        )
        # R23 .wsb already maps the parent assets directory READ-ONLY.
        wsb_path = Path(receipt["sandbox_configuration"])
        r23.assess_wsb(
            wsb_path.read_text(encoding="utf-8"),
            assets.resolve(), values["jdk"],
        )
        receipt.update({
            "result": "R24_STATIC_GAME_CACHE_SANDBOX_PACKAGE_PREPARED_NO_LAUNCH",
            "r12_cache_source": str(r12_home),
            "static_cache_file_count": len(entries),
            "static_cache_bytes": R12_EXPECTED_BYTES,
            "static_cache_inventory_sha256": R12_EXPECTED_INVENTORY,
            "static_cache_copy_included": True,
            "real_player_home_mapped": False,
            "cache_mapped_readonly": True,
            "guest_cache_preflight": (
                r23.GUEST_ASSETS + r"\guest-cache-preflight.ps1"),
            "opaque_binary_content_privacy_certified": False,
            "guest_cache_preflight_executed": False,
            "game_started": False,
            "sandbox_started": False,
            "note": (
                "R24 stage contains filename-allowlisted binary game assets, "
                "not a full content-security or privacy attestation. The guest "
                "and its network/loopback/cache checks have NOT been run. "
                "No auto-start or original user profile mapping."
            ),
        })
        (parent_root / "receipt.json").write_text(
            json.dumps(receipt, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        return receipt
    except BaseException:
        if parent_root is not None:
            shutil.rmtree(parent_root)
        raise


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, required=True)
    parser.add_argument("--source-head", required=True)
    parser.add_argument("--server-jar", type=Path, required=True)
    parser.add_argument("--server-sha256", required=True)
    parser.add_argument("--certified-airgap", type=Path, required=True)
    parser.add_argument("--private-jdk", type=Path, required=True)
    parser.add_argument("--output-parent", type=Path, required=True)
    parser.add_argument("--r12-isolated-home", type=Path, required=True)
    parser.add_argument("--plan-only", action="store_true")
    args = parser.parse_args(argv)
    try:
        values = r23.validate(
            args.repo, args.source_head, args.server_jar,
            args.server_sha256, args.certified_airgap,
            args.private_jdk, args.output_parent,
        )
        if r23.git(values["repo"], "diff", "--name-only", "HEAD", "--",
                   "tools/verification/r24_stage_guest_static_cache.py",
                   "tools/verification/r24_guest_cache_preflight.ps1",
                   "tools/verification/r12_seed_isolated_v308_cache.py"):
            raise CacheAdmissionDenied("R24 source or guest preflight modified")
        home, files = validate_r12_home(args.r12_isolated_home)
        if args.plan_only:
            response = {
                "result": "R24_R12_GAME_CACHE_PREPARED_PREFLIGHT_PASS",
                "source_asset_files": len(files),
                "expected_bytes": R12_EXPECTED_BYTES,
                "expected_inventory": R12_EXPECTED_INVENTORY,
                "guest_launched": False,
                "files_copied": 0,
            }
        else:
            response = append_static_cache(values, home, files)
        print(json.dumps(response, indent=2, sort_keys=True))
        return 0
    except (r23.AdmissionDenied, r12.SeedError,
            CacheAdmissionDenied, OSError, ValueError, KeyError) as exc:
        print(json.dumps({
            "result": "R24_R12_GAME_CACHE_SANDBOX_PACKAGE_BLOCKED",
            "error": str(exc),
            "game_started": False,
            "sandbox_started": False,
        }, sort_keys=True), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
