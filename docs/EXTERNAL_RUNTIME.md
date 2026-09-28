# External runtime

The repo intentionally does not contain original or patched SpawnPK client binaries.

The current external-runtime authority is one coherent exact-v308 lineage.

Required local paths and certified SHA-256 values:

```text
evidence\client(6).jar
854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6

local-client\client-airgap.jar
83b3e27e2aae50512d044ae4c74d84afb36df8b8a8051b5eb0c9275427363c33

local-client\client-localhost.jar
01c878a56ee25fb112dfe8b459dbd11ea26cfa8a92a7f287a4e5ee53f673cdbd
```

## Rebuild the local variants

The localhost and airgap JARs are deterministic derivatives of the exact v308 input.
No patched proprietary JAR is committed. Generated JAR entries use deterministic STORED
ZIP records so whole-JAR hashes do not depend on the caller's Python/zlib version.

Run:

```powershell
python .\tools\runtime\build_v308_local_clients.py .\evidence\client(6).jar .\local-client
```

Expected marker:

`V308_LOCAL_CLIENT_PATCH_PASS`

The patcher rejects any input JAR whose SHA-256 is not the exact v308 authority above.

### localhost variant

The exact client already contains a dormant local socket mode. The localhost variant
changes only `rs/f/a.class` to enable that existing mode.

Result:
- game socket -> `127.0.0.1:43594`;
- AUX socket -> `127.0.0.1:43595`;
- updater/web/API URLs otherwise remain stock.

### airgap variant

The airgap variant includes localhost mode and redirects every known first-party
SpawnPK updater/web/API constant to LocalLab loopback.

Updater base:

`http://127.0.0.1:43595/spk_live/`

The deterministic audit requires no remaining `spawnpk.net`, `spawnpk.org`,
`cloudfront.net`, `execute-api.*`, or `amazonaws.com` production endpoint authority
in class-file constants.

Use `IMPORT_EXISTING_RUNTIME.ps1` when importing a separately prepared exact-v308
triplet. `scripts\Check-ExternalRuntime.ps1` rejects the older v307-era runtime.

## Asset isolation

For LocalLab custom-asset work, use the isolated profile root:

```text
<LocalLab user.home>\.spawnpk
<LocalLab user.home>\.spawnpk-data
```

Do not write generated LocalLab assets into the real OS user profile.

## Existing R8.5 definition patching

R8.5 modifies only local definition metadata in:
- `.spawnpk\configs\i.bin`;
- `.spawnpk\configs\e.bin`.

The existing bootstrap path backs those files up before patching. The v308 external-runtime rebuild in this issue does not broaden that cache-mutation authority.
