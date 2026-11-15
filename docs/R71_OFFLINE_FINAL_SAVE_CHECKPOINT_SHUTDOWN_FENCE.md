# R71 — Offline final-save/checkpoint collision, forced shutdown and backpressure fence

## The race

R70 propagates a frozen owner token alongside each coalesced checkpoint in the real WorldPlayerPersistence queue. But a checkpoint captured under owner A can be **in flight** when A captures a final logout save. If A's earlier checkpoint commits first, it advances R63/R64 generation and may invalidate the final save's older token. Conversely, after a final logout successfully releases A, a delayed checkpoint must never publish A's old snapshot.

R71 implements and tests an **explicit, off-by-default offline final-save seal**. It does not install a production session hook or alter normal persistence behavior.

## Implementation

`R71OfflineFinalSaveCheckpointFence` is a package-private test-only implementation of the existing R70 `OfflineCheckpointOwnerCapture` and R69/R70 `OfflineIoWriteAdmission` interfaces. It delegates actor/generation snapshot capture to R70, ordinary explicitly bound manual callbacks to R69, and serializes its own final-save and checkpoint critical sections using a short-lived in-process seal lock around the R60 OS-lock-protected I/O commit.

During an explicitly test-owned `captureDeferredFinalSave` call on the World thread, a `sealFinalSaveForTest(captured, expectedOwner)` call records an immutable triple: **exact owner token, one captured-final ticket sequence, and SHA-256 of the final snapshot**. It requires a matching test-bound WorldPlayer instance and actor generation. It does not acquire the R60 file lock while holding the World actor mutationLock, preventing that particular lock-order inversion. Existing World checkpoint suppression still applies at final-save capture.

When a captured checkpoint reaches the real asynchronous I/O worker:

- A missing, released or wrong-account checkpoint token is refused.
- If an earlier/same R64 ownership epoch has been final-sealed, its checkpoint is refused **before `PlayerRepository.save`**, even if it was captured before the seal.
- A later owner epoch must first be granted **explicitly via the durable R64 replacement protocol**. Only its own captured checkpoint and token can proceed.
- When authorized, the checkpoint uses the exact captured owner and the usual R64/R63/R59 validation under R60's real cross-process exclusive OS lock before publishing canonical account, generation and owner evidence.
- Checkpoint commit and final-seal publication are serialized inside this **one test gate instance**. If a checkpoint fully committed before the final seal, the final snapshot may have a stale generation and is **refused**, not silently rebased. R71 does not claim that a successfully captured final snapshot can always be saved despite that earlier race.

When the final save reaches the same real I/O worker, the gate consumes its one-shot ticket and validates the frozen digest and session token. Under **one R60 OS lock**, it invokes R64's held-lock save, which performs the canonical repository write and advances R63/R64, then invokes `releaseOwnerUnderLock` to durably publish `RELEASED` before dropping the cooperative OS lock. A replacement session cannot acquire the same R60 lock between a successful final save and the RELEASED record.

If repository publication fails, or if the final ticket is dropped on backpressure/shutdown, **the terminal seal is retained**. Nothing automatically retries, clears the seal, guesses a newer owner, or promotes a checkpoint from the old session. The R71 test gate keeps its owner seals **only in memory**, so a process crash loses this extra seal: production recovery after restart is not proven.

`WorldPlayerPersistence` adds a default no-op `OfflineIoWriteAdmission.onDroppedSaveForTest(sequence)` callback. The actual `SaveTask.reject` invokes it when a task is rejected due to queue backpressure, shutdown or forced cleanup, allowing R71 to remove a final ticket before a canceled save can be replayed. **With no test hook installed, the production writer and discard path are unchanged.** R71 does not automatically erase a failed or canceled final-save's terminal checkpoint seal.

## Local Windows acceptance — November 15, 2026

Isolated worktree `SpawnPK-R71-FinalSave-Checkpoint-20261115`, JDK21 Gradle `jar testClasses verifyJava11Bytecode --offline --no-daemon`: **BUILD SUCCESSFUL**, **2,285 Java11 major-55 classes**.

New deterministic `R71_OFFLINE_FINAL_SAVE_CHECKPOINT_SHUTDOWN_PASS` covers four scenarios:

