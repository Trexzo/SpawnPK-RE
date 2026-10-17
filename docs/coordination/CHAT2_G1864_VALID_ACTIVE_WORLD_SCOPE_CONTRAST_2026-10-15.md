# Chat 2 G18.6.4 — identical valid G4 ACTIVE snapshot collides in old World, restores in fresh World

Date: 2026-10-15.

**Exact certified gameplay parent** [G18.6.3 #2632](https://github.com/Trexzo/SpawnPK-RE/pull/2632), SHA `aba5c639ad8bc05f3cf1395d1d1bff7d4acd569f`, hosted [Actions #38712623688](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38712623688) SUCCESS: **507/507 focused**, Java11 major55/full Gradle. A separate G21.201 + G18.6.3 [#2633](https://github.com/Trexzo/SpawnPK-RE/pull/2633) is a 508-test integration under fresh hosted CI and is NOT used as this test's source base.

## Source-backed hypothesis

G18.6.3 showed that a VALID historical `blood-slayer-g4` ACTIVE extension is rejected if a completed `SlayerTaskService.latestTask(playerName)` already exists in the current World. The conclusion should **not** be generalized to codec invalidity or universally unrecoverable ACTIVE snapshots. `LocalLabSlayerRuntime.ensureRestoredOwned` (i) decodes the exact persisted extension; (ii) refuses when same World runtime already has a task for that username; (iii) otherwise creates a fresh LocalLab task from the persisted state under the current WorldPlayer. These are **World-lifetime-dependent semantics**, not a durable ledger of original SpawnPK ownership.

## G18.6.4 contrast regression

1. Register A in isolated World One, start a **legitimate** G4 Monster Hunter task and save a copy of its exact codec-valid ACTIVE0 extension. Finalize one actual canonical NPC death through the unchanged installed Monster Spawner observer: the World task and A's owned extension advance to COMPLETED1; G1 records one receipt/kill and settles one coin. Unregister A.
2. Register same-name B in the **SAME old World**, copy the exact older ACTIVE extension to B in test-owned memory before hydration. As World One has `latestTask(name)` already completed, G4 status rejects it with **`PERSISTED_STATE_COLLIDES_WITH_LIVE_RUNTIME`** and no task exposed to B. B's ACTIVE bytes remain intact.
3. Independently instantiate **fresh World Two** (same JVM; this does not represent actual account file reload or process restart), register C with same normalized username and **copy the exact same ACTIVE extension** into C before hydration. With NO World-local task collision, G4 status restores ACTIVE0. Execute ONE distinct World Two actual installed NPC death: G4 progresses/completes C's task and writes COMPLETED1 to C's snapshot; G1 records one receipt/kill and settles one coin. Old World B remains quarantined and unchanged throughout.
4. Assert separate receipt maps (one per World) and single ground settlement in each World, no direct replay of an old NPC identity, no cross-World credit/grants.

Expected marker `G1864_G4_VALID_ACTIVE_WORLD_SCOPE_CONTRAST_PASS`.

## Boundaries

No file repository snapshot save/load, real TCP account login, JVM restart, power loss, recovery, autonomous replay, original SpawnPK Slayer policy, or external exploit. Both namespace copies are **test-only in-memory**. The same valid bytes receiving different outcomes is a **CUSTOM_LOCALLAB World task lifecycle constraint**, not proof which source is right. Future [outbox #2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587) must explicitly bind immutable event/recipient/task identity and decide ownership-transfer/recovery policy; [original reward authority #2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558) remains unknown.

**Two files only**: extend existing G1847 installed-observer regression plus this note. Zero production Java, Gradle/manifest, G21.201 or account/core, or client changes. Focused remains 507 unique; fresh exact-head hosted CI needed (G1864/G1863/G1862 markers, Java11 major55, 507/507 focused, full Gradle). DRAFT/unmerged. Chat1 decides if/when to combine with newer certified core; do not import the stacked ancestor history twice.
