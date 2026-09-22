
<img width="240" height="438" alt="image" src="https://github.com/user-attachments/assets/543ea4c9-38ac-44f1-8af8-98045a8d315d" />

<img width="700" height="629" alt="image" src="https://github.com/user-attachments/assets/835f4622-855b-4ff5-a9ad-64c3ea46814f" />

# SpawnPK LocalLab

Local/offline reconstruction and research environment for the current SpawnPK client.

## Baseline

- v5.18.5 / Engine R8.5
- certified server SHA-256: `589635cef6244f1282aee487fdb0649150ded5b60bdc7e3fefd28bba8a86372c`
- pinned client SHA-256: `6232bae206846a4ba8d09766a2dee886b69016066a3f50f83b201bf705f93662`
- R8.5 regression contract: `179/179`

## Existing developer quick start

```powershell
Set-ExecutionPolicy -Scope Process Bypass -Force
.\IMPORT_EXISTING_RUNTIME.ps1 -From "$env:USERPROFILE\Desktop\SpawnPK-LocalLab-v0.1"
.\BOOTSTRAP.ps1
.\RUN_LOCAL_LAB.ps1
```

## New collaborator

Read `docs/SETUP_WINDOWS.md` and `docs/EXTERNAL_RUNTIME.md`.

Client binaries, account state and local cache/config files are intentionally not committed.

## Authority order

1. EXACT_CURRENT_CLIENT
2. EXACT_CURRENT_CACHE
3. LOCAL_RUNTIME_PROVEN
4. HISTORICAL_CORROBORATION
5. INFERENCE
6. UNKNOWN_SERVER_AUTHORITY
7. CUSTOM_LOCALLAB

See `docs/AUTHORITY_MODEL.md`.

## Known R8.5 follow-ups

- `::appfixture makex` can disconnect because the Make-X fixture/root is wrong.
- Voidglass R3 native-compositor visuals are rejected as final design.
- Keep Voidglass item/lifecycle plumbing; move future visuals to a real custom model/cache pipeline.
- Prefer the portable `RUN_LOCAL_LAB.ps1` wrapper over the historical launchers.

Keep the repo private until licensing/provenance review is complete.
## Build toolchain

The certified R8.5 server artifact is preserved under:

`server/certified/SpawnPKLocalServer-R8.5-certified.jar`

Development source builds use **JDK 21** (`javac --release 11`).
The reconstruction matrix found Temurin `javac 21.0.12.1` to match
494/499 certified class entries exactly; Java 17 remains the preferred
runtime/client Java.

The four certified-only historical class entries and one differing
`ClientPacketProbe.class` are preserved as sealed-artifact provenance instead
of being silently injected into current source builds.

Use the repository wrappers:

```powershell
.\scripts\Build-Server.ps1
.\RUN_REPO_SELFTEST.ps1
```

The server build is now Gradle-backed. Direct Gradle entry points live under
`server/`:

```powershell
cd server
.\gradlew.bat build
```

`build` compiles with JDK 21 and `--release 11`, verifies class major version
55, creates `server/build/SpawnPKLocalServer.jar`, and runs the inherited R8.5
regression task when the externally supplied pinned client JAR is available.
Use `-PrequireExactClient=true` to make a missing client fixture fatal.

The original `RUN_V5185_FULL_SELFTEST.ps1` remains strict by default and still
requires the sealed R8.5 outer SHA unless development mode is explicitly
enabled by the repository wrapper.
