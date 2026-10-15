# G21.204 — deterministic two-checkpoint coalescing preserves original JVM owner witness

## Exact certified source

Based on green Chat1+Chat2 G21.203/G18.6.4 [Draft #2640](https://github.com/Trexzo/SpawnPK-RE/pull/2640) exact SHA `c61238d971840f7528a23eb923dac2aa5fdbc651`, hosted [Actions #38722505402](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38722505402) SUCCESS at 510 focused, Java11 major55 and complete Gradle. Core G21.203 [#2638](https://github.com/Trexzo/SpawnPK-RE/pull/2638) Actions #38722308534 SUCCESS; Chat2 G18.6.4 [#2637](https://github.com/Trexzo/SpawnPK-RE/pull/2637) Actions #38722151297 SUCCESS. This G21.204 change is separate, not certified by ancestor runs.

## Precise question

G21.203 carried the immutable original in-JVM opt-in account lease witness with World-owned `PendingCheckpoint` snapshots. But a positive single-checkpoint result did not show that `CheckpointSlot.latest` coalescing replaces snapshot, owner and original Token evidence *as one object*, or that a prior captured checkpoint cannot incorrectly publish after a later coalesced snapshot.

## Deterministic test-only method (zero production Java changes)

New `G21204CoreCoalescedCheckpointOwnerWitnessTest` starts a real synthetic 317/ISAAC TCP owner with opt-in JVM lease, OS account lock, ACTIVE review intent, and forced World final-save fixture. It uses Java reflection **inside the test only** to retrieve the private checkpoint queue lock, then runs one actual World-owned command holding that lock while the same genuine WorldPlayer executes two eligible calls: `checkpointDue(100)` after snapshot extension `CHECKPOINT_OLD`, followed by `checkpointDue(200)` after extension `CHECKPOINT_COALESCED`. Holding the lock from the World thread prevents the asynchronously scheduled checkpoint worker from draining the first pending item until coalescing occurs. This is deliberately a private deterministic fixture, NOT new production locking, and assumes the local current implementation uses that exact lock.

The World action asserts exactly 2 captures and one coalesced replacement; after releasing the lock, require checkpoint-written counter advances exactly once, disk state is **only the latest `CHECKPOINT_COALESCED`**, and the G21.202 capture-bound worker Token-check counter advances after the worker boundary. The test then performs a separate World mutation followed by healthy real forced final save with `WORLD_FINAL_FORCED`, checks lease release and second TCP admission veto from G21.190 ACTIVE. Older G21.184 late-enable legacy test remains intentionally unsafe; this regression should not reclassify it as a repair.

## Limitations

No production code, existing gameplay test or account repository modifications. This does not simulate a malicious player replacing Token identity between coalesced captures, a delayed worker after actual replacement, machine power loss, an authenticated durable owner epoch, a forced owner-bound final receipt, or a safe marker-retirement protocol. Coalescing on a single already admitted World owner is a *positive latest-wins and provenance-retention* test; owner-replacement cutpoint negatives remain an independent future milestone. Strict/mailbox/terminal and raw writers are outside the demonstrated guard.

Exactly **3 changed files** against #2640: new regression, `server/build.gradle` task+focused manifest **510→511 unique**, this research note. Require fresh exact-head hosted CI markers `G21204_COALESCED_CHECKPOINT_WITNESS_PASS`, G21203/G1864/G21184 and 511/511 focused, Java 11 major55, full Gradle. Keep Draft/unmerged, flags default OFF, no main/client changes or ACTIVE/recovery/replay/grants. Core account issues #2540/#2559 and Chat2 gameplay outbox #2587 remain OPEN.