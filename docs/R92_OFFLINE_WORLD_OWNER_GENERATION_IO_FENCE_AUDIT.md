# R92 — offline World actor generation lease at durable IO admission

**DRAFT / TEST-ONLY / STACKED ON R91 #3136 / UNMERGED / UNDEPLOYED**

## Source-confirmed gap

R91 captures an immutable snapshot, ordinal and producer stamp under the
current `WorldPlayer.mutationLock()`. The existing
`WorldPlayerPersistence.submitCapturedWithBackpressure` checks the
captured owner generation when the ticket enters the IO queue, but
`SaveTask.run -> write -> R91/R90/R89` did not recheck that generation.
An IO worker delayed by an unrelated read could accept the old actor's
previously valid snapshot *after* the World unregisters that actor and
registers its successor with the same username. R89's ordinal and R90's
producer incarnation alone do not prevent same-incarnation actor changes.

## Optional R92 behavior

- R91's immutable offline `OfflineCaptureStamp` now also has a
  **non-serialized** reference to the *actual source WorldPlayer instance*
  bound at capture while its mutation lock is held. The R90 on-disk
  record, R89 watermark, stamp digest and existing R91 source fields
  remain unchanged.
- Existing `R91OfflineWorldCaptureGate.installForTest` preserves
  R91 behavior. A separate
  `installGenerationFencedForTest(world, session)` opts into R92.
- On the dedicated IO worker, R92 passes the stamped source actor and
  generation through the **existing**
  `World.withOpenPlayerOwnershipIfCurrent` guard. This holds the
  World lifecycle lock across the R60 OS lock and R89/R90 durable
  publication, preventing a cooperating World unregister/register
  transition from interleaving the commit.
- Stale or unbound actor evidence refuses BEFORE R90 PREPARED,
  R89 PREPARED and account/ledger/owner changes. The failed write
  does not poison the durable gate.
- No production `World`, `LocalSession`, network ingress, client,
  player account or runtime path opts into this lease.

## Regression

The extended `R91OfflineWorldCaptureProvenanceTest` now also exercises
R92 with a real isolated World and real single-threaded IO executor:

1. A deliberately blocked repository **LOAD** occupies the sole IO worker.
2. The old actor captures a stamped deferred save under World ownership
   and submits that ticket into the real bounded save queue.
3. The World unregisters the old actor and registers a fresh actor under
   the same username while the queued save has not executed.
4. The reader is released. The old ticket is refused by the R92
   owner-generation lease; canonical account, generation ledger, R64
   session, R89 watermark and R90 producer bytes remain unchanged.
5. The fresh actor captures a new snapshot at the next ordinal and
   commits it; reopening the durable producer highwater verifies the
   forward path.
6. A manually constructed stamp without an actual World-bound actor
   is rejected before the same canonical files are touched.

The Linux/Windows R92 workflow runs the expanded R91/R92 test and
inherited source-only regression chain with Java 11 bytecode checks.
The previous R91 and full server workflows remain in the PR.

## Explicit limitations and performance cost

This is a **cooperative offline test-only lock**. It intentionally
holds the World lifecycle lock during synchronous disk I/O and can
stall World events, registration and logout. **Never enable it in the
live latency-sensitive server without replacing the lock with a
proper nonblocking ownership/IO transaction design.**

The actor reference exists only in the same JVM; it is not a durable
logical player identity, authenticated remote producer ID or
cryptographic attestation. Hostile in-process code can still forge
or bypass package-local hooks. The R90/R89/R64/R63 records are not
an atomic multi-file transaction, PREPARED states are quarantined
without recovery, and a child-process kill is not physical power-loss
durability proof. No production integration, account migration,
PvP reward/replay guarantees or live gameplay claim is made.

**Exact-head GitHub CI verification is required before treating this
experimental branch as validated. Keep draft, unmerged and undeployed.**
