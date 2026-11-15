# R71 — Real WorldPlayerPersistence snapshot capture-order fence

**DRAFT, stacked on R70; offline/test-only; no live login/session changes.**

## Problem

The actual WorldPlayerPersistence uses a bounded single-thread IO executor, but
its work may be **enqueued after capture**. A deferred save or final/logout
snapshot captured earlier can be submitted after a newer save already reached
the account repository. An IO task's queue position is not a trusted snapshot
freshness marker. In particular, checkpoint drain sequences are assigned at
drain, not at capture, so they cannot establish source chronology.

R70 atomically *coordinates under R60* the account write, R63 generation and
R64 owner-record publication, but does not independently protect snapshot
source order. That can allow a still-authorized, older snapshot to create a
higher committed R63 generation, rolling back gameplay state.

## R71 source change

The actual WorldPlayerPersistence now assigns a positive, monotonic capture
ordinal while holding the World player's mutationLock and after capturing
each immutable PlayerSnapshot:

- explicit immediate saves and deferred saves: the ordinal is retained on
  SaveTicket, alongside the pre-existing IO submission sequence;
- final/logout saves: same World-thread ordinal before delayed admission;
- autosave checkpoints: each candidate snapshot gets an ordinal **before**
  CheckpointSlot coalescing; the latest pending checkpoint retains its
  original capture ordinal all the way through the drain;
- SaveTask and CheckpointDrainTask both forward the immutable capture ordinal
  into their shared write() method.

R71 adds a separate `OfflineOrderedSaveAdmission` type that *extends* the
existing R69 test hook. No hook present: the old raw repository write path
remains unchanged. A preexisting R69 or R70 hook still receives exactly its
original snapshot/write callback, without new behavior.

The R71 test-owned hook obtains R60's cooperative OS lock and validates
R64 owner/epoch, R63 canonical generation/hash, and R59 pending-trade state
**before** checking the ordinal. An ordinal at or below the highest
previously admitted ordinal is refused before account/ledger/owner writes.

Newer identical snapshots are validated and *advance* the accepted ordinal,
but do not rewrite the identical canonical account or increment the R63
generation. A changed snapshot commits through
`R64OfflineSessionOwnership.saveOwnedUnderLock` and advances the ordinal
only after the coordinated publisher returns successfully. The raw
`PlayerRepository.save` callback provided to the hook is never invoked.

The watermark is **in memory only**, per hook instance. It is not a
durable cross-restart ordering ledger and not a replacement for R64 owner
validation or authentication.

## Real IO tests

`R71OfflineSnapshotCaptureOrderTest` runs actual World commands and the
dedicated WorldPlayerPersistence IO worker against a fresh disposable
FilePlayerRepository and separate R63/R64/journal roots:

1. Capture a deferred snapshot under a World actor at ordinal 1. Mutate
   state and commit a checkpoint captured at ordinal 2. Submit the old
   deferred snapshot **after** that checkpoint, and verify the resulting
   future fails with `R71_SAVE_DENIED stale capture ordinal`. The account,
   R63 ledger and R64 owner bytes stay identical.
2. Capture a newer identical snapshot at ordinal 3; owner and metadata are
   validated, but the R63 generation does not advance. Replay ordinal 3
   against the test hook and verify denial. Commit a newer ordinary save
   at ordinal 4 and a valid delayed final save at ordinal 5. The final-save
   checkpoint suppression remains effective.
3. Replace the owner in a separate JVM, then capture a *numerically newer*
   ordinal 6 in the old World. R64 must still reject it before writes.
4. In a separate disposable World, capture an old **final** snapshot at
   ordinal 1 and hold it. Capture and commit a newer ordinary snapshot at
   ordinal 2; submit the old final and verify refusal before any rollback.
5. In a third World, hold checkpoint tick 100 in the real IO worker with
   a latch. Capture tick 200 and tick 300 checkpoints on the World actor.
   The latter coalesces the pending snapshot. On release, exactly two
   checkpoints commit and the accepted ordinal advances from 1 to 3.
6. All fixtures use a raw PlayerRepository adapter that throws if the
   normal direct writer is invoked, proving the opt-in coordinated protocol
   is used for permitted IO writes. No game sockets, production saves or
   real accounts are touched.

## Limitations and R72 priorities

- No durable high-water marker survives process restart. Capture ordinals
  reset in a new WorldPlayerPersistence instance; within an authorized
  owner, a restart requires authenticated reattachment and a durable epoch
  plus freshness agreement before any live activation.
- No guarantee that a snapshot captured later represents a more recent
  gameplay *mutation* if non-World paths mutate state. Those require their
  own source audit and synchronized ownership checks.
- Checkpoint coalescing preserves only the last pending checkpoint, not
  every intermediate state. R71 proves freshness, not uninterrupted save
  throughput under load.
- Final-save suppression prevents later checkpoints but intentionally does
  not block a new explicit save at the same still-owned World actor. This
  test exposes that ordering issue; it is not yet a terminal save barrier.
- R60 is cooperative, multi-file publication is fail-closed rather than
  power-loss atomic, and R64 offline owner tokens are not authenticated.
- Forced IO shutdown, queue saturation, final-save guarantee after
  interruption, and legitimate recovery of quarantined partial commits
  remain separate work. R72 should audit final-save terminal admission,
  in-flight cancellation and durable source watermarking before any
  production integration.

**R71 DRAFT / STACKED ON R70 / UNMERGED / OFF BY DEFAULT.**
