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
backs up and patches local config metadata, verifies the patch, and runs the
current regression harness.

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