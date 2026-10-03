# External runtime

The repo intentionally does not contain original or patched SpawnPK client binaries.

The current external-runtime authority is one coherent exact-v308 lineage.

Required local paths and certified SHA-256 values:

```text
evidence\client(6).jar
854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6

local-client\client-airgap.jar
024fad774453430bb964076b98d460a6dae821ee31100322d104e30d9a9c97a7

local-client\client-localhost.jar
15ceb89669ddfe0a666e65b4a5291705af692a50e479bbde17e751eceb7fd23e
```

## Rebuild the local variants

The localhost and airgap JARs are deterministic derivatives of the exact v308 input.
No patched proprietary JAR is committed. Generated JAR entries use deterministic STORED
ZIP records so whole-JAR hashes do not depend on the caller's Python/zlib version.

Use the canonical operator wrapper from the repository root:

```powershell
.\scripts\Build-V308LocalClients.ps1
```

The wrapper:
- requires the canonical `evidence\client(6).jar` input path;
- requires the canonical `local-client\` output directory;
- independently checks the exact v308 input SHA-256;
- invokes `tools\runtime\build_v308_local_clients.py`;
- runs `scripts\Check-ExternalRuntime.ps1` afterward so the complete
  evidence/airgap/localhost triplet is verified before success.

Expected final wrapper marker:

`V308_LOCAL_CLIENT_BUILD_AND_VERIFY_PASS`

The Python patcher is the deterministic implementation layer used by that wrapper.
For research/tool development it may be invoked directly, but the canonical LocalLab
runtime should be rebuilt through the PowerShell wrapper above.

### localhost variant

The exact client already contains a dormant local socket mode. The localhost variant:
- changes `rs/f/a.class` to enable that existing mode;
- applies the exact-v308, byte-length-neutral `rs/Client.class` Walk-here patch that
  expands the already-present unreachable-target fallback from radius 1 to radius 2.

Result:
- game socket -> `127.0.0.1:43594`;
- AUX socket -> `127.0.0.1:43595`;
- updater/web/API URLs otherwise remain stock;
- Walk-here uses the certified radius-2 fallback derivative.

Hosted source regression proves the deterministic patch contract and generated hashes.
The human HOME blocked-scenery Walk-here acceptance remains external and must not be
inferred from source CI alone.

### airgap variant

The airgap variant includes the same localhost mode and exact-v308
`rs/Client.class` Walk-here radius-2 patch, then redirects every known first-party
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

## Live configs and custom assets

Normal bootstrap does **not** rewrite the authorized live client config files:

- `%USERPROFILE%\.spawnpk\configs\i.bin`;
- `%USERPROFILE%\.spawnpk\configs\e.bin`.

The former direct live-config patch path is retired. `scripts\Patch-LocalConfigs.ps1`
is retained only as a fail-closed compatibility shim and does not copy or mutate
client config bytes.

Custom LocalLab asset work belongs in an isolated client profile/cache copy. Use:

```powershell
.\scripts\Run-R13AssetAcceptance.ps1 -BaseSpawnpk "<authorized .spawnpk directory>"
```

or the underlying isolated profile tooling in
`tools\custom-assets\build_r13_isolated_profile.py`.

The exact-v308 external-runtime rebuild changes only the generated LocalLab client
variants under `local-client\`; it does not grant authority to mutate the user's
live `.spawnpk\configs` tree.
