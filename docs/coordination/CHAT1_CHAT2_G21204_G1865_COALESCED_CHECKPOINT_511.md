# G21.204 + G18.6.5 — deterministic coalesced checkpoints and Slayer source-tick provenance

## Exact source provenance

Certified shared parent [Chat1 G21.203 + Chat2 G18.6.4 #2640](https://github.com/Trexzo/SpawnPK-RE/pull/2640) SHA `c61238d971840f7528a23eb923dac2aa5fdbc651`, [Actions #38722505402](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38722505402) SUCCESS at 510 focused/Java11/full Gradle.

Chat1 core [G21.204 Draft #2642](https://github.com/Trexzo/SpawnPK-RE/pull/2642) exact head `be5b19a90fdf25b928efcfcd3b6cc20a9755d552`, Actions #38723875039 in progress at creation. Adds one test-only real TCP checkpoint coalescing regression + Gradle manifest **510→511** + research note, zero production code edits. World action holds the existing checkpointLock **from TEST via reflection**, calls `checkpointDue(100)` / `checkpointDue(200)` with distinct `CHECKPOINT_OLD`/`CHECKPOINT_COALESCED` values, checks 2 captures/1 coalesce/1 final checkpoint publication and original opt-in capture Token worker witness; later separate forced World final-save mutation still commits and G21.190 ACTIVE veto remains. No hostile lease swap, durable account epoch or raw-writer safety assertion.

Chat2 exact donor [cumulative G18.6.5 Draft #2641](https://github.com/Trexzo/SpawnPK-RE/pull/2641) HEAD `5a90d0cd573507156f749d8bf2e31ac91e8a19f9`, same certified #2640 parent, Actions #38723741128 in progress. Imported G1847 test Git blob **`43545b1845d48bc7a323ba7c049f2bc4aa4123ec`**, exact original G18.6.5 source note blob `5bc04ff6700dde78b7be3eed5ef9b91b9127871c`, original cumulative donor note blob `4a9016fa090dbf24d6c95f436109f116271e39b7`. Standalone Chat2 #2639 SHA `a0015196c68f0cf77c38dddd08074fa6ca7c8fc1` Actions #38722498548 SUCCESS; does not certify combined donor itself.

Chat2 gameplay negative: valid codec-encoded G4 ACTIVE task sourceAssignedTick=77 is test-only hydrated in fresh independent World with a different tick; runtime task uses new World tick not preserved original. Exactly one unchanged installed Monster Spawner real NPC terminal advances task and writes COMPLETED extension with new tick while source retains77. CUSTOM_LOCALLAB only; no actual TCP relog, account file reload, historical original SpawnPK time rule or safe automatic recovery.

## Changes and verification

Diff vs core #2642 exactly FOUR files: existing Chat2 G1847 test overlay, TWO Chat2 donor docs, and this combined note. No additional production source/Gradle changes; focused manifest stays 511 unique. Do not double-import G18.6.4 or earlier Chat2 tests. Require separate fresh **exact-head CI** for G21.204 core and the G21.204/G18.6.5 combined tree, markers G21204/G1865/G21203/G21184, 511/511 focused, Java11 major55 and full Gradle.

All Draft/unmerged, existing runtime flags default OFF, no main/SpawnPK-Client modifications, grants/replay/automatic ACTIVE review retirement, power-loss or raw account writer claims. Account issue #2540/#2559 and per-consumer outbox #2587 remain OPEN.