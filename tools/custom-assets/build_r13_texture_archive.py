#!/usr/bin/env python3
"""Build the exact R13 texture-278 archive augmentation from source only."""
from __future__ import annotations

import argparse
import bz2
import gzip
import hashlib
from pathlib import Path


def u3(value: int) -> bytes:
    if not 0 <= value <= 0xFFFFFF:
        raise ValueError(value)
    return value.to_bytes(3, "big")


def jag_hash(name: str) -> int:
    value = 0
    for char in name.upper():
        value = (value * 61 + ord(char) - 32) & 0xFFFFFFFF
    return value


def archive_body(blob: bytes) -> bytes:
    if len(blob) < 6:
        raise ValueError("short archive")

    raw_len = int.from_bytes(blob[:3], "big")
    packed_len = int.from_bytes(blob[3:6], "big")
    payload = blob[6:]

    if packed_len == 0:
        body = gzip.decompress(payload)
    elif packed_len == raw_len:
        body = payload
    else:
        body = bz2.decompress(b"BZh1" + payload)

    if len(body) != raw_len:
        raise ValueError(
            f"archive length mismatch expected={raw_len} actual={len(body)}"
        )

    return body


def semantic_sha256(blob: bytes) -> str:
    return hashlib.sha256(archive_body(blob)).hexdigest()


def parse_archive(blob: bytes):
    body = archive_body(blob)

    count = int.from_bytes(body[:2], "big")
    pos = 2
    meta = []

    for _ in range(count):
        name_hash = int.from_bytes(body[pos : pos + 4], "big")
        decoded = int.from_bytes(body[pos + 4 : pos + 7], "big")
        encoded = int.from_bytes(body[pos + 7 : pos + 10], "big")
        pos += 10
        meta.append((name_hash, decoded, encoded))

    data_pos = pos
    entries = []

    for name_hash, decoded, encoded in meta:
        if decoded != encoded:
            raise ValueError(
                "R13 requires the exact whole-archive-compressed texture form"
            )
        data = body[data_pos : data_pos + decoded]
        if len(data) != decoded:
            raise ValueError("truncated archive entry")
        entries.append([name_hash, data])
        data_pos += encoded

    if data_pos != len(body):
        raise ValueError(f"trailing archive bytes: {len(body) - data_pos}")

    return entries


def emit_archive(entries) -> bytes:
    table = bytearray()
    payload = bytearray()
    table += len(entries).to_bytes(2, "big")

    for name_hash, data in entries:
        table += (name_hash & 0xFFFFFFFF).to_bytes(4, "big")
        table += u3(len(data))
        table += u3(len(data))
        payload += data

    body = bytes(table + payload)
    compressed = gzip.compress(body, compresslevel=9, mtime=0)
    return u3(len(body)) + b"\x00\x00\x00" + compressed


def checkerboard_texture(index_offset: int):
    # Exact source form of the safe R12 PNG:
    # 128x128, opaque FF00FF / 00FFFF, alternating 32x32 tiles.
    palette = [(0, 0, 0), (255, 0, 255), (0, 255, 255)]

    index = bytearray()
    index += (128).to_bytes(2, "big")
    index += (128).to_bytes(2, "big")
    index += bytes([len(palette)])

    for red, green, blue in palette[1:]:
        index += bytes([red, green, blue])

    # x/y offsets, sub-width, sub-height, row-major packing mode.
    index += b"\x00\x00"
    index += (128).to_bytes(2, "big")
    index += (128).to_bytes(2, "big")
    index += b"\x00"

    dat = bytearray(index_offset.to_bytes(2, "big"))

    for y in range(128):
        for x in range(128):
            dat.append(
                1
                if ((x // 32) + (y // 32)) % 2 == 0
                else 2
            )

    return bytes(index), bytes(dat)


def build(base: bytes, texture_id: int = 278) -> bytes:
    entries = parse_archive(base)
    by_hash = {
        name_hash: index
        for index, (name_hash, _data) in enumerate(entries)
    }

    index_hash = jag_hash("index.dat")
    texture_hash = jag_hash(f"{texture_id}.dat")

    if index_hash not in by_hash:
        raise ValueError("index.dat hash missing")

    if texture_hash in by_hash:
        raise ValueError(
            f"{texture_id}.dat already exists; refusing collision/overwrite"
        )

    index_entry = by_hash[index_hash]
    old_index = entries[index_entry][1]

    if len(old_index) > 0xFFFF:
        raise ValueError("texture index offset exceeds u16")

    index_append, texture_dat = checkerboard_texture(len(old_index))
    entries[index_entry][1] = old_index + index_append
    entries.append([texture_hash, texture_dat])

    return emit_archive(entries)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("base_archive", type=Path)
    parser.add_argument("output_archive", type=Path)
    parser.add_argument("--texture-id", type=int, default=278)
    args = parser.parse_args()

    blob = build(
        args.base_archive.read_bytes(),
        texture_id=args.texture_id,
    )
    args.output_archive.write_bytes(blob)

    print(
        "R13_TEXTURE_ARCHIVE_BUILD_PASS "
        f"bytes={len(blob)} "
        f"transportSha256={hashlib.sha256(blob).hexdigest()} "
        f"semanticBodySha256={semantic_sha256(blob)}"
    )


if __name__ == "__main__":
    main()
