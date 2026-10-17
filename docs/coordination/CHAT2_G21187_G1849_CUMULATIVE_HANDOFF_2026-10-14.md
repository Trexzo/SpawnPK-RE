# Chat 2 G21.187 cumulative G18.4.9 — exact gameplay-only overlay

Date: 2026-10-14.
Core parent: Chat 1 combined [PR #2580](https://github.com/Trexzo/SpawnPK-RE/pull/2580) exact `6e31c8b4aab4b9bf67e6a0b80756836539e0296f`, including Chat 1 G21.187 process-killed-JVM stale BOOTSTRAP negative, G21.185 cooperative OS lock, G21.186 real cross-process TCP tests and Chat 2 G18.4.7/G18.4.8. This is test-only cumulative ownership by Chat 2, not a substitute for Chat 1's core authority or a main merge.

## Exact source origin

- Parent G18.4.9 [PR #2577](https://github.com/Trexzo/SpawnPK-RE/pull/2577), head `031bd37f6ad3e1166d3e0f66db4bf0ead55748e9`, hosted [Actions #38556879916](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38556879916) SUCCESS Java11 major55, G1847/G1848/G1849 markers, 492 focused, complete Gradle.
- Import exact Git blob `1e294d16c80fe7824a087614acf4b36e71cf3098` for `server/src/spk/local/G1847MonsterSpawnerNameReuseTerminalAttributionTest.java`, and `b9a635a78f0bbaa591891ef5f5be7908b75b6019` for `docs/coordination/CHAT2_G1849_RESPAWN_NAME_REUSE_2026-10-14.md`.
- All other files, including core `LocalJvmAccountSessionLease.java`, `LocalCrossJvmAccountSessionLock.java`, `LocalSession.java`, persistence, dedicated G21187 and G21186 tests, G1 service/provisioning runtime, and `server/build.gradle`, remain **byte-for-byte identical** to Chat 1 exact #2580 head.
- Existing G1847 focused class is extended; **494 unique tests remain 494**. No new build tasks or runtime behavior.

## Exact regression scope and limits

The **actual installed CUSTOM_LOCALLAB G1 terminal observer** first proves old NPC recipient A / current same-username generation B DMM and G1 PvM record issues (G18.4.7/G18.4.8). G18.4.9 adds two G1 delayed-respawn checks: first, B already current at terminal; second, A **still current when terminal schedules** a G1 ticket, then A disconnects/B joins before `WorldEventQueue.runDue`, causing a newly canonical spawned NPC to carry B's generation witness. An absent-at-terminal control neither credits objective/record nor creates tracked NPC respawn. This is a deterministic source-observed cross-generation lifetime issue, not original SpawnPK reward/respawn semantics or a server-side production exploit.

Chat 1 G21.187 external-process death test demonstrates default-OFF experimental OS session lock releases when holder JVM is killed, permitting stale BOOTSTRAP reentry; still no durable failed-save negative/epoch/quarantine. It is a **NEGATIVE** characterization, not an automatic recovery fix. The cooperative lock does not exclude RAW/uncooperative writes; no original graphical client protocol/gameplay or server-policy authority is inferred.

## Validation / handoff

Fresh **exact-head** GitHub hosted Java11 bytecode major55, G1847/G1848/G1849, G21185/G21186/G21187, **494/494 focused** and full Gradle must pass to certify combined behavior. Do not infer this from two independently green PRs. Chat 1 retains promotion and persistence authority; do not separately import both #2577 and this cumulative branch. Open [#2547](https://github.com/Trexzo/SpawnPK-RE/issues/2547), [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558), [#2540](https://github.com/Trexzo/SpawnPK-RE/issues/2540), [#2559](https://github.com/Trexzo/SpawnPK-RE/issues/2559) remain unresolved. R25 release pointer frozen; Chat 3 client deobfuscation separate.
