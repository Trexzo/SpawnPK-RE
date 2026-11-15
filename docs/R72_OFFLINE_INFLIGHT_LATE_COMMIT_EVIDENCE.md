# R72 — Offline durable observation of in-flight late writes after shutdown

## The reproduced bug

R71 proved that queued final saves and checkpoints can be rejected on bounded shutdown or queue backpressure without publishing account bytes. However, shutdown does not undo an **in-flight, uninterruptible `PlayerRepository.save`** that already entered the real `WorldPlayerPersistence` worker. `close()` waits for the worker, interrupts it, and eventually rejects the SaveTicket while the repository may still be executing. The I/O worker can subsequently return after the canceled future and have already committed canonical account, R63 ledger and R64 RELEASED owner evidence.

**R72 reproduces that exact counterexample against a disposable real `FilePlayerRepository`.** R72 does not pretend that marking the Java future exceptionally rolled back the filesystem. Instead it adds separate, immutable, non-authoritative evidence so an offline fresh-JVM reader can distinguish the evidence stages and always fail closed.

## Implementation

`R72OfflineInFlightCommitEvidence` implements the existing **test-only** `WorldPlayerPersistence.OfflineIoWriteAdmission` and delegates validated owner save/release publication to R71.

For one explicitly registered World-owned `CapturedSave.ticket.sequence`, owner UUID/epoch, R63 generation and SHA-256 before/after digests, it writes an isolated evidence directory consisting of unique **R16 atomic-published immutable marker files**:

- `INTENT`: registered before the final snapshot can enter the asynchronous persistence worker; captures exact sequence, owner UUID, epoch, generation, R59 revision and canonical before/proposed after snapshot digests
- `ENTERED`: the real I/O worker has reached R72's commit wrapper, immediately before entering R71's owner-checked commit; the worker may be blocked inside the actual repository afterward
- `CANCEL_OBSERVED`: the real `WorldPlayerPersistence` close/backpressure path has terminally rejected that save ticket, **not** evidence of rollback or worker termination
- `ACK_OBSERVED`: the underlying R71 commit method eventually returned, **not** proof the original canceled SaveTicket succeeded, nor a login authorization
- `FAILURE_OBSERVED`: the commit method reported an error. Some file replacements may have happened before this error, so it is not evidence of rollback

The filename includes a random immutable transaction ID and the phase. Each separately atomic marker contains all immutable fields and a strict schema; a fresh reader validates every companion marker against the INTENT. No marker is ever automatically deleted, promoted or rewritten. An unexpected staging file, corrupted marker, missing INTENT, conflicting phases or mismatched fields blocks inspection.

### Critical shutdown lock behavior

A real final-save `PlayerRepository.save` is executed under **R71's monitor and R60's OS FileLock**. Calling R71's existing dropped-ticket callback from the shutdown thread while the repository blocks could stall shutdown indefinitely. Therefore R72's in-flight cancellation callback **never waits for R71's monitor or R60**: it writes `CANCEL_OBSERVED` independently, leaves the in-flight worker to finish or fail, and returns. It delegates queued, not-yet-entered ticket removal to R71. This is a conservative, test-only accounting observation; it does not revoke an already started OS file operation.

If the cancellation marker cannot be published, the reader still treats the remaining INTENT/ENTERED evidence as uncertain. An unreliable marker write cannot convert ambiguity into permission to recover.

### Offline fresh-JVM inspector

`R72OfflineInFlightCommitEvidence.inspect(root,id)` is **read-only** and returns only blocking statuses:

| Evidence | Classification |
|---|---|
| Missing INTENT | `BLOCK_UNREGISTERED` |
| INTENT only | `BLOCK_REGISTERED_UNCERTAIN` |
| INTENT + ENTERED | `BLOCK_INFLIGHT_UNCERTAIN` |
| Cancellation observed, with no ACK | `BLOCK_CANCELLED_UNCERTAIN` |
| Cancellation and late ACK | `BLOCK_LATE_COMMIT_OBSERVED` |
| Normal ACK with no cancellation | `BLOCK_COMPLETION_NOT_AUTHORIZED` |
| Worker error marker | `BLOCK_FAILED_OR_PARTIAL` |
| Corrupted, conflicting or ambiguous marker data | `BLOCK_AMBIGUOUS_EVIDENCE` |

For **every status** `loginAuthorized()`, `replayAuthorized()` and `quarantineReleased()` return **false**. The inspector intentionally does **not** infer actual account authorization by reading canonical account bytes without the full cooperative OS lock, nor does it retire R57/R59 trade quarantine.

## Windows evidence — November 15, 2026

