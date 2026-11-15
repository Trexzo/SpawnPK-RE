# R73 — Offline durable write-intent admission fence for account takeover

## Purpose

R72 proved a late persistence write can **commit after the original SaveTicket was canceled by forced shutdown**. The cancellation is a Java future outcome, not rollback. A new R64 owner must not take over while the old session's final-save write is registered, executing, canceled-but-in-flight, failed with uncertain partial publication, or completed without a separate reconciliation decision.

R73 is the first **opt-in, offline-only test protocol** which closes the *registration vs takeover time-of-check/time-of-use race* using the same real R60 cross-JVM exclusive OS FileLock on BOTH operations.

**Neither production `LocalSession` nor default `WorldPlayerPersistence` invokes R73. Direct `R64.replaceOwner` is retained for the earlier offline tests and can bypass this new gate. This is not a production login security boundary.**

## Implementation

### Registration before queue admission

`R73OfflinePendingWriteAdmission.registerFinalBeforeQueueForTest(captured, expectedOwner, recorder)`:

1. Acquires R60's established exclusive file lock for the journal root, verifies the separate test-owned evidence root and actor/account folder roots, then verifies R64's persisted ACTIVE owner UUID/epoch and R63 canonical hash/generation/R59 journal revision with the lock still held.
2. Refuses if the account already has a durable R73 intent, an ambiguous R16 stage for that intent, or relevant R72 evidence. An unknown/corrupt R72-shaped marker blocks conservatively rather than assuming its account can be safely identified.
3. Atomically publishes a **never-auto-retired account-specific R73 intent** in the evidence root, recording owner UUID, epoch, captured account generation, journal revision, before/after SHA256 snapshot digests and exact save-ticket sequence.
4. Only AFTER this durable blocker exists, calls R72's existing explicit registration (R72 INTENT + R71 final-seal) **inside the same OS lock**, and returns the R72 evidence UUID. The caller then submits the immutable snapshot to the real I/O queue.
5. If a later step fails, R73 does not delete the already-published blocker. The state remains fail closed, requiring a future independently reviewed reconciliation protocol.

All registration is performed against **test-owned account directories**. The R64 token remains a test fixture, not authenticated user credentials.

### Evidence-aware owner replacement

`replaceOwnerIfClear(expectedPrior)` acquires the **same R60 OS FileLock** and, within the same held lock, scans R73 and legacy R72 evidence and invokes the extracted `R64OfflineSessionOwnership.replaceOwnerUnderLock` only if the account has no observed unresolved write. It never performs an unlocked evidence scan followed by a separate lock acquisition for takeover.

`R64.replaceOwner` now delegates to the same held-lock replacement implementation while retaining its old behavior and test-only contract. Its existing callers do **not** automatically opt into R73's evidence preflight.

The R73 preflight also detects pre-existing **R72-only** intent/phase evidence, including after an abrupt child-JVM halt. This is a conservative compatibility check; **legacy R72 producers which do not share R73's registration lock are not race-safe with takeover**. Only the new paired R73 registration/owner-transfer protocol is serialized against itself.

### Evidence lifecycle

R73 issues **no automatic retire/release or timeout**. Once an account has a R73 blocker, takeover remains blocked **even after** the R72 worker writes an ACK and its R64 owner record becomes RELEASED. An ACK only means that some I/O completed; it is not proof of a replay-safe and crash-reconciled global account state.

R73's `inspect(username)` is read-only and returns either a blocking status or `READY_FOR_OTHER_OFFLINE_CHECKS`; **even READY returns loginAuthorized=false**, repairAuthorized=false, writeIntentRetired=false and quarantineReleased=false.

There is no timestamp-based stale-file deletion, guessed process liveness, automatic stage-file promotion, clearing of R57/R59 pending trade intents, or account recovery.

## Connected Windows acceptance — November 15, 2026

