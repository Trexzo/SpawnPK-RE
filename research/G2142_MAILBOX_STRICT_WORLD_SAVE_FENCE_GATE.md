# G21.42 — strict PREPARED World saves under the shared negative Mailbox review-fence publication lock

**Certified parent:** G21.41 exact commit `dd9fe641e6a007bde1e085effb4a9dc853b260c9`, hosted [workflow 37838792423](https://github.com/Trexzo/SpawnPK-RE/actions/runs/37838792423) SUCCESS / 340 focused Java11, 17/17 G21.41 checks. Frozen R25 promotion PR #1847 remains untouched.

## Verified write-side bypass

G21.34, G21.38–G21.40 protect review-marker publication with exclusive no-clobber hard links and account-specific JVM+cross-process file locks. G21.36 applies that protection to ordinary, checkpoint and final `FilePlayerRepository` World saves. However, G21.23's **separate strict PREPARED writer** remained outside the coordinator: the existing `WorldPlayerPersistence.PreparedStrictBarrierTask.run()` called `writer.saveStrict(snapshot)` directly. That strict, fsynced file replacement could therefore run across a newly armed G21.32 review marker and overwrite account bytes while the marker already demanded manual review.

## G21.42 bounded implementation

The real `WorldPlayerPersistence.PreparedStrictBarrierTask` now selects `StrictDurablePlayerSnapshotWriter.saveStrictForWorld(snapshot, expectedRepositoryFile)` when its repository is the concrete **file-backed `FilePlayerRepository`**. The expected account file is obtained from the actual repository's canonical username/path resolver; the strict writer **must** resolve that exact same normalized file path, otherwise it refuses before touching files. Historical `saveStrict(snapshot)` and strict barrier use with custom/non-file repository adapters remain unchanged and are explicitly *outside* the new file-backed guarantee.

The guarded strict World path:
1. Requires G21.31's exact `VALID_PREPARED_UNCLAIMED` admission on the immutable snapshot, never hypothetical inventory+CLAIMED output.
2. Checks persistent G21.32 review-marker presence **before** creating a strict temp file; existing marker or unreadable marker metadata denies the strict operation without success receipt.
3. Serializes the full account file to a unique fsynced G21.23 temp just as before. At final publication it obtains the **same G21.38/39/40 account-local publication lock** as the negative marker publisher, checks sidecar presence **again inside the lock**, then performs the `ATOMIC_MOVE` account replacement and parent-directory `force(true)` before releasing the lock.
4. Preserves G21.23's **no ATOMIC_MOVE fallback** and no directory-force downgrade. If a failure occurs **after replacement**, a `StrictDurablePlayerSnapshotWriter.UnconfirmedCommitException` is raised; the file may already have been replaced, and **no rollback/positive grant is claimed**. Failed pre-publication outcomes clean unpublished temporary files.
5. Preserves the old strict `Receipt` only on confirmed return; any G21.32 marker appearing before publication fails closed. Marker publication immediately *after* a completed strict save remains a negative review obligation, never item settlement.

No new threads or World tick filesystem probes were introduced. Existing autosave paths retain the earlier G21.36 guard. A strict writer supplied for a different file can no longer write that file on the actual concrete file-backed World barrier path.

## Deterministic integration

`G2142MailboxStrictWorldSaveFenceIntegrationTest` uses the real file-backed `WorldPlayerPersistence.submitPreparedStrictBarrier` and independent G21.34/G21.41 review-marker publishers:
- Already-published G21.41 verified marker: strict barrier fails and saved account bytes remain unchanged.
- Strict writer pauses at G21.23 `BEFORE_ATOMIC_REPLACE` **outside** the publication lock; a negative marker publishes first; resumed strict task fails and preserves the original file.
- Strict writer pauses at G21.23 `BEFORE_DIRECTORY_FORCE` **inside** the publication lock; independent marker publisher reaches its final stage but must wait; releasing the strict writer returns the G21.23 receipt, then the marker publishes and still blocks fresh login.
- Unmarked other account strict save succeeds, path mismatch fails, hypothetical CLAIMED account snapshot is refused, unpublished G21.23 temp files are removed, cross-account JVM publication leases are clean, no live item grant/Mailbox claim/marker release occurs.

Java11 focused manifest **340→341**, new Gradle task `g2142MailboxStrictWorldSaveFenceRegression`. Exact-head full hosted CI SUCCESS is mandatory before certification; existing 340 tests are retained.

## Remaining limits

This protects only cooperating file-backed **World persistence strict barriers**, ordinary World saves and negative sidecar publishers on the tested filesystem/provider. Direct standalone historical `saveStrict`, manual/forensic `FilePlayerRepository.save`, custom repositories and uncooperative external file writers are outside this specific lock contract. It is not an atomic multi-file transaction, hardware crash guarantee, positive exact-once reward settlement or original SpawnPK server behavioral authority. Native widget32181 stays NO_GRANT; no automatic release, rollback, replay or reward acknowledgement. Bank widget32178 excluded; frozen R25 #1847 untouched.
