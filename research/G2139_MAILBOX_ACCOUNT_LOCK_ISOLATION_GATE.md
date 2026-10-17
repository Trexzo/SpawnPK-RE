# G21.39 — account-local JVM publication locking

**Certified parent:** G21.38 exact `6667ff905b44d50fcbe533d018c6baee77e228be`, hosted [37833987794](https://github.com/Trexzo/SpawnPK-RE/actions/runs/37833987794) SUCCESS, focused **337** / Java 11. Frozen R25 promotion PR #1847 remains untouched.

## Measured defect in the G21.38 implementation

G21.38 brought the no-clobber G21.34 negative review marker and G21.36 guarded World account-file replacement under the SAME per-account sibling `FileChannel.lock()`. The original same-JVM `OverlappingFileLockException` mitigation was `synchronized(SAME_JVM_LOCK)` on one **global** static monitor. A slow writer for one account could thus hold the JVM monitor and stall other, unrelated accounts, even when the OS-level FileLock was scoped to the correct per-account path. The G21.38 regression only verified that unrelated accounts continued after the competing account lock was released, not that they could publish **concurrently**.

## G21.39 repair

`MailboxAccountPublicationCoordinator.withExclusivePublication` retains the exact **stable canonical sibling lock-file path**, the exact filesystem `FileChannel.lock()` contract, and the same file-backed cooperating writer call sites. It replaces the coarse JVM monitor with a synchronized `Map<Path,JvmLease>`, keyed by the complete normalized lock-file path. Each lease owns a private monitor and a reference count.

- `acquire(lockPath)` increments the ref count for both active holders and queued waiters **under the registry bookkeeping lock**, then releases that registry lock **before** waiting on the lease monitor or attempting filesystem operations.
- The account-specific monitor, not the global registry monitor, is held while obtaining and executing that account's FileLock-backed publication.
- `release(lockPath, lease)` decrements under the registry bookkeeping lock **after leaving** the account monitor, deleting the idle JVM-map entry only when references reach zero. This preserves monitor identity for already-queued writers and prevents an unbounded idle account-key cache.
- Errors thrown by the publication callback, FileLock acquisition or overlapping lock still execute the `finally` release. Existing review-marker no-clobber behavior and normal account-save marker veto remain unchanged.
- The lock file itself is **never automatically deleted** by the coordinator, since deleting an inode while another process retains an open lock could allow a second incompatible lock-file generation.

This is a concurrency and resource-lifecycle correction for **cooperating same-JVM account publications**. It does not change reward, Mailbox, inventory, restart-admission, or native packet semantics.

## Integration test

`G2139MailboxAccountLockIsolationIntegrationTest` uses a deterministic held publication for account A, a queued competing A publication, and an account B publication. The B future **must complete while account A remains held**; the competing A operation must not enter until its first holder releases, and its maximum observed active A count stays one. The test checks the same lease remains referenced while a contender queues; registry cleanup after contention, injected `IOException`, and 25 distinct account paths; and that an actual `WorldPlayerPersistence.captureAndSave` for an unrelated account completes **while** a G21.34 review marker is held at its internal publication phase for a different account. The marker subsequently publishes with unchanged G21.34 no-clobber identity, no live item credit, and restart-time account login still denied.

**335→336→337→338** prior progression remains additive: focused manifest now **338 Java11** and Gradle task `g2139MailboxAccountLockIsolationRegression`. Hosted exact-head CI is mandatory before certification.

## Explicit limits

- Different accounts can publish concurrently in the **same JVM**; shared worker scheduling still serializes work that is intentionally placed on one World persistence worker. The test's negative review marker publisher is a distinct Java thread and does not require additional I/O workers.
- This test does not spawn separate JVM processes. The preserved OS FileLock cross-process behavior relies on the provider contract, not a new multi-process acceptance fixture.
- Only cooperating FilePlayerRepository guarded World saves and MailboxDurableReviewFence publishers acquire this coordinator lock. Direct manual/forensic saves and uncooperative external processes remain outside protection.
- It does not provide multi-file transactional atomicity, power-loss protection, authentic success receipts, native C2S185 widget32181 grants, replay, automatic rollback or marker-release authority. Bank widget32178 excluded.