- Isolated disposable Windows worktree `SpawnPK-R73-Unresolved-Takeover-20261115`, JDK21 `jar testClasses verifyJava11Bytecode --offline --no-daemon` — **BUILD SUCCESSFUL**, **2,300 Java11-major-55 classes**.
- `R73_OFFLINE_UNRESOLVED_WRITE_TAKEOVER_FENCE_PASS` verifies:
  - Before queue submission, the actual test-owned World actor captures a final account snapshot and registers a R73 permanent blocker + R72 immutable intent under the same OS lock.
  - A **different JVM** attempting the R73 owner-transfer path is refused specifically on unresolved durable evidence. Refusal does not modify the account, R63 ledger, R64 owner record or immutable marker.
  - The corresponding real `WorldPlayerPersistence` I/O worker enters an uninterruptible test-owned `FilePlayerRepository.save`. An independent JVM attempting takeover is refused on R60 lock contention while the write is active, both before and after `close()` settles the SaveTicket exceptionally.
  - When the writer is released **after close returned**, the original account, generation and RELEASED owner record can still be published despite the canceled SaveTicket, as R72 demonstrated. A fresh independent JVM attempting takeover is now refused specifically on the persisted R73 blocker, not promoted by an observed late ACK.
  - An unrelated second test account, with no R73/R72 evidence, can replace its owner successfully. Reuse of that account's old owner UUID/epoch is refused.
  - Duplicate final-save registration for the blocked account is refused and the existing R73 marker bytes remain unchanged.
  - An isolated R72-only legacy hard-stop child leaves INTENT+ENTERED without ACK. A newly constructed R73 gate in another JVM detects the earlier R72 evidence even though no R73 blocker was issued, and refuses takeover.
  - A synthetic ambiguous R16 staging file for a separate account's R73 marker blocks replacement. A malformed R72-shaped entry with no attributable username also fails closed; neither negative control is automatically cleaned by the gate.
- Inherited R72, R71, R70, R69, R64 and persistence shutdown suites run in the same Windows worktree; independent GitHub Linux/Windows CI covers the entire R55–R73 chain and normal trade flow.

## Limitations and production blockers

1. **No live integration.** R73 is opt-in. The current real login path, direct R64 offline helper, producer registration and real persistence worker are not bound to R73. It does not globally protect noncooperative writers or authentication.
2. **Permanent fail-closed blocker.** R73 does not make an account recoverable or permit a subsequent valid session after any registered write. A future explicitly reviewed, crash-safe reconciliation and retirement proof is essential before considering live deployment; simply expiring/deleting markers would reintroduce the race.
3. **Only cooperating registration/takeover are serialized.** Unwrapped R72 registration or direct R64 replacement can bypass the new protocol; R73's legacy scan is diagnostic and cannot make those uncooperative paths atomic.
4. **Global OS lock contention and World-thread lock ordering.** R60's lock serializes every registered pair/account for that journal root. The test registration intentionally occurs on a World actor thread under its mutation lock; applying such blocking locking to a live game tick could create latency, lock-order inversions and deadlocks. A production design needs a separate authenticated admission/service boundary.
5. **Not an atomic multi-file transaction.** R73 intent, R72 stage markers, account, R63 ledger and R64 owner record are individual atomic publications. Power-loss directory fsync, physical storage faults and partial interrupted replacements remain unproven. Any invalid evidence remains quarantined.
6. **No system-wide hardening.** A separate noncooperative process, direct `PlayerRepository.save`, source plugin or another unguarded server path may write without R60. UUID/token possession is not authenticated user identity.
7. **No automatic login/trade retirement.** R57/R59 paired-trade quarantine remains in effect regardless of these write-intent markers. The R62 completion certificate alone does not authorize login.
8. **Not exactly-once.** A canceled final save may still perform a late commit. R73 prevents cooperating owner takeover during uncertainty but does not make the canceled future accurate or undo it.

## R74 candidate

Build an **offline, read-only reconciliation certificate** requiring exact R72 intent+ACK, matching R63 canonical generation/hash, matching R64 owner RELEASED phase, clear R59 journal, and a proven absence of an active R60 holder. Negative controls must cover canceled ACK, partial account/ledger/owner file publication, unknown staging, duplicate records and owner revision mismatch. A certificate should remain non-authorizing until an independently validated, durable retirement protocol exists. The immediate goal is to **explain** why an account remains quarantined, not to automatically release it.

**R73 DRAFT / STACKED ON R72 / UNMERGED.**
