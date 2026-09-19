#!/usr/bin/env python3
"""Create a localhost-only copy of the exact SpawnPK client.

This does NOT touch the original. It replaces only the rs/f/a.class constant-pool UTF8
literal used by the default game/cache hostname: www.spawnpk.org -> 127.0.0.1.
No dev/staff flags are changed.
"""
from __future__ import annotations
import hashlib, io, struct, sys, zipfile
from pathlib import Path

EXPECTED_SHA256 = "6232bae206846a4ba8d09766a2dee886b69016066a3f50f83b201bf705f93662"
TARGET_ENTRY = "rs/f/a.class"
OLD = b"www.spawnpk.org"
NEW = b"127.0.0.1"

def sha256(p: Path) -> str:
    h=hashlib.sha256()
    with p.open('rb') as f:
        for chunk in iter(lambda:f.read(1024*1024), b''): h.update(chunk)
    return h.hexdigest()

def patch_utf8_constant(class_bytes: bytes, old: bytes, new: bytes) -> bytes:
    if class_bytes[:4] != b"\xca\xfe\xba\xbe": raise ValueError("not a class file")
    cp_count = struct.unpack_from('>H', class_bytes, 8)[0]
    pos=10
    out=bytearray(class_bytes[:10])
    replacements=0
    i=1
    while i < cp_count:
        tag=class_bytes[pos]; out.append(tag); pos+=1
        if tag==1:  # Utf8
            n=struct.unpack_from('>H', class_bytes, pos)[0]; pos+=2
            raw=class_bytes[pos:pos+n]; pos+=n
            if raw==old:
                raw=new; replacements+=1
            out += struct.pack('>H', len(raw)) + raw
        elif tag in (3,4): out += class_bytes[pos:pos+4]; pos+=4
        elif tag in (5,6): out += class_bytes[pos:pos+8]; pos+=8; i+=1
        elif tag in (7,8,16,19,20): out += class_bytes[pos:pos+2]; pos+=2
        elif tag in (9,10,11,12,17,18): out += class_bytes[pos:pos+4]; pos+=4
        elif tag==15: out += class_bytes[pos:pos+3]; pos+=3
        else: raise ValueError(f"unknown constant-pool tag {tag} at cp index {i}")
        i+=1
    out += class_bytes[pos:]
    if replacements != 1:
        raise ValueError(f"expected exactly 1 endpoint replacement in {TARGET_ENTRY}, got {replacements}")
    return bytes(out)

def main() -> int:
    if len(sys.argv) not in (2,3):
        print("usage: make_local_client.py <client(6).jar> [output.jar]", file=sys.stderr); return 2
    src=Path(sys.argv[1]).resolve()
    dst=Path(sys.argv[2]).resolve() if len(sys.argv)==3 else src.with_name('client-localhost.jar')
    actual=sha256(src)
    if actual.lower()!=EXPECTED_SHA256:
        raise SystemExit(f"REFUSED: source SHA-256 {actual} != pinned {EXPECTED_SHA256}")
    with zipfile.ZipFile(src,'r') as zin, zipfile.ZipFile(dst,'w') as zout:
        seen=False
        for info in zin.infolist():
            data=zin.read(info.filename)
            if info.filename==TARGET_ENTRY:
                data=patch_utf8_constant(data, OLD, NEW); seen=True
            zout.writestr(info, data)
        if not seen: raise SystemExit(f"missing {TARGET_ENTRY}")
    with zipfile.ZipFile(dst,'r') as z:
        c=z.read(TARGET_ENTRY)
        if OLD in c: raise SystemExit("verification failed: production hostname remains in endpoint class")
        if NEW not in c: raise SystemExit("verification failed: localhost hostname missing from endpoint class")
    print("LOCAL_CLIENT_PATCH_OK")
    print(f"source_sha256={actual}")
    print(f"output={dst}")
    print(f"output_sha256={sha256(dst)}")
    print("change=rs/f/a.class constant-pool endpoint only: www.spawnpk.org -> 127.0.0.1")
    return 0

if __name__=='__main__': raise SystemExit(main())
