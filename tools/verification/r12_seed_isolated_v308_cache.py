#!/usr/bin/env python3
"""R12: seed a private v308 LocalLab client HOME from an authorized cache.

Never touches the source cache or user client JARs. Excludes per-user identity,
local account/price files, yaml and unknown cache/config files. Copies only
allowlisted static assets and typed game cache data; proves source/destination
sha256 equality for every file and leaves a machine-readable receipt.

No Java process is launched, stopped, or injected into. A seeded HOME is only
a precondition of future GUI acceptance, NOT GUI PASS.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import stat
import sys
from typing import Any

# Required for the exact LocalLab v308 v308-client cache baseline.
REQUIRED = frozenset({
    "main_file_cache.dat",
    "main_file_cache.idx0",
    "main_file_cache.idx1",
    "main_file_osrs.dat",
    "main_file_osrs.idx0",
    "configs/i.bin",
    "configs/e.bin",
})
ROOT_FILES = frozenset({
    "main_file_cache.dat", *(f"main_file_cache.idx{i}" for i in range(5)),
    "main_file_osrs.dat", *(f"main_file_osrs.idx{i}" for i in range(5)),
    "loc.dat", "loc.idx", "loc667.dat", "loc667.idx",
    "flo.dat", "flo2.dat", "obj.dat", "obj.idx",
    "map_index.dat", "osrs_loc.dat", "osrs_loc.idx",
    "osrs_flo.dat", "osrs_flo2.dat", "versions.dat",
})
DIR_RULES: dict[str, re.Pattern[str]] = {
    "configs": re.compile(r"^(?:[a-z0-9_]+\.bin|old_[a-z0-9_]+\.dat)$", re.I),
    "raw": re.compile(r"^\d+(?:\.dat)?$", re.I),
    "sprites": re.compile(r"^[a-z0-9_ -]+\.(?:dat|idx|png|jpg|gif|webp)$", re.I),
    "misc": re.compile(r"^\d+\.dat$", re.I),
    "old_osrs_anims": re.compile(r"^[a-z0-9_ -]+\.(?:dat|idx)$", re.I),
    "osrs_config": re.compile(r"^[a-z0-9_ -]+\.(?:dat|idx)$", re.I),
}
EXCLUDED = frozenset({
    "uid.dat", "market_prices.yaml",
    "accounts.properties", "credentials", "settings.properties",
})
MAX_FILE_BYTES = 200 * 1024 * 1024


class SeedError(RuntimeError):
    pass


def safe_entry(path: Path, is_directory: bool) -> None:
    s = path.lstat()
    reparse = getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0x400)
    if path.is_symlink() or (getattr(s, "st_file_attributes", 0) & reparse):
        raise SeedError("R12 reparse/symlink cache source rejected")
    expected = stat.S_ISDIR if is_directory else stat.S_ISREG
    if not expected(s.st_mode):
        raise SeedError("R12 non-regular cache entry rejected")


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def is_descendant(parent: Path, child: Path) -> bool:
    try:
        child.relative_to(parent)
        return True
    except ValueError:
        return False


def checked_paths(source: Path, home: Path) -> tuple[Path, Path]:
    if not source.is_absolute() or not home.is_absolute():
        raise SeedError("R12 input and output paths must be absolute")
    if not source.exists() or not source.is_dir() or source.name != ".spawnpk":
        raise SeedError("R12 cache source must be an existing .spawnpk directory")
    if home.exists() or home.is_symlink():
        raise SeedError("R12 isolated HOME already exists; refusing overwrite")
    parent = home.parent
    if not parent.is_dir():
        raise SeedError("R12 isolated HOME parent must already exist")
    safe_entry(source, True)
    safe_entry(parent, True)
    real_home = Path.home().resolve()
    src = source.resolve(strict=True)
    dest = home.resolve(strict=False)
    if (dest == real_home or dest == src.parent or
        is_descendant(src, dest) or is_descendant(dest, src) or
        dest == src):
        raise SeedError("R12 HOME overlaps source cache or real user profile")
    # Do not write under an existing real user .spawnpk tree or its parent
    # as that could interfere with the live client config.
    if parent == src or is_descendant(src, parent):
        raise SeedError("R12 private HOME parent inside source cache")
    return src, dest


def enumerate_assets(source: Path) -> list[tuple[str, Path, int]]:
    result: list[tuple[str, Path, int]] = []
    for child in sorted(source.iterdir(), key=lambda x: x.name):
        if child.is_symlink():
            raise SeedError("R12 source contains symlink")
        if child.name in EXCLUDED:
            continue
        if child.is_file():
            safe_entry(child, False)
            if child.name in ROOT_FILES:
                size = child.stat().st_size
                if size > MAX_FILE_BYTES:
                    raise SeedError("R12 cache file exceeds size ceiling")
                result.append((child.name, child, size))
        elif child.is_dir():
            if child.name not in DIR_RULES:
                continue
            safe_entry(child, True)
            # top-level static asset directories may contain nested subfolders.
            for directory, dirs, files in os.walk(child, followlinks=False):
                here = Path(directory)
                safe_entry(here, True)
                for directory_name in dirs:
                    safe_entry(here / directory_name, True)
                for filename in files:
                    path = here / filename
                    safe_entry(path, False)
                    if DIR_RULES[child.name].fullmatch(filename) is None:
                        continue
                    size = path.stat().st_size
                    if size > MAX_FILE_BYTES:
                        raise SeedError("R12 cache asset exceeds size ceiling")
                    relative = path.relative_to(source).as_posix()
                    result.append((relative, path, size))
    result.sort(key=lambda row: row[0])
    names = [name for name, _, _ in result]
    if len(names) != len(set(names)):
        raise SeedError("R12 duplicate cache asset paths")
    missing = REQUIRED.difference(names)
    if missing:
        raise SeedError("R12 required v308 cache assets missing: " + ", ".join(sorted(missing)))
    return result


def seed(source: Path, home: Path) -> dict[str, Any]:
    src, destination = checked_paths(source, home)
    files = enumerate_assets(src)
    if not files:
        raise SeedError("R12 no cache assets selected")
    digests: list[tuple[str, int, str]] = []
    copied = 0
    created = False
    try:
        # Atomic create with no replacement; abort if another process raced
        # our earlier nonexistent-destination check.
        destination.mkdir(exist_ok=False)
        created = True
        cache = destination / ".spawnpk"
        cache.mkdir()
        (destination / ".spawnpk-data").mkdir()
        for relative, origin, expected_size in files:
            # Only relative, allowlisted asset paths may enter the private root.
            if Path(relative).is_absolute() or ".." in Path(relative).parts:
                raise SeedError("R12 invalid relative cache asset")
            target = cache.joinpath(*relative.split("/"))
            target.parent.mkdir(parents=True, exist_ok=True)
            safe_entry(origin, False)
            if origin.stat().st_size != expected_size:
                raise SeedError("R12 source asset size changed during snapshot")
            before = sha256(origin)
            with origin.open("rb") as reader, target.open("xb") as writer:
                shutil.copyfileobj(reader, writer, length=1024 * 1024)
                writer.flush()
                os.fsync(writer.fileno())
            after_source = sha256(origin)
            after_target = sha256(target)
            if before != after_target or before != after_source:
                raise SeedError("R12 source/destination SHA mismatch during snapshot")
            copied += expected_size
            digests.append((relative, expected_size, before))
        # Recheck source after copying. A fixed digest lookup keeps the
        # 10k+ entry source scan linear rather than quadratic.
        # Live v312 clients may change assets during the snapshot; fail closed.
        verified_digest = {name: value for name, _, value in digests}
        for relative, origin, expected_size in files:
            expected = verified_digest[relative]
            safe_entry(origin, False)
            if origin.stat().st_size != expected_size or sha256(origin) != expected:
                raise SeedError("R12 live source changed before snapshot finalized")
        inventory = hashlib.sha256()
        for path, size, digest in digests:
            inventory.update(f"{path}\0{size}\0{digest}\n".encode("utf-8"))
        receipt = {
            "result": "R12_ISOLATED_V308_CACHE_SEED_PASS",
            "source_preserved": True,
            "user_identity_copied": False,
            "live_profiles_modified": False,
            "existing_processes_stopped": 0,
            "game_processes_started": 0,
            "gui_acceptance_performed": False,
            "isolated_home": str(destination),
            "source_cache_file_count": len(digests),
            "copied_bytes": copied,
            "sha256_inventory": inventory.hexdigest(),
            "required_cache_files_verified": sorted(REQUIRED),
            "excluded_ids_and_unknown_files": True,
            "next_step": "R10 exact-head source/client/server and process/port checks still required",
        }
        (destination / "r12-cache-seed-receipt.json").write_text(
            json.dumps(receipt, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        return receipt
    except BaseException:
        # Our output path was absent at admission, created by us; only
        # that exact path is eligible for cleanup. Never touch the source.
        if created:
            shutil.rmtree(destination)
        raise


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-cache", required=True, type=Path)
    parser.add_argument("--isolated-home", required=True, type=Path)
    args = parser.parse_args(argv)
    try:
        answer = seed(args.source_cache.expanduser(), args.isolated_home.expanduser())
        print(json.dumps(answer, indent=2, sort_keys=True))
        return 0
    except (SeedError, OSError, ValueError) as e:
        print(json.dumps({
            "result": "R12_ISOLATED_V308_CACHE_SEED_BLOCKED",
            "error": f"{type(e).__name__}: {e}",
            "game_processes_started": 0,
            "existing_processes_stopped": 0,
            "gui_acceptance_performed": False,
        }, indent=2, sort_keys=True), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
