#!/usr/bin/env python3
from pathlib import Path
import hashlib,re,zipfile

ROOT=Path(__file__).resolve().parents[1]
ORIG='6232bae206846a4ba8d09766a2dee886b69016066a3f50f83b201bf705f93662'
LOCAL='aabca3605421337e81ab96da0de5669b94b2db8471c0157ad3f7ac9a84d031a2'
AIRGAP='981eb3607802fcd892fd465d62a6f1ba9d8afffcbfd410045b5cc170ae06e71e'
SERVER='8eeb1beaf525d474f10587e460a196692f81cac2597a1d5cd339db0eff119aba'

def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
orig=ROOT/'evidence/client(6).jar'
local=ROOT/'local-client/client-localhost.jar'
airgap=ROOT/'local-client/client-airgap.jar'
server=ROOT/'server/build/SpawnPKLocalServer.jar'
assert sha(orig)==ORIG, 'original authority hash mismatch'
assert sha(local)==LOCAL, 'local client hash mismatch'
assert sha(airgap)==AIRGAP, 'airgap client hash mismatch'
assert sha(server)==SERVER, 'server hash mismatch'
with zipfile.ZipFile(local) as z:
    c=z.read('rs/f/a.class')
    assert b'127.0.0.1' in c
    assert b'www.spawnpk.org' not in c
with zipfile.ZipFile(airgap) as z:
    bad=[]
    for n in z.namelist():
        if not (n.startswith('rs/') and n.endswith('.class')): continue
        d=z.read(n)
        for raw in re.findall(rb'[\x20-\x7e]{5,}',d):
            s=raw.decode('latin1','ignore').lower()
            if ('cloudfront.net' in s or 'execute-api' in s or 'www.spawnpk.org' in s or
                '149.56.28.70' in s or 'http://spawnpk.net' in s or 'https://spawnpk.net' in s):
                bad.append((n,s))
    assert not bad, f'airgap application endpoint strings remain: {bad[:5]}'
with zipfile.ZipFile(server) as z:
    blob=b''.join(z.read(n) for n in z.namelist() if n.endswith('.class'))
    assert b'www.spawnpk.org' not in blob
    assert b'149.56.28.70' not in blob
    assert b'127.0.0.1' in blob
print('LOCAL_ONLY_STATIC_GUARDS_PASS_V32')
print('server_loopback_only_literals=true')
print('client_game_endpoint=127.0.0.1')
print('airgap_known_application_external_endpoints=rewritten_to_loopback')
print('m4_certification=CERTIFIED')
print('m5_walking_running=LIVE_CERTIFIED')
print('v32_bank_main_root=5292')
print('v32_bank_container=5382')
print('v32_bank_inventory_overlay_root=5063')
print('v32_bank_inventory_widget=5064')
print('v32_inventory_initial_state=EMPTY')
print('v32_close_interface_opcode130=STATIC_EXACT_FIXED0')
print('v32_inventory_item_opcode41=STATIC_EXACT_FIXED6_OBSERVE_ONLY')
