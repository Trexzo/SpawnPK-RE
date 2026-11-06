# R12 — isolated v308 client profile and final GUI admission evidence

**Verified on authorized Windows desktop on November 6, 2026:** isolated cache source-to-copy verification PASS. **Original graphical client was not launched.**

## Source and destination

R12 adds `tools/verification/r12_seed_isolated_v308_cache.py`: a fail-closed helper that takes the authorized existing `.spawnpk` cache source and an absent new private `user.home`. It creates only the new profile and copies a strict allowlist of game cache `.dat`/`.idx` data, binary game config files (`configs/*.bin`), numeric model data (`raw`), sprite assets and typed static animations/config cache. It intentionally excludes user identity `uid.dat`, market price YAML and unknown/possible private account/session settings. A new `.spawnpk-data` starts empty.

Every selected file is read-only SHA-256 checked before and after copying, with an additional full source read/hash verification before acceptance. The output directory must not preexist; invalid source or output paths, missing required assets, unexpected source symlinks/reparse points and mismatching source/destination digests fail closed. On failure only the newly created private output may be removed. It has no Java game launcher, Stop-Process, client injector or user-account mutation.

## Exact Windows receipt

| Field | Verified value |
|---|---|
| Result | `R12_ISOLATED_V308_CACHE_SEED_PASS` |
| Static asset files copied | 10,764 |
| Asset bytes copied | 209,188,225 |
| Cache inventory SHA-256 | `37cf52440fb46c44c8671470776e5950b5bdc6a8ecf470bd52bf71c87d5208f3` |
| Original source preserved | true |
| Personal identifiers copied | false (explicit exclusion) |
| Live user profiles modified | false |
| Existing Java clients stopped | 0 |
| Java game clients started | 0 |
| Real graphical acceptance | false |

Profile is in a new isolated `%LOCALAPPDATA%/Temp/SpawnPK-R12-IsolatedV308-20261106` folder, with a private `r12-cache-seed-receipt.json`. Its inventory SHA covers the sorted relative path, size and SHA of every admitted file; it does not contain the original per-user ID value or account state. The source live `.spawnpk` and already-running game sessions remain unchanged.

## Exact source/client/server identities

An additional **clean private R12 checkout** was cloned from the GitHub branch at commit `cfe0213129d8096958c2ebf8c45aa0f93eee63b3`. Both R11 and R12 checkouts had the exact identical tracked `server` source tree Git object `cd4668e08d7202b0a66f72bde95906c211ea7d9d`. Therefore the existing independently built JDK21/Java11-bytecode R11 server JAR was safely copied into the private R12 checkout after pinning and rechecking its SHA256 `88ff92b4866c213c7c82411d97b0b22083dec38de71b30da73cf23b8c6a63ec4`. No source changes or server recompilation occurred in the R12 copy.

The exact R10 read-only acceptance checker was rerun against:

- clean R12 commit `cfe0213129d8096958c2ebf8c45aa0f93eee63b3` at `Trexzo/SpawnPK-RE` origin;
- built server JAR in that canonical private checkout, correct full SHA256, metadata and Java11 bytecode major55;
- original v308 input SHA256 `854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6`;
- separately rebuilt certified **patched** airgap v308 SHA256 `024fad774453430bb964076b98d460a6dae821ee31100322d104e30d9a9c97a7` (not identical to original);
- installed Java 11 and successfully seeded isolated v308 user.home;
- unoccupied 127.0.0.1:43594/43595 ports at check time.

All of those checks **PASS**. However, the R10 check still deliberately returns `R10_ORIGINAL_GUI_PREFLIGHT_BLOCKED` because it detected **two running user SpawnPK v312 Java clients**. The R12 task does not weaken the existing live-process fence, stop a running client, or launch original GUI. Static endpoint rewriting of the v308 derivative is not an OS firewall or a runtime egress capture. No GUI login/movement/bank/NPC HP/ground-loot/skill XP/logout-reconnect acceptance has occurred.

## Next test and unresolved scope

The next step requires a deliberate, isolated graphical-client acceptance window once the two live v312 sessions no longer trigger the process fence (without ChatGPT stopping them). At that point rerun the full R10 preflight to recheck artifact bytes, source status, ports and runtime state, and only then use a separately reviewed non-destructive GUI harness with synthetic LocalLab credentials and network egress evidence.

Even if GUI accepts, authentic SpawnPK server NPC HP/AI/drop/XP formula, public accounts, Windows positive save/crash fsync durability, concurrent multi-JVM ownership and G21.171 remain separate release blockers. R12 is a draft stacked source-only milestone; no full RSPS release certification.
