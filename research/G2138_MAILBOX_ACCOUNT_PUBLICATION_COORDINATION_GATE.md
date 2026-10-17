# G21.38 — coordinated review-fence and World account-file publication

**Certified parent:** G21.37 exact head `da073dd8c2e5fa960262750ae85a23ba274424a1`, hosted [37831441912](https://github.com/Trexzo/SpawnPK-RE/actions/runs/37831441912) SUCCESS, 336 focused Java11. Frozen R25 promotion PR #1847 untouched.

## The verified design gap

The previously certified G21.34 negative review marker uses `Files.createLink(marker,temp)` for exclusive no-clobber publication; the G21.36 file-backed World save independently checks marker absence immediately before `Files.move(temp,account,ATOMIC_MOVE)`. Those individually fail closed when a marker is already visible but do **not** serialize the two operations. An independent marker can become visible after a save's final check but before its replacement, allowing the account file to be overwritten **after** its persistent review fence was armed.

G21.38 creates `MailboxAccountPublicationCoordinator` using a **stable per-account sibling lock file** `<account.properties>.g2138-mailbox-publication.lock`. It combines a JVM-local monitor (to prevent same-JVM Java `OverlappingFileLockException`) with `FileChannel.lock()` (advisory cross-process lock on a filesystem honoring Java file locks). Lock paths are derived from the **same canonical FilePlayerRepository account path**. The lock file is intentionally never removed in normal operation, because unlink-and-recreate can break mutual exclusion among concurrently open file handles.

Cooperating operation critical sections are narrow:
- G21.36's `FilePlayerRepository.saveForWorld(snapshot)` still validates the G21.31 PREPARED journal and performs an initial G21.32 negative marker preflight **before** temporary-file serialization. Its FINAL G21.32 marker check and the account-file `Files.move` are now performed together **under the account's exclusive publication lock**. A marker cannot publish through the cooperating G21.34 writer between those two operations.
- G21.34's `MailboxDurableReviewFence.arm` still validates the immutable PREPARED proposal, writes a unique same-directory temp, and forces its contents before publication. Its final **no-clobber hard-link publication** `Files.createLink`, temp-link deletion, and required parent-directory force now execute under that **same account lock**. It never downgrades to a nonexclusive rename/copy and never auto-removes a published review marker. The G21.38 extra fault phase within the critical section is test-only injection to deterministically exercise marker-first interleavings.

This is a **mutual-exclusion ordering guarantee only for cooperating G21.34/G21.36 implementations**, not a single atomic transaction spanning both account file and marker. If the save linearizes first, the later negative marker may become visible and still needs manual review; the arm primitive does **not** presently assert that account bytes match the original PREPARED proposal at publication. If the marker publishes first, the subsequent guarded save's inside-lock check rejects without replacing account bytes.

## Integration and focused verification

`G2138MailboxAccountPublicationCoordinatorIntegrationTest` uses a real file-backed World, distinct marker writer and file repository instances and blocking hooks **inside** the critical sections:
- **Save-first:** an actual World save passes the final check and pauses inside its exclusive lock. A marker writer reaches the publication boundary but cannot publish until the save releases. The save completes normally; the marker then publishes a negative-only record, and future login is fenced.
- **Marker-first:** a marker writer holds the same exclusive lock before creating its final hard link. A World save serializes its temp and reaches the final-check boundary but cannot replace while the marker publisher holds the lock. After marker publication, the saved account's checksum and bytes remain unchanged, and the queued World save fails with the G21.36 review-veto exception.
- Additional assertions: distinct account lock paths, existing 336 safety regressions intact, no temp-file leaks in ordinary outcomes, unrelated account can save, fresh-World login still fenced, and no live Mailbox CLAIMED or inventory credit.

New Gradle task `g2138MailboxAccountPublicationCoordinationRegression`; focused manifest **336→337 Java11**, exact-head hosted CI required.

## Explicitly NOT proven

File locks are advisory and only effective where supported by the underlying filesystem/provider. The test drives **separate writer instances in one JVM**, not two isolated operating-system processes: cross-JVM behavior relies on Java FileLock semantics but is not separately hosted-tested. An external/uncooperative writer, direct legacy/forensic `FilePlayerRepository.save`, or a non-file PlayerRepository can still change account bytes without acquiring this lock. The shared JVM monitor serializes accounts more broadly than necessary during the short final publication section. The coordinator does not confirm disk durability of ordinary legacy account writes, protect an already-ongoing session action with a cross-process gameplay lock, or implement durable positive item settlement.

**Native C2S185 widget32181 remains NO_GRANT; no replay, automatic rollback or review-marker release.** Bank widget32178 excluded; frozen R25 PR #1847 untouched.
