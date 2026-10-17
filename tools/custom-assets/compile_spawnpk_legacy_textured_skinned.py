#!/usr/bin/env python3
"""Compile the proven SpawnPK v308 legacy/no-marker textured+skinned model family.

This writer is intentionally narrow. It re-establishes the exact R11 contract from
preserved authored inputs plus the certified model output, reusing the signed-smart
coordinate/index encoding already proven by the preserved R10 compiler.

Supported authoring contract:
- OBJ vertices and triangular faces with explicit vt indices;
- canonical per-face UV basis exactly (0,0), (1,0), (0,1);
- one unsigned-byte skin/group id per model vertex;
- one texture id shared by all faces;
- one texture mapping triangle per face, using the face's three vertex indices;
- at most 64 mapping triangles (legacy face-render mapping index 0..63).

Output is the 18-byte-footer legacy/no-marker family accepted by exact v308. It does
not write FF FF models, blended weights, arbitrary UV bases, priorities, alpha, or
face-skin streams.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from dataclasses import dataclass
from pathlib import Path

CANONICAL_UV = ((0.0, 0.0), (1.0, 0.0), (0.0, 1.0))
SKIN_FORMAT = "spawnpk-v308-vertex-skins-v1"
MATERIAL_FORMAT = "spawnpk-v308-blender-material-v1"


def be16(v: int) -> bytes:
    if not 0 <= v <= 0xFFFF:
        raise ValueError(f"u16 out of range: {v}")
    return bytes(((v >> 8) & 0xFF, v & 0xFF))


def signed_smart(v: int) -> bytes:
    # Exact helper preserved from the R10 v308 writer.
    if -64 <= v <= 63:
        return bytes((v + 64,))
    if -16384 <= v <= 16383:
        x = v + 49152
        return bytes(((x >> 8) & 0xFF, x & 0xFF))
    raise ValueError(
        f"signed-smart delta {v} is outside exact decoder range "
        "[-16384,16383]; reorder/split geometry or reduce scale"
    )


@dataclass(frozen=True)
class ObjData:
    vertices: tuple[tuple[int, int, int], ...]
    texcoords: tuple[tuple[float, float], ...]
    faces: tuple[tuple[int, int, int], ...]
    face_texcoords: tuple[tuple[int, int, int], ...]


def _obj_index(token: str, count: int, kind: str, line_no: int) -> int:
    if token == "":
        raise ValueError(f"OBJ line {line_no}: missing {kind} index")
    n = int(token)
    if n == 0:
        raise ValueError(f"OBJ line {line_no}: {kind} index must not be zero")
    idx = n - 1 if n > 0 else count + n
    if not 0 <= idx < count:
        raise ValueError(f"OBJ line {line_no}: {kind} index {n} out of range")
    return idx


def parse_obj_text(text: str, scale: float = 1.0) -> ObjData:
    vertices: list[tuple[int, int, int]] = []
    texcoords: list[tuple[float, float]] = []
    faces: list[tuple[int, int, int]] = []
    face_texcoords: list[tuple[int, int, int]] = []

    for line_no, raw in enumerate(text.splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split()
        if parts[0] == "v":
            if len(parts) < 4:
                raise ValueError(f"OBJ line {line_no}: malformed vertex")
            vertices.append(
                tuple(int(round(float(parts[i]) * scale)) for i in (1, 2, 3))
            )
        elif parts[0] == "vt":
            if len(parts) < 3:
                raise ValueError(f"OBJ line {line_no}: malformed texture coordinate")
            texcoords.append((float(parts[1]), float(parts[2])))
        elif parts[0] == "f":
            if len(parts) != 4:
                raise ValueError(
                    f"OBJ line {line_no}: legacy textured writer requires triangular faces"
                )
            vi: list[int] = []
            ti: list[int] = []
            for token in parts[1:]:
                fields = token.split("/")
                vi.append(_obj_index(fields[0], len(vertices), "vertex", line_no))
                if len(fields) < 2 or fields[1] == "":
                    raise ValueError(
                        f"OBJ line {line_no}: every face vertex requires a "
                        "texture-coordinate index"
                    )
                ti.append(_obj_index(fields[1], len(texcoords), "texture", line_no))
            faces.append(tuple(vi))
            face_texcoords.append(tuple(ti))

    if not vertices:
        raise ValueError("OBJ has no vertices")
    if not faces:
        raise ValueError("OBJ has no faces")
    if len(vertices) > 0xFFFF or len(faces) > 0xFFFF:
        raise ValueError("legacy writer uses u16 vertex/face counts")
    if len(faces) > 64:
        raise ValueError(
            f"legacy textured mapping capacity exceeded: faces={len(faces)} max=64"
        )

    for face_no, tex_indices in enumerate(face_texcoords):
        actual = tuple(texcoords[i] for i in tex_indices)
        if actual != CANONICAL_UV:
            raise ValueError(
                f"face {face_no}: unsupported UV basis {actual}; expected {CANONICAL_UV}"
            )

    return ObjData(
        tuple(vertices),
        tuple(texcoords),
        tuple(faces),
        tuple(face_texcoords),
    )


def parse_skin_json(text: str, vertex_count: int) -> tuple[int, ...]:
    data = json.loads(text)
    if data.get("format") != SKIN_FORMAT:
        raise ValueError(f"unsupported skin sidecar format: {data.get('format')!r}")
    if data.get("vertexCount") != vertex_count:
        raise ValueError(
            f"skin vertexCount mismatch expected={vertex_count} "
            f"actual={data.get('vertexCount')}"
        )
    groups = data.get("groups")
    if not isinstance(groups, list) or len(groups) != vertex_count:
        raise ValueError("skin groups must contain exactly one group per vertex")
    out: list[int] = []
    for index, value in enumerate(groups):
        if (
            isinstance(value, bool)
            or not isinstance(value, int)
            or not 0 <= value <= 255
        ):
            raise ValueError(
                f"skin group {index} outside unsigned-byte range: {value!r}"
            )
        out.append(value)
    return tuple(out)


def parse_material_json(text: str) -> int:
    data = json.loads(text)
    if data.get("format") != MATERIAL_FORMAT:
        raise ValueError(f"unsupported material format: {data.get('format')!r}")
    texture_id = data.get("textureId")
    if (
        isinstance(texture_id, bool)
        or not isinstance(texture_id, int)
        or not 0 <= texture_id <= 0xFFFF
    ):
        raise ValueError(f"textureId outside u16 range: {texture_id!r}")
    policy = data.get("mappingPolicy")
    expected = "canonical triangle UV: (0,0),(1,0),(0,1)"
    if policy != expected:
        raise ValueError(f"unsupported mappingPolicy: {policy!r}")
    return texture_id


def encode_vertex_streams(
    vertices: tuple[tuple[int, int, int], ...],
) -> tuple[bytes, bytes, bytes, bytes]:
    flags = bytearray()
    xs = bytearray()
    ys = bytearray()
    zs = bytearray()
    px = py = pz = 0

    for x, y, z in vertices:
        dx, dy, dz = x - px, y - py, z - pz
        flag = 0
        if dx:
            flag |= 1
            xs += signed_smart(dx)
        if dy:
            flag |= 2
            ys += signed_smart(dy)
        if dz:
            flag |= 4
            zs += signed_smart(dz)
        flags.append(flag)
        px, py, pz = x, y, z

    return bytes(flags), bytes(xs), bytes(ys), bytes(zs)


def encode_faces_type1(
    faces: tuple[tuple[int, int, int], ...],
) -> tuple[bytes, bytes]:
    types = bytes([1] * len(faces))
    stream = bytearray()
    last = 0

    for a, b, c in faces:
        stream += signed_smart(a - last)
        last = a
        stream += signed_smart(b - last)
        last = b
        stream += signed_smart(c - last)
        last = c

    return types, bytes(stream)


def compile_legacy_textured_skinned(
    obj_text: str,
    skin_json_text: str,
    material_json_text: str,
    *,
    scale: float = 1.0,
) -> bytes:
    obj = parse_obj_text(obj_text, scale)
    skins = parse_skin_json(skin_json_text, len(obj.vertices))
    texture_id = parse_material_json(material_json_text)

    vflags, xs, ys, zs = encode_vertex_streams(obj.vertices)
    face_types, face_indices = encode_faces_type1(obj.faces)

    body = bytearray()
    body += vflags
    body += face_types

    # Low two bits == 2 means textured face. Upper six bits select the
    # legacy type-0 texture-mapping triangle index.
    for mapping_index in range(len(obj.faces)):
        body.append(2 + (mapping_index << 2))

    body += bytes(skins)
    body += face_indices

    # In this exact legacy family the face-colour slot carries texture id.
    for _ in obj.faces:
        body += be16(texture_id)

    # The R11 canonical UV basis permits each face's own three vertices to
    # be the type-0 mapping triangle; no hidden mapping vertices are needed.
    for a, b, c in obj.faces:
        body += be16(a)
        body += be16(b)
        body += be16(c)

    body += xs
    body += ys
    body += zs

    footer = bytearray()
    footer += be16(len(obj.vertices))
    footer += be16(len(obj.faces))
    footer += bytes((
        len(obj.faces),  # texture mapping triangles
        1,               # face-render-info stream present
        0,               # constant priority / no priority stream
        0,               # alpha stream absent
        0,               # face-skin stream absent
        1,               # vertex-skin stream present
    ))
    footer += be16(len(xs))
    footer += be16(len(ys))
    footer += be16(len(zs))
    footer += be16(len(face_indices))

    return bytes(body + footer)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("input_obj", type=Path)
    ap.add_argument("skin_sidecar", type=Path)
    ap.add_argument("material_json", type=Path)
    ap.add_argument("output_dat", type=Path)
    ap.add_argument("--scale", type=float, default=1.0)
    args = ap.parse_args()

    blob = compile_legacy_textured_skinned(
        args.input_obj.read_text(encoding="utf-8"),
        args.skin_sidecar.read_text(encoding="utf-8"),
        args.material_json.read_text(encoding="utf-8"),
        scale=args.scale,
    )
    args.output_dat.parent.mkdir(parents=True, exist_ok=True)
    args.output_dat.write_bytes(blob)
    digest = hashlib.sha256(blob).hexdigest()
    print(
        "LEGACY_TEXTURED_SKINNED_MODEL_WRITE_PASS "
        f"bytes={len(blob)} sha256={digest} output={args.output_dat.resolve()}"
    )


if __name__ == "__main__":
    main()