1. **Already-queued checkpoint vs final logout.** A World tick captures checkpoint A and the *actual I/O worker* pauses before canonical commit. The World actor captures and seals final save A. Checkpoint capture suppression rejects later new A checkpoints. The earlier checkpoint resumes, is refused before any repository call, and canonical/ledger/owner bytes remain unchanged. The final save then commits exactly once and publishes the RELEASED owner under the same R60 OS lock.
2. **Old callback and successor checkpoint.** A captured old manual callback dispatched after RELEASED fails on stale owner without reaching the repository. The same final `CapturedSave` cannot be submitted a second time. A test-authorized explicit R64 owner epoch 2 can subsequently checkpoint its own rehydrated snapshot, advancing R63 generation without using A's old seal.
3. **Repository error before canonical publication.** A test-owned repository throws before the final file replacement. Neither canonical bytes nor generation/owner records advance. The final ticket is consumed, checkpoint capture stays suppressed, and the seal remains fail-closed. The test does not automatically reconcile or retry.
4. **Forced shutdown and queue backpressure.** A real repository `load` blocks the single I/O thread. Checkpoint A and final save A are queued behind it; `WorldPlayerPersistence.close()` reaches bounded forced shutdown and drops both. The pending final token is revoked and future is rejected; checkpoint and account writes do not occur. Separately, all **64 real I/O queue slots** are filled with unbound test saves behind a blocked load. An attempted final save with zero admission timeout is refused with `IO_BACKPRESSURE_TIMEOUT`, consumes its final token, preserves the terminal seal, and never changes canonical/ledger/owner bytes. Forced cleanup drains the canceled test tasks without publishing them.

The first R71 local run passed the new test and **11 inherited Windows suites**, including R70/R69, R68–R63, original dispatcher and three persistence forced-shutdown/checkpoint/uninterruptible-shutdown suites. The expanded queue-backpressure R71 test also passed the isolated local Windows build/test run.

The independent GitHub Linux/Windows workflow additionally exercises the R55–R71 trade, player-generation, paired-journal, recovery and original LocalLab trade-flow regressions.

## Limitations / deployment blockers

- **OFFLINE ONLY.** No real LocalSession or world bootstrap installs R71; the live persistence worker continues its ordinary repository path. This is not an authenticated login/session authorization design.
- **Not durable final-seal journaling.** R71's checkpoint seal and ticket maps exist in one JVM, not on disk. Restart may discard the seal before any final-save record is published. A correct production solution requires durable, authenticated terminal-session intent and reconciliation of partial commit states.
- **No universal in-flight cancellation guarantee.** Shutdown discards *queued* work and retires its test ticket. If an I/O worker has already entered an uninterruptible actual repository write, a later timeout or rejected future does not prove filesystem rollback. R71's OS lock and seal only constrain cooperating check-before-write paths. In-flight late return after forced close needs a separate proof.
- **Not one atomic multi-file commit.** R63 generation and R64 owner are separate individually atomic replacements after the account file; interruption between them remains fail-closed, with no automatic repair or hardware power-loss guarantee.
- **Checkpoint before final seal is allowed to linearize first.** In that case the final snapshot is refused as a stale generation; R71 does not resolve or automatically retry. No guarantee of successful logout completion under all crash/queue schedules.
- **No global operating-system authority.** R60 only fences cooperative writers using its root. Direct PlayerRepository writes, alternate processes or real session callbacks outside the gate bypass R71.
- **No safe trade quarantine retirement.** R57/R59 indexed pending paired intents still block their participants. Neither R71 nor the earlier R62 offline completion certificate authorizes live login.
- **No credentials, production owner issuance, durable queue, or exactly-once gameplay guarantee.** Tokens remain test fixtures.

## R72 focus

Audit forced shutdown of an **in-flight, uninterruptible repository commit** after a worker has already passed owner admission. Reproduce cancellation/terminal-future races where the file write continues after `close()` returns, then design a verifiable commit acknowledgment or durable write-intent protocol (offline first) instead of assuming that canceling a Java future revokes an operating-system file write. Also audit replay of an interrupted final-save account/ledger/owner publication.

**R71 DRAFT / STACKED ON R70 / UNMERGED.**
