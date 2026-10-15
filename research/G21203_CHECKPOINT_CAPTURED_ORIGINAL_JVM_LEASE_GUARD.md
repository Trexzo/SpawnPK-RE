# G21.203 — coalesced World autosave checkpoint carries capture-time opt-in JVM Token

## Exact verified starting point

Based on certified combined [G21.202 + G18.6.3 #2636](https://github.com/Trexzo/SpawnPK-RE/pull/2636), commit `ec0fe92516acf6aa1d740f643a0f49769baf812a`, hosted [Actions #38714817951](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38714817951) SUCCESS (509 focused, Java 11 major55/full Gradle). Separate core #2634 Actions #38714524386 SUCCESS and Chat2 G18.6.3 #2633 Actions #38714176668 SUCCESS.

## New opt-in writer coverage

Previously only queued normal/final `WorldPlayerPersistence.CapturedSave -> SaveTask` preserved an immutable G21.202 original JVM lease `Token` from real World snapshot capture through FIFO publication. The `CheckpointDrainTask` instead called its separate `write(...,checkpoint=true)` overload with a null SaveTask and had no original-token check.

G21.203 extends `PendingCheckpoint` to contain the exact captured `WorldPlayer` instance, World registration generation and `LocalJvmAccountSessionLease.SaveCaptureWitness` created *with* the tick-owned immutable checkpoint snapshot under `player.mutationLock`. Coalescing replaces `slot.latest` with the whole immutable PendingCheckpoint object (snapshot + original provenance), not independently the bytes or token. `drainCheckpoints` passes the exact pending to a separate `write(...,SaveTask,PendingCheckpoint)` overload, which calls `requireCapturedWitnessBeforeWrite` immediately before file-backed publication. If a captured leased token is released/replaced or owner/gen/account differs, the worker fails the checkpoint rather than attributing it to a later account-path owner. A real previously unleased default-OFF World still follows the intentionally unsafe G21.184 late-toggle behavior; unknown/manual non-file paths remain unchanged. The normal/final worker guard remains byte-identical in behavior.

## Test and strict limitations

New `G21203CoreAutosaveCheckpointOriginalLeaseTest` starts a genuine synthetic 317/ISAAC TCP login with opt-in account lease, OS lock, ACTIVE review and forced final save test settings, observes its registered World owner, and manually preflights G21.202 original lease witness negatives. Within actual `World.submit` execution context, it writes checkpoint extension state and invokes `checkpointDue(100)`. It must observe `checkpointWrittenCount` advance, exact on-disk file-backed snapshot state `CHECKPOINT_VERIFIED`, and `captureBoundWorkerChecksForTest` advance **after the checkpoint start**, proving actual coalesced worker checked the original lease. Then a distinct in-World mutation persists through healthy forced final save `WORLD_FINAL_FORCED`; healthy token release and unchanged ACTIVE refusal on a subsequent World TCP login remain required.

**This is only JVM opt-in current Token and capture evidence.** It is NOT an authenticated durable owner epoch or account compare-and-swap, does not cover strict prepared barriers, mailbox terminal writes or raw account writers, and cannot grant automatic ACTIVE retirement/rehydration/recovery. No power-loss or original SpawnPK server rule claim. Checkpoint coalescing/replacement races need further dedicated negative tests after this positive path.

## GitHub acceptance

Exactly 4 changed files vs #2636: existing `WorldPlayerPersistence.java`, one new Java test, `server/build.gradle` task/manifest **509→510 unique**, this research note. No existing production gameplay, Chat2 G18.6.3, LocalSession, FilePlayerRepository, server defaults, main or client changes. Require fresh exact-head hosted CI with `G21203_CHECKPOINT_CAPTURED_JVM_LEASE_PASS`, G21202/G21184/G1863, **510/510 focused**, Java11 major55 and full Gradle before certification. Keep Draft/unmerged. #2540/#2559 and Chat2 outbox #2587 remain open.