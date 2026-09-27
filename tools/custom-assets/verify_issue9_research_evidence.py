#!/usr/bin/env python3
"""Verify the exact preserved research inputs behind the Issue #9 v308 asset pipeline.

This verifier consumes only safe research packages supplied by the caller. It pins
the R10 compiler sources, successful R11 authored-source artifact, R12 texture/render
kit, and the source-controlled legacy textured+skinned model writer.

The model-writer proof is exact: the committed writer recompiles the preserved R11
OBJ/skin/material inputs from the R12 safe kit and must produce bytes identical to the
certified R11 generated model.
"""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import sys
import zipfile
from pathlib import Path

EXPECTED_ZIPS = {
    "r10": "a83712a70b532dee3fde91c623cabe19e3e785064d9e03c45181f142f6f5a2f6",
    "r11": "573b828ff42e2b8d563cd3fa985906d01f0625021f145a786783b805129d729b",
    "r12": "3143425a58240c424ba2ad189269627a0458500c6bde7e578619219e5a71a709",
}

R10_FILES = {
    "tools/core/obj_to_spawnpk_ffff_skin.py":
        "a37f135cd8cd8eb4c41c3c2a17212ea63244ed6afd654d7b682a551c88514bf0",
    "tools/core/compile_spawnpk_rig_v1.py":
        "338d0c7da4f74dc54919ca702c5c0579a1e43e278a483a0e67ed621ccae332ad",
    "tools/core/make_v308_frame_group_json.py":
        "c54bf61f839f3b2be88bb536542438b054988c4b66364ce6983a90f004c48a82",
    "tools/core/make_v308_animation_definition.py":
        "0c949209293c169e863c92e9b10c2a5a3b93384797136202c3ff0b34970745ed",
}

R11_SOURCE_FILES = {
    "r11-authored-rest.obj":
        "7780c05809ee4cd78849ae5ba41159eeb5a731fe1509a609e515f206afd5f6d9",
    "r11-authored-rest.obj.skins.json":
        "c52cd8a336f01a8b3230843f2b4b302940ac5f377ee512c4725cb246f06617a2",
    "r11-material.json":
        "1611a1821320c6522ddbe258b62da748fd5dc4c4dfe80ab4f61100c8e56ae7a7",
    "r11-rig-v1.json":
        "b116d03aca5deb8746deafed9755203d1032572c8def51cfc273927b0e953f70",
    "r11-texture-278.png":
        "bf3b7f8de3124ff504b6aaa9a133214f60011cd0c613a1b1b31048f61d7b7ef9",
}

R12_FILES = {
    "tools/build_spawnpk_texture_archive_278.py":
        "6a0f8c88f897a82090c8c20e6112cb628d57ae2e6e94db3998ae167e6b816c2d",
    "blender/r11-authored-rest.obj": R11_SOURCE_FILES["r11-authored-rest.obj"],
    "blender/r11-authored-rest.obj.skins.json": R11_SOURCE_FILES["r11-authored-rest.obj.skins.json"],
    "blender/r11-material.json": R11_SOURCE_FILES["r11-material.json"],
    "blender/r11-rig-v1.json": R11_SOURCE_FILES["r11-rig-v1.json"],
    "blender/r11-texture-278.png": R11_SOURCE_FILES["r11-texture-278.png"],
    "generated/r11-blender-textured-skinned-model-79999.dat":
        "6cf617b5e14e60b5bc58d4f1c72e11476f09382d40a72f49be122009157c7fad",
    "generated/custom-frame-group-3990.dat":
        "a0fbf04abdbaab315f87d7f43b3d8c41ec74e4758ccf63c2083a25393068aaa9",
    "generated/custom-animation-30009.bin":
        "b89428a67eae9ad273c459ef38d45027c33a5f4a18563de890fcd7bb1479eef9",
}


def sha256_bytes(blob: bytes) -> str:
    return hashlib.sha256(blob).hexdigest()


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def resolve_entry(names: list[str], suffix: str) -> str:
    matches = [name for name in names if name == suffix or name.endswith("/" + suffix)]
    if len(matches) != 1:
        raise ValueError(f"expected exactly one ZIP entry ending {suffix!r}, got {matches}")
    return matches[0]


