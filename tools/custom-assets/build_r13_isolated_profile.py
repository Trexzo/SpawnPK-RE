#!/usr/bin/env python3
"""Build the isolated Issue #9 R13 GUI-acceptance profile.

External authority supplied by the user:
- exact v308 client JAR;
- authorized exact .spawnpk directory.

Everything added by this builder is source-controlled CUSTOM_LOCALLAB probe data.
The source cache is read-only authority and selected source hashes are rechecked after
the build.
"""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

CLIENT_SHA = (
    "854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6"
)
MODEL_SHA = (
    "6cf617b5e14e60b5bc58d4f1c72e11476f09382d40a72f49be122009157c7fad"
)
TEXTURE_ARCHIVE_SEMANTIC_SHA = (
    "596f6e438a2f3dd8141d1d5c757a50ff38ad30dbee361921b163ae73d599b4f8"
)
CERTIFIED_R12_TEXTURE_ARCHIVE_SHA = (
    "8d5ca9da0d629960a41401fa873cbfd1a0c61727214588f87578f045e98afc14"
)

ITEM_ID = 29999
MODEL_ID = 79999
TEXTURE_ID = 278


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_module(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"cannot import {path}")

    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


def run(command: list[str], cwd: Path | None = None) -> None:
    print("RUN", " ".join(str(part) for part in command))
    subprocess.run(command, cwd=cwd, check=True)


def packed_model_occupied(idx1: Path, model_id: int) -> bool:
    offset = model_id * 6

    if offset + 6 > idx1.stat().st_size:
        return False

    with idx1.open("rb") as handle:
        handle.seek(offset)
        record = handle.read(6)

    length = int.from_bytes(record[:3], "big")
    sector = int.from_bytes(record[3:], "big")
    return length != 0 or sector != 0


