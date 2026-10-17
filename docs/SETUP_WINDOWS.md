# Windows setup

## Requirements

- Windows 10/11
- Windows PowerShell 5.1+
- Git
- JDK 17 recommended
- an authorized/current SpawnPK client/cache installation

## Clone

```powershell
git clone <PRIVATE-REPO-URL> SpawnPK-LocalLab
cd SpawnPK-LocalLab
```

## Supply external runtime

Required local files:

```text
evidence\client(6).jar
local-client\client-airgap.jar
local-client\client-localhost.jar
```

If you already have a certified LocalLab checkout:

```powershell
.\IMPORT_EXISTING_RUNTIME.ps1 -From "C:\path\to\SpawnPK-LocalLab-v0.1"
```

Otherwise read `EXTERNAL_RUNTIME.md`.

## Cache/config

The current client expects its normal authorized SpawnPK local cache/config tree.
R8.5 config metadata needs at least:

```text
%USERPROFILE%\.spawnpk\configs\i.bin
%USERPROFILE%\.spawnpk\configs\e.bin
```

## Build + certify

```powershell
Set-ExecutionPolicy -Scope Process Bypass -Force
.\BOOTSTRAP.ps1
```

Bootstrap selects Java, verifies external runtime hashes, builds the server,
leaves the authorized live `.spawnpk\configs` tree untouched, and runs the
inherited exact-v308 179/179 R8.5 development compatibility selftest when the
exact client fixture is available.

Direct live `i.bin` / `e.bin` mutation is retired. Custom-asset work uses an
isolated cache/profile copy through `scripts\Run-R13AssetAcceptance.ps1` or
`tools\custom-assets\build_r13_isolated_profile.py`; normal bootstrap does not
rewrite the user's live client configs. The legacy `-SkipConfigPatch` bootstrap
switch remains accepted only as a compatibility no-op.

That bootstrap selftest is **not** the complete current cumulative release
certificate. For full release acceptance, run:

```powershell
.\RUN_CURRENT_RELEASE_ACCEPTANCE.ps1 -V308ClientPath .\evidence\client(6).jar
```

The full release path delegates to the canonical exact-current cumulative
certification/evidence wrapper and then loopback-smokes the server output left
by that certification.

## Launch

```powershell
.\RUN_LOCAL_LAB.ps1
```

Expected endpoints:

```text
GAME 127.0.0.1:43594
AUX  127.0.0.1:43595
```

## Server-only work

```powershell
.\BOOTSTRAP.ps1 -ServerOnly -SkipConfigPatch
```

Never commit client JARs, account profiles, `.spawnpk` files, build output,
runtime backups, logs, release ZIPs or old `.v*-backup` trees.
## Build Java vs runtime Java

LocalLab uses two deliberately separate Java roles:

- **JDK 21** for compiling the server source.
- **Java/JDK 17** as the preferred proven runtime for server/client launch.

For closest R8.5 source-bytecode parity, Temurin `javac 21.0.12.1` is the
recorded compiler baseline.

`.\scripts\Build-Server.ps1` automatically selects JDK 21.
Runtime launchers independently prefer Java 17.