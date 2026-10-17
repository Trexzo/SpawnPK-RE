# G21.73 — bounded competing-process restart observation (NO GRANT)

**Parent:** exact hosted-certified G21.72 `3e9de3f6114266350874b02a558087ab2a369d46`, [GitHub Actions #37969602874](https://github.com/Trexzo/SpawnPK-RE/actions/runs/37969602874) SUCCESS, 372 focused Java11. Issue [#2322](https://github.com/Trexzo/SpawnPK-RE/issues/2322).

## Observed gap

G21.72 captures an accurate, portable and **non-authorizing** SHA-256 account/review-marker fingerprint while holding the G21.39 cooperating file publication lock. If a separate writer or process holds that lock indefinitely, the forensic caller can remain stuck waiting forever without producing an explicit failed observation. An old witness is never a grant authority, even if comparison succeeds.

## Implementation

G21.73 introduces `MailboxAccountPublicationCoordinator.withExclusivePublicationBounded(file, timeoutMillis, operation)`, for **read-only forensic observations only**. The existing standard `withExclusivePublication` remains blocking for World saves, strict terminal publication, negative review marker publication and session admission. Both now share a **fair, reference-counted, per-account ReentrantLock**, ensuring timed waiters and ordinary writers cannot bypass or replace each other's JVM lock lease. The stable sibling lock file is NEVER deleted/recreated.

The bounded path uses one monotonic acquisition budget for the per-account JVM lock and the existing OS-level advisory FileLock, using `FileChannel.tryLock` with 10ms-or-less backoff. Within the configured deadline, a cooperating JVM writer and an OS lock from another process must finish before the forensic read begins. Timeout returns `IOException G21.73 RECOVERY_PUBLICATION_BUSY_NO_GRANT`; interruption restores interrupt status and returns `IOException G21.73 RECOVERY_PUBLICATION_INTERRUPTED_NO_GRANT`. Both retire the JVM lease reference and release any held resources. No lock acquisition is interpreted as grant, replay, rollback, reservation release or login permission.

Only `FilePlayerRepository.captureRestartContinuityTokenReadOnly` uses the bounded method, with **1500ms acquisition budget**. `compareRestartContinuityReadOnly` naturally inherits it. The G21.72 repeat account/sidecar byte inspections and rechecks **remain inside** the lock after acquisition, preserving the exact prior SHA-256 comparison. **Important:** the 1500ms limit bounds acquisition ONLY; it is not a filesystem-I/O execution deadline. It does not cover malicious uncooperative writers, OS lock semantics on unsupported network filesystems, power-loss durability or a deliberate post-observation mutation.

A busy or interrupted inspector gets NO witness and NO positive authorization. The previous successful historical witness may still be retained externally but never becomes a positive Mailbox transaction COMMIT. Existing G21.71/31/64 terminal and negative-marker session admission quarantines remain authoritative.

## Real-file Java11 G21.73 regression

`G2173MailboxBoundedRecoveryContentionIntegrationTest` covers:
- unchanged portable witness across fresh repository instances before concurrent publication;
- same-account ordinary publication lock held while a bounded inspector times out within its acquisition budget;
- interrupting an in-JVM waiting observer, preserving interrupt flag, releasing reference-counted leases;
- independent account's strict file write and read-only witness unaffected by same-account contention;
- clean success after the standard JVM lock holder exits;
- a direct separate-channel OS advisory FileLock conflict, bounded failure and successful read after OS unlock;
- two fresh-repository observers queued behind a cooperating review-marker publisher, both returning CHANGED after the publisher releases its lock, with the permanent negative sidecar retained;
- restart session still refusing the marker-fenced terminal account;
- no live inventory credit or Mailbox CLAIMED mutation; no temp or JVM lock lease leaks.

**Focused manifest:** 372→373 Java11 tests; exact-head hosted full Gradle build required for certification. PR remains Draft/unmerged. Native C2S185/widget32181 NO_GRANT and frozen R25 PR #1847 remain untouched.
