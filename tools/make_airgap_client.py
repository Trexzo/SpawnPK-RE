#!/usr/bin/env python3
"""Create a stricter localhost-only SpawnPK client for protocol-lab runs.

Input should be v0.3's client-localhost.jar.  This second-stage patch keeps the
already-local game/cache host and rewrites application-level rs/* URL constants
that could contact SpawnPK/CDN/API (and bare http(s) URL builders) to the local
cache sink at 127.0.0.1:43595.

It is a static application-endpoint guard, not an OS firewall.  The original
client is never modified.
"""
from __future__ import annotations
import hashlib,re,struct,sys,zipfile
from pathlib import Path

LOCAL_ORIGIN = "http://127.0.0.1:43595"
KNOWN_HOSTS = (
    "d2n1q79xme98rn.cloudfront.net",
    "hqxg0u8s64.execute-api.ca-central-1.amazonaws.com",
    "spawnpk.net",
    "www.spawnpk.net",
    "spawnpk.org",
    "www.spawnpk.org",
)


def sha256(p: Path) -> str:
    h=hashlib.sha256()
    with p.open('rb') as f:
        for ch in iter(lambda:f.read(1<<20),b''): h.update(ch)
    return h.hexdigest()


def rewrite_utf8(s: str) -> tuple[str,bool]:
    old=s
    # Full known SpawnPK/CDN/API URL origins.
    s=re.sub(r'https?://d2n1q79xme98rn\.cloudfront\.net', LOCAL_ORIGIN, s, flags=re.I)
    s=re.sub(r'https?://hqxg0u8s64\.execute-api\.ca-central-1\.amazonaws\.com', LOCAL_ORIGIN, s, flags=re.I)
    s=re.sub(r'https?://(?:www\.)?spawnpk\.net', LOCAL_ORIGIN, s, flags=re.I)
    s=re.sub(r'https?://(?:www\.)?spawnpk\.org', LOCAL_ORIGIN, s, flags=re.I)
    # Raw production game fallback.
    s=s.replace('149.56.28.70','127.0.0.1')
    s=s.replace('www.spawnpk.org','127.0.0.1')
    # Client.class contains generic scheme fragments used to assemble URLs.
    # Make those builders loopback too so a runtime-created host cannot escape.
    if s == 'http://' or s == 'https://':
        s = LOCAL_ORIGIN + '/blocked/'
    # Any remaining full URL in application code is redirected to loopback while
    # preserving a descriptive path suffix only where practical.  This catches
    # e.g. runescape links and makes the lab stricter than SpawnPK-only blocking.
    if s.startswith('http://') or s.startswith('https://'):
        try:
            rest=s.split('://',1)[1]
            slash=rest.find('/')
            suffix=rest[slash:] if slash >= 0 else '/'
            # Don't re-rewrite our local origin.
            if not s.startswith(LOCAL_ORIGIN):
                s=LOCAL_ORIGIN + '/blocked' + suffix
        except Exception:
            s=LOCAL_ORIGIN + '/blocked/'
    return s, s!=old


def patch_class(data: bytes) -> tuple[bytes,list[tuple[str,str]]]:
    if data[:4] != b'\xca\xfe\xba\xbe': return data,[]
    cp_count=struct.unpack_from('>H',data,8)[0]
    pos=10; out=bytearray(data[:10]); changes=[]; i=1
    while i<cp_count:
        tag=data[pos]; out.append(tag); pos+=1
        if tag==1:
            n=struct.unpack_from('>H',data,pos)[0]; pos+=2
            raw=data[pos:pos+n]; pos+=n
            try: txt=raw.decode('utf-8')
            except UnicodeDecodeError: txt=None
            if txt is not None:
                new,changed=rewrite_utf8(txt)
                if changed:
                    nr=new.encode('utf-8'); changes.append((txt,new)); raw=nr
            out += struct.pack('>H',len(raw))+raw
        elif tag in (3,4): out+=data[pos:pos+4]; pos+=4
        elif tag in (5,6): out+=data[pos:pos+8]; pos+=8; i+=1
        elif tag in (7,8,16,19,20): out+=data[pos:pos+2]; pos+=2
        elif tag in (9,10,11,12,17,18): out+=data[pos:pos+4]; pos+=4
        elif tag==15: out+=data[pos:pos+3]; pos+=3
        else: raise ValueError(f'unknown cp tag {tag} at {i}')
        i+=1
    out += data[pos:]
    return bytes(out),changes


def external_rs_strings(jar: Path):
    bad=[]
    with zipfile.ZipFile(jar) as z:
        for n in z.namelist():
            if not (n.startswith('rs/') and n.endswith('.class')): continue
            data=z.read(n)
            # printable strings sufficient as an independent static guard here
            for raw in re.findall(rb'[\x20-\x7e]{5,}',data):
                s=raw.decode('latin1','ignore')
                low=s.lower()
                if ('http://' in low or 'https://' in low or 'cloudfront.net' in low or
                    'execute-api' in low or 'www.spawnpk.org' in low or '149.56.28.70' in low):
                    if '127.0.0.1' not in low:
                        bad.append((n,s))
    return bad


def main():
    if len(sys.argv)!=3:
        print('usage: make_airgap_client.py <client-localhost.jar> <client-airgap.jar>',file=sys.stderr); return 2
    src=Path(sys.argv[1]); dst=Path(sys.argv[2])
    changes=[]
    with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst,'w') as zout:
        for info in zin.infolist():
            data=zin.read(info.filename)
            if info.filename.startswith('rs/') and info.filename.endswith('.class'):
                data,ch=patch_class(data)
                for old,new in ch: changes.append((info.filename,old,new))
            zout.writestr(info,data)
    bad=external_rs_strings(dst)
    if bad:
        for x in bad[:20]: print('UNPATCHED',x,file=sys.stderr)
        raise SystemExit(f'airgap static verification failed: {len(bad)} external application strings remain')
    print('AIRGAP_CLIENT_PATCH_OK')
    print('source_sha256='+sha256(src))
    print('output_sha256='+sha256(dst))
    print('changed_utf8_constants='+str(len(changes)))
    for n,old,new in changes:
        print(f'{n}: {old!r} -> {new!r}')
    return 0

if __name__=='__main__': raise SystemExit(main())
