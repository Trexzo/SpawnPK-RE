# R73 — verified real-IO final save, drain, owner release and World actor removal

## Status

**DRAFT / STACKED ON R72 / OFFLINE ONLY / UNMERGED.** This stage extends
R72's isolated, opt-in persistence protocol. No production `LocalSession`,
network dispatcher, user account, or default `World` installs R73.

## Why R72 did not settle the logout problem

The R72 terminal capture fence prevents later snapshots from being created
or admitted, and its on-disk capture marker detects stale snapshot
publications across restarts. But a final capture alone does not prove
that the actual bounded WorldPlayerPersistence IO queue has written the
snapshot, that earlier checkpoint callbacks have drained, or that the
R64 owner may safely be marked RELEASED.

A final-save future may complete exceptionally due to executor saturation,
IO failure or an injected interruption between the R64 account/ledger/owner
and the R72 marker. Releasing R64 regardless of that outcome would
falsely claim a successful logout.

## R73 implementation

`WorldPlayerPersistence.awaitOfflineIoDrainForTest(timeoutMillis)` adds
an explicit, off-by-default queue sentinel to the real single-worker,
64-slot ThreadPoolExecutor. It:

- is prohibited on the World execution context;
- uses the **actual** bounded queue, not a fake or copied queue;
- uses a bounded admission loop; refuses shutdown, interruptions and
  admission/worker timeouts;
- returns only after the FIFO sentinel runs on the worker, proving
  that tasks admitted before it have finished running;
- does **not** claim that an already-running non-cooperative worker has
  been canceled, or that no *new external producer* can submit later.

`R72OfflineDurableCaptureOrder.releaseVerifiedFinalForTest` holds the
cooperative cross-JVM R60 OS lock, validates the current R64 owner,
checks the exact R72 final capture ordinal and snapshot SHA256 against
its persisted marker and current R63 metadata, then uses the existing
`R64.releaseOwnerUnderLock` to persist RELEASED. An owner may not be
released by this API merely because a save was submitted.

`R73OfflineFinalLogoutCoordinator.finish` performs a strict sequence:

1. From outside the World actor, require the R72 terminal capture fence,
   a genuine final CapturedSave for the exact World actor generation,
   active ownership and checkpoint suppression, with no prior known IO
   failures.
2. Submit the final ticket through real bounded backpressure admission;
   await its **actual** completion with a specified timeout.
3. Queue the R73 drain sentinel behind all previously admitted IO
   tasks and await its completion. Any failure, rejection, shutdown,
   uncertain timeout, or nonempty queue is a hard refusal.
4. Issue one actual World actor command. Under the actor's mutation
   lock, validate suppression, publish R64 RELEASED through the
   R60-locked verified final marker check, then unregister the same
   World generation in the same callback. World unregister cancels
   further queued actor commands and clears per-actor persistence
   checkpoint/terminal suppression state.
5. Verify R64 is RELEASED and the actor no longer owns the player.

The last step is **not** a multi-file crash-atomic transaction: an
abrupt process death between releasing R64 and actor unregister may
leave in-memory state incomplete. However, a persisted RELEASED owner
fails subsequent R64-checked writes. No automatic recovery is performed.

## Deterministic acceptance

`R73OfflineFinalLogoutDrainTest` uses three separate disposable
worlds/accounts:

- **Successful logout:** capture a real autosave checkpoint at tick
  100 and then a deferred final snapshot at ordinal 2. Submit final,
  await completed checkpoint + final through the real worker, and
  release R64 under R60 in the actor callback. Verify account data
  equals final snapshot, R63 generation advanced, R64 is RELEASED,
  World generation unregistered, post-unregister gameplay action denied,
  and post-release R72-checked save refused without changing bytes.
- **Bounded-queue saturation:** a test-only blocker occupies the actual
  IO worker, with exactly 64 synthetic no-op tasks filling the real
  queue. Final capture is genuine, but admission times out. Its future
  fails and R73 must refuse release, leaving the R64 owner ACTIVE and
  all four canonical records unchanged. Release blocker and prove
  queue can drain; no retroactive release occurs.
- **Uncertain final marker:** inject R72's failure after account/R63/R64
  publication but before marker publication. The final future fails.
  The World actor remains registered, the owner remains ACTIVE (but
  its marker mismatch prevents safe resume), and no automatic marker
  repair is attempted.

The synthetic queue filler tests backpressure mechanics; it does
**not** pretend to be 64 real disk writes or a production load
benchmark. Production queue behavior is otherwise unchanged.

Independent Linux and Windows CI run R73, inherited R63–R72
regressions, Java 11 bytecode checks and existing server build checks.
Do not mark this version fully verified until exact-head checks pass.

## Limits and next work

- **Authentication:** R64 offline owner UUIDs are not real login
  authorization, and live LocalSession / socket handlers have not been
  integrated with these offline guards.
- **Snapshot correctness:** gameplay mutations outside the World actor
  or non-cooperative direct FilePlayerRepository writes remain outside
  the R73 proof. R68's actual dispatcher test hook is still offline.
- **Physical durability:** the R72 marker and R63/R64 account files
  are not a single power-loss-atomic transaction; partial publication
  is quarantined and requires explicit recovery design.
- **Shutdown:** R73's bounded drain is a positive pre-shutdown fence,
  not forced-shutdown proof. Failures after interruption are
  indeterminate, and already-running non-cooperative IO cannot be
  assumed canceled.
- **Ingress while closing:** pending producer/admission races outside
  this test fixture must be explicitly closed before production
  integration; single World-thread command order alone does not
  quiesce every network, pet and trade callback.
- **Performance:** no latency/throughput or FPS claims; full
  IO saturation and save/owner checkpoint cost under real gameplay
  remain unmeasured.

### Proposed R74

Audit genuine shutdown interruptions and concurrent ingress under the
same final-save state machine; add fault injection for after-release/
before-unregister and for a blocked worker during forced shutdown.
Preserve fail-closed behavior rather than attempting automatic
repair or session revival.

**OFFLINE ONLY / DRAFT / UNMERGED / NO PRODUCTION ACTIVATION.**
