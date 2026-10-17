# Chat 2 G18.6.2 — active G4 task reattributed to new same-name WorldPlayer and persisted history diverges

Date: 2026-10-14.

**Exact certified source parent:** [Chat 1 + Chat 2 G21.200/G18.6.1 #2628](https://github.com/Trexzo/SpawnPK-RE/pull/2628) at `3c17ff39884dee2aac995f68e3edee2afa8d7d89`, [GitHub Actions #38618035483](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38618035483) **SUCCESS**. **507 unique focused** / Java11 bytecode major55 / full Gradle. Parent and this proposal both draft/unmerged; no main/release changes.

## Independent G4 task ownership gap

Earlier G18.6.1 [#2626](https://github.com/Trexzo/SpawnPK-RE/pull/2626) started a *new* G4 Monster Hunter task for replacement B after original A had invalid G4 state and no task. G18.6.2 instead examines an **already ACTIVE legitimate G4 task originally started and persisted by A**. Source authority is all **CUSTOM_LOCALLAB**, not historical SpawnPK server logic:

- `LocalLabSlayerRuntime` delegates active task identity to World-scoped `SlayerTaskService` keyed by normalized username. `World.unregisterPlayer` removes World membership but does not automatically clear this Slayer task. `status(name)` after B's registration can therefore show the still-active task assigned by A, although B's new `WorldPlayer.snapshotExtensions()` has no G4 namespace yet.
- `LocalLabSlayerRuntime.recordMonsterSpawnerFinalization(name,...)` resolves `world.players().byName(name)` at callback time, and `recordOwned` can advance the existing task and write `LocalLabSlayerPersistence.encode(status)` into **B's** snapshot extension. `LocalLabSlayerPersistence` snapshot on detached A is a separate owned value and remains unchanged. G1 record is similarly attributed to B, while immutable Monster Spawner `RecipientOrigin` still captures A and generation.
- This is a **same-World, same-name identity takeover boundary**, not authenticated socket reconnect, actual persisted account relog, cross-JVM behavior or proof of the original game's intended recipient.

## G18.6.2 test acceptance

1. Register A, legitimately `startMonsterHunter` (task ACTIVE, 0/1), verify exact G4 snapshot extension decodes ACTIVE with completion count 0. Activate DMM EASY 0/3. Spawn a genuine canonical NPC through unchanged installed `LocalLabMonsterSpawnerProvisioning.create(world)`; capture immutable `RecipientOrigin` A and its generation.
2. Unregister A **before the NPC dies**, register new same-name B with **empty G4 snapshot extension**. Confirm `slayer.status(name)` on B already exposes A's original active task ID and progress0 without B selecting/starting/accepting anything. DMM is still 0/3.
3. Cause exactly ONE canonical lifecycle NPC death and terminal finalization using the **actual installed combined observer**. Confirm the terminal still references original A but the G4 task completes 1/1 and persists COMPLETED+completions1 on B, while A's original snapshot extension is **byte/value unchanged** and decodes ACTIVE+progress0+completions0. G1 A kills0, B kills1, receipt1, DMM1/3, one settled coin, one ground item, no extra death or replay.

Marker: `G1862_G4_ACTIVE_TASK_NAME_REUSE_PERSISTENCE_SPLIT_PASS`.

### Why this matters

The distinction is stronger than username-only final progress: **one shared World G4 task and two different `WorldPlayer`-owned persistence snapshots describe incompatible lifecycle states**. A generic per-consumer outbox cannot automatically infer recipient/epoch from task name, current username, prior task status, `PvmRecordService.REPLAY`, or the final `FINALIZED` outcome. Ground settlement remains canonical and independent. A future fix must choose an explicit LocalLab ownership-transfer policy, preserve immutable spawn origin and exact owner generation, and design task/persistence atomicity and idempotency before replay. See [outbox #2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587) and [original kill/credit authority #2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558), both OPEN; no original SpawnPK reward rules are assumed.

**Only existing `G1847MonsterSpawnerNameReuseTerminalAttributionTest.java` plus this coordination document changed**; zero production Java, account lifecycle, G21.200 JVM lease, Gradle, manifest, client, or original/source parity changes. Focused test class already in manifest so **507 unique entries unchanged**. **Fresh hosted exact-head** test must prove G1862/G1861/G1860/G21200, Java11 major55, 507/507 focused/full Gradle. Draft/unmerged, Chat1 owns cumulative promotion; avoid importing stacked ancestor commits again.
