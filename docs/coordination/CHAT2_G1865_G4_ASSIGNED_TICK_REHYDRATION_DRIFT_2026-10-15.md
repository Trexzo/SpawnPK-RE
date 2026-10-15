# Chat 2 G18.6.5 — valid G4 ACTIVE hydration rewrites source assignment-tick provenance

Date: 2026-10-15.

**Exact hosted-green gameplay parent** [G18.6.4 #2635](https://github.com/Trexzo/SpawnPK-RE/pull/2635), SHA `ab25f5dc33712d487842b30fb1cacbbebc9be2de`, [Actions #38714711844](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38714711844) SUCCESS, **507/507 focused**, Java11 major55/full Gradle. This branch remains standalone **test/docs-only** on green gameplay, not the still-running newer 509-focus cumulative [#2637](https://github.com/Trexzo/SpawnPK-RE/pull/2637).

## New source-backed semantic drift

- `LocalLabSlayerPersistence.encode` writes `source-assigned-tick` from `status.task.assignedTick`. `decode` validates and exposes it as `Snapshot.sourceAssignedTick`.
- In `LocalLabSlayerRuntime.ensureRestoredOwned`, after decoding a valid ACTIVE extension and ruling out World task collision, the reconstructed task is allocated by `bloodSlayer.requestTask(player, world.clock().tick())` rather than passing the decoded source assignment tick. Thus the **runtime task** carries the **new World's clock tick** as `assignedTick`, even when persisted `sourceAssignedTick` was different.
- The raw hydrated player's snapshot bytes remain unchanged on `status()`; after a legitimate installed terminal kill and G4 persistence write, the namespace's `source-assigned-tick` is replaced by the replay World tick. The original player's extension retains the original assignment timestamp. This test characterizes **provenance drift**, not incorrect original SpawnPK elapsed-time behavior or an exploit.

## G18.6.5 acceptance

1. Register source A in isolated World One; **legitimately** start Monster Hunter with explicit legal source tick `77`. Capture valid ACTIVE0 codec namespace; verify task and persisted `sourceAssignedTick==77`.
2. Create independent isolated World Two (same JVM), with its clock tick intentionally different from 77. Register new WorldPlayer B with identical name; **test-only** copy the exact valid ACTIVE bytes into B's in-memory namespace before G4 hydrate. Verify codec/extension still valid and unchanged; `slayer.status(name)` restores ACTIVE0 but `task.assignedTick` equals the **new** World clock rather than 77.
3. Drive exactly one **real installed** Monster Spawner NPC spawn/death/finalization on World Two. Verify G4 COMPLETED1 and `LocalLabSlayerPersistence.decode(B).sourceAssignedTick` now equals replay World's tick, not source 77. A's namespace still decodes 77; one G1 kill/receipt/coin ground settlement for B. No manual replay of an old NPC identity.

Expected marker `G1865_G4_ACTIVE_SOURCE_ASSIGNED_TICK_PROVENANCE_DRIFT_PASS`.

## Limits

These are isolated LocalLab Worlds with only test-side in-memory snapshot copies: no FilePlayerRepository round-trip, authenticated TCP login, JVM restart, account recertification, actual clock continuity, source-server behavior or original SpawnPK time policy. The *specific* persisted source tick is not preserved by current LocalLab task reconstruction; whether that should change requires an explicit policy and source authority, not automatic rewriting or account recovery.

**Exactly 2 additive files**: existing G1847 test plus this note. ZERO production Java, G21.202 account lease code, Gradle, manifest, main/client or gameplay policy edits; **507 unique focused entries unchanged**. Fresh exact-head hosted CI must prove G1865/G1864/G1863 markers, Java11 major55, 507/507 focused and full Gradle. Draft/unmerged. [Outbox #2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587) and [original server kill/time authority #2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558) remain open.