def validate_output_home(
    base: Path,
    output_home: Path,
    real_home: Path,
) -> None:
    source_home = base.parent.resolve()

    if (
        output_home == real_home
        or output_home == source_home
        or output_home == base
        or base in output_home.parents
    ):
        raise ValueError(
            "refusing real/source/cache-contained user.home as isolated output"
        )

    if output_home.exists():
        raise ValueError(
            f"output home already exists; refusing replacement: {output_home}"
        )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-spawnpk", required=True, type=Path)
    parser.add_argument("--client-jar", required=True, type=Path)
    parser.add_argument("--output-home", required=True, type=Path)
    parser.add_argument("--java", default="java")
    parser.add_argument("--javac", default="javac")
    args = parser.parse_args()

    here = Path(__file__).resolve().parent
    fixtures = here / "fixtures"

    base = args.base_spawnpk.resolve()
    client = args.client_jar.resolve()
    output_home = args.output_home.resolve()

    if base.name != ".spawnpk" or not base.is_dir():
        raise SystemExit(
            f"base must be an existing .spawnpk directory: {base}"
        )

    if sha256_file(client) != CLIENT_SHA:
        raise SystemExit("exact v308 client SHA mismatch")

    real_home = Path.home().resolve()

    try:
        validate_output_home(
            base,
            output_home,
            real_home,
        )
    except ValueError as exc:
        raise SystemExit(str(exc)) from exc

    required = (
        "configs/i.bin",
        "main_file_cache.dat",
        "main_file_cache.idx0",
        "main_file_cache.idx1",
    )

    for relative in required:
        if not (base / relative).is_file():
            raise SystemExit(f"missing base file: {relative}")

    if (
        (base / "raw" / str(MODEL_ID)).exists()
        or (base / "raw" / f"{MODEL_ID}.dat").exists()
    ):
        raise SystemExit("loose model 79999 collision")

    if packed_model_occupied(
        base / "main_file_cache.idx1",
        MODEL_ID,
    ):
        raise SystemExit("packed model 79999 collision")

    guarded = (
        "configs/i.bin",
        "main_file_cache.dat",
        "main_file_cache.idx0",
        "main_file_cache.idx1",
    )
    source_before = {
        relative: sha256_file(base / relative)
        for relative in guarded
    }

    work = Path(
        tempfile.mkdtemp(prefix="spawnpk-r13-build-")
    )

    try:
        cache = output_home / ".spawnpk"
        shutil.copytree(base, cache)
        (output_home / ".spawnpk-data").mkdir(parents=True)

        model_writer = load_module(
            "r13_model_writer",
            here / "compile_spawnpk_legacy_textured_skinned.py",
        )

        model = model_writer.compile_legacy_textured_skinned(
            (fixtures / "r13-probe.obj").read_text(encoding="utf-8"),
            (fixtures / "r13-probe.skins.json").read_text(
                encoding="utf-8"
            ),
            (fixtures / "r13-material.json").read_text(
                encoding="utf-8"
            ),
        )

        if hashlib.sha256(model).hexdigest() != MODEL_SHA:
            raise RuntimeError("model writer output SHA mismatch")

        (cache / "raw").mkdir(exist_ok=True)
        (cache / "raw" / f"{MODEL_ID}.dat").write_bytes(model)

        java_sources = (
            here / "R13ConfigBuilder.java",
            here / "R13CacheArchiveTool.java",
            here / "VerifyR13Profile.java",
        )

        classes = work / "classes"
        classes.mkdir()

        run(
            [
                args.javac,
                "-cp",
                str(client),
                "-d",
                str(classes),
                *[str(source) for source in java_sources],
            ]
        )

        classpath = f"{client}{os.pathsep}{classes}"

        source_config = cache / "configs/i.bin"
        built_config = work / "i.bin"

        run(
            [
                args.java,
                "-cp",
                classpath,
                "R13ConfigBuilder",
                str(source_config),
                str(built_config),
            ]
        )

        os.replace(built_config, source_config)

        cache_tool = [
            args.java,
            "-cp",
            classpath,
            "R13CacheArchiveTool",
        ]

        dat = cache / "main_file_cache.dat"
        idx0 = cache / "main_file_cache.idx0"
        base_archive = work / "textures-base.jag"
        new_archive = work / "textures-r13.jag"
        check_archive = work / "textures-check.jag"

        run(
            cache_tool
            + [
                "extract",
                str(dat),
                str(idx0),
                "1",
                "6",
                str(base_archive),
            ]
        )

        texture_builder = load_module(
            "r13_texture_builder",
            here / "build_r13_texture_archive.py",
        )
        texture_blob = texture_builder.build(
            base_archive.read_bytes(),
            texture_id=TEXTURE_ID,
        )

        texture_semantic_sha = (
            texture_builder.semantic_sha256(
                texture_blob
            )
        )

        if (
            texture_semantic_sha
            != TEXTURE_ARCHIVE_SEMANTIC_SHA
        ):
            raise RuntimeError(
                "texture archive semantic-body SHA mismatch"
            )

        new_archive.write_bytes(texture_blob)

        run(
            cache_tool
            + [
                "inject",
                str(dat),
                str(idx0),
                "1",
                "6",
                str(new_archive),
            ]
        )
        run(
            cache_tool
            + [
                "extract",
                str(dat),
                str(idx0),
                "1",
                "6",
                str(check_archive),
            ]
        )

        if check_archive.read_bytes() != texture_blob:
            raise RuntimeError(
                "texture archive injection readback mismatch"
            )

        run(
            [
                args.java,
                "-cp",
                classpath,
                "VerifyR13Profile",
                str(output_home),
            ]
        )

        source_after = {
            relative: sha256_file(base / relative)
            for relative in guarded
        }

        if source_before != source_after:
            raise RuntimeError(
                "source cache changed during isolated build"
            )

        manifest = {
            "format": "spawnpk-r13-isolated-profile-v1",
            "exactClientSha256": CLIENT_SHA,
            "baseSpawnpk": str(base),
            "baseGuardSha256": source_before,
            "itemId": ITEM_ID,
            "modelId": MODEL_ID,
            "modelSha256": MODEL_SHA,
            "textureId": TEXTURE_ID,
            "textureArchiveSemanticSha256": TEXTURE_ARCHIVE_SEMANTIC_SHA,
            "textureArchiveTransportSha256": hashlib.sha256(
                texture_blob
            ).hexdigest(),
            "certifiedR12TextureArchiveSha256": CERTIFIED_R12_TEXTURE_ARCHIVE_SHA,
            "outputConfigSha256": sha256_file(
                cache / "configs/i.bin"
            ),
            "outputCacheDatSha256": sha256_file(dat),
            "outputCacheIdx0Sha256": sha256_file(idx0),
        }

        (output_home / "R13_PROFILE_MANIFEST.json").write_text(
            json.dumps(
                manifest,
                indent=2,
                sort_keys=True,
            )
            + "\n",
            encoding="utf-8",
        )

        print(
            "R13_ISOLATED_PROFILE_BUILD_PASS "
            f"output={output_home} "
            f"configSha256={manifest['outputConfigSha256']} "
            f"modelSha256={MODEL_SHA} "
            f"textureArchiveSemanticSha256={TEXTURE_ARCHIVE_SEMANTIC_SHA} "
            "sourceUnchanged=true"
        )
        return 0

    except Exception:
        if output_home.exists():
            shutil.rmtree(output_home)
        raise

    finally:
        shutil.rmtree(work, ignore_errors=True)


if __name__ == "__main__":
    raise SystemExit(main())