- Isolated worktree `SpawnPK-R72-InFlight-Evidence-20261115` on the connected Windows desktop; JDK21 `jar testClasses verifyJava11Bytecode --offline --no-daemon` **PASS**, **2,293 Java 11 major-55 classes**.
- **`R72_OFFLINE_LATE_INFLIGHT_WRITE_EVIDENCE_PASS`**:
  1. Capture a final player snapshot on the actual test World thread and bind its R64 owner to the R71 final-save seal and R72 immutable intent.
  2. Submit the `CapturedSave` to the real bounded `WorldPlayerPersistence` executor. Its actual counting `FilePlayerRepository` wrapper enters `save`, signals the test and then **ignores interruptions while waiting on a latch**. No canonical writes have happened yet.
  3. A **different JVM** inspects `INTENT + ENTERED` as `BLOCK_INFLIGHT_UNCERTAIN`; no login or replay is authorized.
  4. Call real `WorldPlayerPersistence.close()`. The 5-second wait plus forced interruption/rejection settles the SaveTicket exceptionally with `SHUTDOWN_IN_FLIGHT`, and `CANCEL_OBSERVED` is atomically published while the repository stays blocked. Canonical, R63 ledger and R64 owner bytes remain unchanged **at that instant**. A different JVM reports `BLOCK_CANCELLED_UNCERTAIN`.
  5. Release the still-running worker. **It subsequently performs the real canonical account write and advances R63 generation to 2 and R64 owner to RELEASED, despite the canceled SaveTicket**. The worker publishes its late ACK. Completion counters and the SaveTicket remain failed while the files have changed. Another JVM reports `BLOCK_LATE_COMMIT_OBSERVED`, still with no authorization.
  6. Unknown IDs and a deliberately malformed copied ACK marker are rejected fail closed.
- **Separate hard-stop child JVM**: the test-owned repository invokes `Runtime.halt(93)` upon reaching `save`. Its parent observes `INTENT + ENTERED` but no ACK/CANCEL, confirms canonical generation/owner remains at the prewrite state, and a separate JVM reports `BLOCK_INFLIGHT_UNCERTAIN`. No automatic abandoned-marker cleanup is performed.
- The same Windows worktree passes R71 final/checkpoint collision and saturated queue, R70 coalesced checkpoint owner, R69 actual IO-commit fencing, and inherited uninterruptible/checkpoint shutdown suites: **6/6** suites PASS.
- Independent Linux and Windows GitHub CI also exercises R55–R72 persistence, session ownership, real dispatcher, paired trade recovery and ordinary trade-flow suites.

## Guarantees NOT established

1. **Not a live hook.** R72 is an isolated test-only wrapper; no `LocalSession`, `World` default, production I/O queue, network, game tick, player or real account installs it. Actual live persistence remains unchanged.
2. **Evidence is not a durable transaction protocol.** Each marker is individually atomic-published, with no cross-file atomicity, persistent OS-lock ownership or guaranteed parent-directory fsync. A power failure may leave missing markers, staged files or partial canonical/ledger/owner writes. Any disagreement must remain quarantined; a marker alone cannot repair it.
3. **Cancellation does not undo an in-flight filesystem operation.** R72 deliberately observes rather than prevents a late write after a canceled Java future. Correct containment requires preventing an unsafe new session takeover while an old writer can still complete, or a durable session/commit-intent recovery protocol with fenced commit generations.
4. **No authenticated session issuer.** The R64 owner UUID and generation are test-controlled; R72 cannot prove that the user who supplied a token is an authenticated game account holder.
5. **No automatic rollover/replay.** The R72 inspector neither authorizes account load/login nor deletes/manipulates incomplete intent, staged files, R57/R59 journal entries or owner records.
6. **No exactly-once or crash-replay proof.** Successful or failed write outcome after an abrupt JVM stop remains uncertain without broader protocol reconciliation.
7. **No noncooperative protection.** R60 only coordinates filesystem writers voluntarily using its root. Other save paths outside the gate remain unprotected.

## R73 priority

Design a **test-only, durable pre-admission write-intent fence** that blocks a new owner from taking over while an older committed-but-unacknowledged write may remain active, even when its SaveTicket future was canceled. The admission gate must combine durable intent/owner revision with the R60 lock and refuse unresolved R72 markers across **fresh JVM restarts**, without clearing stale intents solely by timestamp or process absence. Audit how R64 replacement and real `WorldPlayerPersistence` shutdown would participate in an eventual authenticated production protocol; do not enable live hooks until each writer path uses the same authority.

**R72 DRAFT / STACKED ON R71 / UNMERGED.**
