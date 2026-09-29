#!/usr/bin/env python3
"""Source-only regression for the Issue #913 legacy textured+skinned writer."""
from __future__ import annotations

import hashlib
import importlib.util
import json
import sys
from pathlib import Path

EXPECTED_SHA = "6cf617b5e14e60b5bc58d4f1c72e11476f09382d40a72f49be122009157c7fad"

R11_OBJ = """# safe authored R11 cube fixture
v -64 -64 -64
v 64 -64 -64
v 64 64 -64
v -64 64 -64
v -64 -64 64
v 64 -64 64
v 64 64 64
v -64 64 64
vt 0 0
vt 1 0
vt 0 1
vt 0 0
vt 1 0
vt 0 1
vt 0 0
vt 1 0
vt 0 1
vt 0 0
vt 1 0
vt 0 1
vt 0 0
vt 1 0
vt 0 1
vt 0 0
vt 1 0
vt 0 1
vt 0 0
vt 1 0
vt 0 1
vt 0 0
vt 1 0
vt 0 1
vt 0 0
vt 1 0
vt 0 1
vt 0 0
vt 1 0
vt 0 1
vt 0 0
vt 1 0
vt 0 1
vt 0 0
vt 1 0
vt 0 1
f 2/1 4/2 1/3
f 8/4 6/5 5/6
f 5/7 2/8 1/9
f 6/10 3/11 2/12
f 7/13 4/14 3/15
f 1/16 8/17 5/18
f 2/19 3/20 4/21
f 8/22 7/23 6/24
f 5/25 6/26 2/27
f 6/28 7/29 3/30
f 7/31 8/32 4/33
f 1/34 4/35 8/36
"""

R11_SKINS = json.dumps({
    "format": "spawnpk-v308-vertex-skins-v1",
    "vertexCount": 8,
    "groups": [0, 0, 0, 0, 1, 1, 1, 1],
})

R11_MATERIAL = json.dumps({
    "format": "spawnpk-v308-blender-material-v1",
    "materialName": "SPK_Texture_278",
    "textureId": 278,
    "uvLayer": "SPK_UV",
    "image": "r11-texture-278.png",
    "mappingPolicy": "canonical triangle UV: (0,0),(1,0),(0,1)",
})


def load_writer():
    path = Path(__file__).with_name("compile_spawnpk_legacy_textured_skinned.py")
    spec = importlib.util.spec_from_file_location("legacy_writer_under_test", path)
    if spec is None or spec.loader is None:
        raise AssertionError("cannot import writer")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def require_rejected(action, text: str) -> None:
    try:
        action()
    except ValueError as exc:
        if text not in str(exc):
            raise AssertionError(f"wrong rejection: {exc}") from exc
        return
    raise AssertionError(f"expected rejection containing {text!r}")


def main() -> None:
    writer = load_writer()
    blob = writer.compile_legacy_textured_skinned(
        R11_OBJ,
        R11_SKINS,
        R11_MATERIAL,
    )
    digest = hashlib.sha256(blob).hexdigest()

    if len(blob) != 209 or digest != EXPECTED_SHA:
        raise AssertionError(
            f"R11 fixture drift bytes={len(blob)} sha256={digest}"
        )

    bad_uv = R11_OBJ.replace("vt 1 0", "vt 0.5 0", 1)
    require_rejected(
        lambda: writer.compile_legacy_textured_skinned(
            bad_uv,
            R11_SKINS,
            R11_MATERIAL,
        ),
        "unsupported UV basis",
    )

    bad_skins = json.dumps({
        "format": "spawnpk-v308-vertex-skins-v1",
        "vertexCount": 8,
        "groups": [0, 0, 0, 0, 1, 1, 1, 256],
    })
    require_rejected(
        lambda: writer.compile_legacy_textured_skinned(
            R11_OBJ,
            bad_skins,
            R11_MATERIAL,
        ),
        "unsigned-byte range",
    )

    many_faces = (
        "v 0 0 0\n"
        "v 1 0 0\n"
        "v 0 1 0\n"
        "vt 0 0\n"
        "vt 1 0\n"
        "vt 0 1\n"
        + ("f 1/1 2/2 3/3\n" * 65)
    )
    many_skins = json.dumps({
        "format": "spawnpk-v308-vertex-skins-v1",
        "vertexCount": 3,
        "groups": [0, 0, 0],
    })
    require_rejected(
        lambda: writer.compile_legacy_textured_skinned(
            many_faces,
            many_skins,
            R11_MATERIAL,
        ),
        "mapping capacity exceeded",
    )

    print(
        "LEGACY_TEXTURED_SKINNED_WRITER_REGRESSION_PASS "
        "r11ByteIdentity=true "
        "canonicalUvFailClosed=true "
        "skinRangeFailClosed=true "
        "mappingCapacity64=true "
        f"sha256={digest}"
    )


if __name__ == "__main__":
    main()