def verify_zip(label: str, path: Path, expected_files: dict[str, str]) -> dict[str, str]:
    if not path.is_file():
        raise ValueError(f"{label}: ZIP not found: {path}")
    actual_zip = sha256_file(path)
    expected_zip = EXPECTED_ZIPS[label]
    if actual_zip != expected_zip:
        raise ValueError(
            f"{label}: ZIP SHA mismatch expected={expected_zip} actual={actual_zip}"
        )

    verified: dict[str, str] = {}
    with zipfile.ZipFile(path, "r") as zf:
        names = zf.namelist()
        for suffix, expected_sha in expected_files.items():
            entry = resolve_entry(names, suffix)
            blob = zf.read(entry)
            actual_sha = sha256_bytes(blob)
            if actual_sha != expected_sha:
                raise ValueError(
                    f"{label}: inner SHA mismatch entry={entry} "
                    f"expected={expected_sha} actual={actual_sha}"
                )
            verified[suffix] = actual_sha
    return verified


def load_model_writer(path: Path):
    if not path.is_file():
        raise ValueError(f"model writer not found: {path}")
    spec = importlib.util.spec_from_file_location(
        "spawnpk_legacy_textured_skinned_writer",
        path,
    )
    if spec is None or spec.loader is None:
        raise ValueError(f"cannot import model writer: {path}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    compiler = getattr(module, "compile_legacy_textured_skinned", None)
    if compiler is None:
        raise ValueError("model writer lacks compile_legacy_textured_skinned")
    return compiler


def verify_model_writer(writer_path: Path, r12_path: Path) -> str:
    compiler = load_model_writer(writer_path)

    with zipfile.ZipFile(r12_path, "r") as zf:
        names = zf.namelist()
        obj = zf.read(
            resolve_entry(names, "blender/r11-authored-rest.obj")
        ).decode("utf-8")
        skins = zf.read(
            resolve_entry(names, "blender/r11-authored-rest.obj.skins.json")
        ).decode("utf-8")
        material = zf.read(
            resolve_entry(names, "blender/r11-material.json")
        ).decode("utf-8")
        expected = zf.read(
            resolve_entry(
                names,
                "generated/r11-blender-textured-skinned-model-79999.dat",
            )
        )

    rebuilt = compiler(obj, skins, material)
    expected_sha = R12_FILES[
        "generated/r11-blender-textured-skinned-model-79999.dat"
    ]
    rebuilt_sha = sha256_bytes(rebuilt)

    if rebuilt_sha != expected_sha:
        raise ValueError(
            "legacy model writer SHA mismatch "
            f"expected={expected_sha} actual={rebuilt_sha}"
        )
    if rebuilt != expected:
        raise ValueError(
            "legacy model writer output differs from certified R12 model bytes"
        )

    return rebuilt_sha


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--r10-kit", required=True, type=Path)
    ap.add_argument("--r11-artifact", required=True, type=Path)
    ap.add_argument("--r12-kit", required=True, type=Path)
    ap.add_argument(
        "--model-writer",
        type=Path,
        default=Path(__file__).with_name(
            "compile_spawnpk_legacy_textured_skinned.py"
        ),
    )
    args = ap.parse_args()

    r10 = verify_zip("r10", args.r10_kit, R10_FILES)
    r11 = verify_zip("r11", args.r11_artifact, R11_SOURCE_FILES)
    r12 = verify_zip("r12", args.r12_kit, R12_FILES)

    for name, expected_sha in R11_SOURCE_FILES.items():
        r12_name = "blender/" + name
        if r11[name] != r12[r12_name] or r11[name] != expected_sha:
            raise ValueError(f"R11/R12 authored-source drift: {name}")

    legacy_model_sha = verify_model_writer(
        args.model_writer,
        args.r12_kit,
    )

    print(
        "ISSUE9_RESEARCH_EVIDENCE_PASS "
        f"r10={EXPECTED_ZIPS['r10']} "
        f"r11={EXPECTED_ZIPS['r11']} "
        f"r12={EXPECTED_ZIPS['r12']} "
        "r11SourceMatchesR12=true "
        "modelGeometryEncoderSourcePreserved=true "
        "rigCompilerSourcePreserved=true "
        "textureBuilderSourcePreserved=true "
        "modelWriterSourcePreserved=true "
        "modelWriterByteIdentity=true "
        "legacyModelSha256="
        + legacy_model_sha
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"ISSUE9_RESEARCH_EVIDENCE_FAIL {exc}", file=sys.stderr)
        raise SystemExit(1)
