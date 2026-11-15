# R69 — Offline real persistence IO-commit ownership fence

## Scope and status

**DRAFT, stacked on R68, test-only and unmerged.** R69 tests the real
`WorldPlayerPersistence` IO worker at the precise boundary before
`PlayerRepository.save(snapshot)`. It does not activate an R64 account-owner
fence in `LocalSession` or `World`, enable actual multiplayer, publish any
credentials, or modify live accounts.

## Source trace

- `captureDeferredSave` and `captureDeferredFinalSave` capture immutable
  player snapshots only on the World execution thread after the World actor's
  in-memory generation check. `submitCapturedWithBackpressure` rechecks World
  actor ownership before queue admission, but neither step protects against a
  cross-JVM R64 account-owner transfer **after enqueue**.
- `checkpointDue` captures at tick multiples of 100 and keeps one
  `CheckpointDrainTask` per actor. The latest waiting snapshot replaces the
  older waiting snapshot, while a prior checkpoint may already be in flight.
- `SaveTask.run` and `drainCheckpoints` both reach `write`, which formerly
  called `repository.save(snapshot)` with no persisted owner recheck.
- `WorldPlayerPersistence.write` now contains an explicit **off-by-default**
  `OfflineSaveAdmission` seam. Normal calls still enter the repository
  directly. No production constructor or session installs the new hook.
  Duplicate hook installation is rejected. The test-only
  `R69OfflinePersistenceAdmission` holds R60's cooperative cross-JVM OS lock
  while checking the R64 owner, R63 canonical generation/hash and R59 trade
  state, then invokes the actual repository writer under the same lock.

## Exact deferred-write regression

The isolated test creates four fresh temp directories (journal, canonical
accounts, R63 generation records, R64 owner records), initializes an existing
test account, claims owner A and creates a real `World` with the normal
single-thread persistence worker and a counting wrapper around a real
`FilePlayerRepository`.

1. An actual World actor command captures checkpoint tick 100. Its IO worker
   reaches the R69 hook, signals a latch, and blocks **before** taking R60.
2. World actor commands capture tick-200 and tick-300 checkpoints while the
   first is still in flight. Tick 300 replaces tick 200. A final logout save
   is captured, suppresses tick 400, and is queued behind the drain task.
3. A separate Java process performs the existing R64
   `R64SessionFenceWorker takeover`, replacing owner A with epoch-2 owner B.
   The canonical account, R63 ledger and R64 owner file are sampled.
4. Release the first IO hook. Both checkpoint writes and the final save reach
   `R64OfflineSessionOwnership.requireActiveOwnerUnderLock` and refuse as
   stale before the counting repository wrapper or actual
   `FilePlayerRepository.save` is called. The final save's completion future
   must fail with the stale-owner IOException; stale checkpoints must not
   count as written. All three sampled files must remain byte-for-byte
   unchanged.
5. After those refusals, owner B saves through the *existing coordinated*
   `R64OfflineSessionOwnership.saveOwned` API, proving the previous stale
   worker did not block successor progress.
6. A separate isolated World without any R69 hook still captures, queues and
   writes a deferred snapshot through the original repository path.

The latch controls ordering explicitly; the test does not depend on arbitrary
sleep timing. The world is isolated and uses a 60-second test tick; there are
no game sockets, accounts from production, or manual logins.

## What is **not** proven

- This hook checks owner state before allowing the raw
  `FilePlayerRepository.save` call but **does not** advance R63 generation
  and R64 owner records if the writer is permitted. Such an authorized raw
  write could produce an R63 hash mismatch. For that reason the positive
  owner-B write is deliberately exercised using the preexisting coordinated
  R64 API. **This gate is not suitable for production activation.**
- Only cooperative writers participating in the same R60 lock are covered.
  Direct legacy writes outside the protocol remain outside this protection.
  Multi-file crash atomicity, hardware power-loss durability, credential
  authentication, journal retirement, real trade callbacks, live login and
  real sockets are not established.
- Forced shutdown and 64-task backpressure behavior are not changed. This
  test uses the bounded queue's ordinary `submitCapturedWithBackpressure`
  path, asserts a genuine queued final save, and checks completion/failure.
  It does not claim to cover races with forced `shutdownNow`, nor automatic
  checkpoint abort metrics. A stale checkpoint rejected at IO currently
  increments the existing `failed` counter but not `checkpointRejected`;
  the latter counts queue/abort rejection, not all commit failures.

## Next audit

R70 should investigate safe account+R63+R64 commit composition for permitted
saves, especially reentrancy with actor/World locks, checkpoint generation
binding, shutdown races and interrupted IO. Keep that separate from
authenticated real-session authority, and require independent source review
before any production activation.

**R69 DRAFT / STACKED ON R68 / NO LIVE INTEGRATION.**
