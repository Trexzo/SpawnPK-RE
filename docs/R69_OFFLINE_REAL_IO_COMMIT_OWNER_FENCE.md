# R69 — Offline real persistence-worker I/O commit ownership fence

## Goal and observed production gap

R68 placed an opt-in test-only R64/R66 owner check around the **actual typed request dispatcher**. That did not protect the later filesystem boundary.

`WorldPlayerPersistence` on the production path captures a `PlayerSnapshot` on the World actor thread while `world.players().owns(player, generation)` is true, then enqueues work on a separate dedicated executor. The real `write(...)` method eventually calls `repository.save(snapshot)`. The existing generation check at capture/enqueue does **not** revalidate a replaced account owner at the moment of I/O commit. The executor can be delayed past disconnect and an independent new session's account takeover.

**R69 fixes this race in a fully isolated, opt-in test harness, not in the live server.** LocalSession and World default construction never install R69's test hook. No running game server, socket or account file is touched.

## Actual writer admission seam

`WorldPlayerPersistence` now has a package-private, **test-only** `OfflineIoWriteAdmission` and one-shot explicit installation `installOfflineIoWriteAdmissionForTest`. Its `write()` method continues to execute exactly `repository.save(snapshot)` when **no hook is present**, preserving production behavior, metrics, queue backpressure and shutdown paths.

When the hook is explicitly installed by a disposable test, the actual executor's I/O worker calls `persistAtCommit(sequence, snapshot, repository, checkpoint)`. The gate is responsible for performing the only authorized repository write or throwing before any write. Exceptions go through the worker's existing `future.completeExceptionally` and failed-task accounting. The writer is not silently retried or accepted on failure.

`R69OfflineIoCommitGate` requires an explicit binding from a **specific immutable `CapturedSave.ticket.sequence`** to the R64 session token that existed when that save was captured. A username or snapshot is not authority. It removes this one-shot binding when the worker reaches commit, checks that the snapshot belongs to that token and then acquires R60's **real cross-JVM exclusive OS FileLock**.

Under that one held lock, the existing R64 current-owner checks verify UUID, epoch, ACTIVE phase, R63 account generation and exact snapshot hash, and the current R59 journal revision/pending-transaction state. Only if all are current does R63 use the **actual `WorldPlayerPersistence` repository object** for the one canonical write. It reloads and verifies the post-write snapshot, increments R63's generation ledger, and advances the bound R64 owner record. The lock is not released between authority verification and these writes.

R63 and R64 gained package-private overloads taking an explicit `PlayerRepository` to reuse the real worker's repository in R69. Their original externally called offline methods still use their existing `FilePlayerRepository` and retain behavior.

### Checkpoint limitation

World checkpoint saves use a separate coalescing/drain path and normally do **not** carry a binding for a particular R64 session token. R69 intentionally refuses **all unbound I/O tasks**, including such checkpoints, while this isolated test hook is active. It does not manufacture an owner token from the latest username, World registry or R64 owner record.

A future production protocol must bind the captured checkpoint to authenticated session ownership **at World capture time**, carry that frozen authority through coalescing and then revalidate it at the exact I/O commit. As currently implemented, R69 is not suitable for enabling on a live server; all automatic checkpoint saves would be refused without the appropriate binding work.

## Connected Windows acceptance — November 15, 2026

- Isolated JDK21 Gradle `jar testClasses verifyJava11Bytecode --offline --no-daemon` **PASS**. Java11 major-55 classfile scan passed, plus 12 directly executed Windows regression suites R69/R68/R67/R66/R65/R64/R63/R62/R61/R60/R17 and the original normal dispatcher test.
- `R69_REAL_IO_COMMIT_OWNERSHIP_FENCE_PASS`: an actual `World.isolatedForTest(..., repository)` has a **real `WorldPlayerPersistence` single-thread bounded I/O executor** backed by a counting wrapper around a test-owned `FilePlayerRepository`.
- Session A's modified profile is captured on its World thread with `captureDeferredSave`, explicitly bound to the A owner token and submitted through **real `submitCapturedWithBackpressure`**.
- A deterministic hook pauses the real I/O worker just before the commit gate. A separate child JVM calls the existing R64 takeover protocol and publishes session B's epoch. On releasing the worker, it fails **specifically with stale-owner denial**, before the counting repository's `save` method is called even once.
- Canonical account bytes, generation-ledger bytes and owner-record bytes are preserved after that old-save failure. There is no implicit token promotion or snapshot recovery.
- The World registry actor is explicitly replaced with session B. A forced exception from the actual repository before its canonical write preserves the original ledger and owner generation, while settling the SaveTicket exceptionally via the worker's normal failure path.
- A separately captured, correctly bound session-B save then succeeds through the **actual I/O worker** and the same counting `FilePlayerRepository`, advancing R63/R64 generation to 2. A later fresh valid retry advances it to generation 3.
- An unbound queued manual save is refused before the repository. A real due autosave checkpoint is captured by `checkpointDue(100)` and reaches the same I/O worker, where it is refused as unbound, with the existing failed-write counter advancing; no checkpoint bytes are published.
- A separate JVM holds the real R60 coordinator lock while an otherwise valid B save reaches the writer: it fails on OS-lock contention before the repository call. A **new** authorized captured ticket succeeds after the lock is released; the already-failed ticket is not reused.

**None of those tests proves a real account authentication system or physical power-loss durability.**

## Technical boundaries

1. **No live integration or security claim.** The hook is never installed from `LocalSession`, `World` construction, production login, TradeService or WorldPlayerPersistence defaults. Existing live I/O writer remains unchanged when no test gate is installed.
2. **The test binding is not authentication.** `bindForTest(captured, owner)` uses a token supplied by the test. A real integration must bind the token to an authenticated login, the actual World actor and every asynchronous callback. It must ensure user code cannot attach somebody else's owner record to a snapshot.
3. **Cooperative OS lock only.** R60 coordinates only consumers that opt into its root and lock. Any direct `PlayerRepository.save` from another path/JVM remains outside this fence.
4. **Multi-file commit is not atomic.** Canonical account, R63 generation ledger and R64 owner record are separate single-file atomic replacements. If a process stops between them, the persisted evidence no longer matches and inspection refuses further work; R69 does not supply automatic recovery. Actual disk power-loss behavior and parent-directory fsync remain unproven.
5. **Risk of I/O queue latency.** Acquiring the R60 exclusive OS lock on the actual I/O thread can fail due to contention; this test hook deliberately fails closed and does not automatically retry. A production design needs explicit retry/reconciliation semantics without blocking World ticks.
6. **No durable checkpoint/session binding yet.** In the isolated hook's mode, unbound autosave checkpoints are refused. Existing checkpoint coalescing and backpressure semantics remain unchanged when the hook is absent; CI includes the three pre-existing persistence shutdown tests for regression coverage.
7. **Not pair-complete or login-authorized.** The R56 split-account trade risk is still unresolved in real account persistence. R57/R59 pending manifests remain quarantined even after R61/R62 offline replay/certificate. R69 cannot retire those records or permit login.

## R70 focus

Build a **test-only checkpoint capture-token propagation** from the World-thread checkpoint snapshot to the eventual coalesced I/O commit, with explicit per-actor owner generation and R64 token. Test that when checkpoint A is replaced by B, the checkpoint worker uses only B's authority and snapshot; never bind an old A snapshot to a new B token implicitly. Add shutdown/backpressure failure probes to verify tokens cannot leak/replay. Keep opt-in and away from production until the server's authenticated login/session ownership and trade persistence paths are fully audited.

**R69 DRAFT / STACKED ON R68 / UNMERGED.**
