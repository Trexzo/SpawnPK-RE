# G21.166 — detect advisory lock-file path replacement (NO AUTHORITY)

**Certified parent:** [G21.165 Draft PR #2510](https://github.com/Trexzo/SpawnPK-RE/pull/2510), `fcfeec47f30e5b59c315d4b8b697fb1afe07308d`, [Actions #38392216089](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38392216089) SUCCESS, Java11 465/465 focused/full Gradle. Issue #2511.

## Source-confirmed risk

The G21.39/G21.73 account-local advisory lock uses a **persistent sibling lock file**; its source explicitly warns that the lock leaf must not be unlinked/recreated during use. On POSIX a raw, uncooperative actor can unlink a file even while a different process holds an OS FileLock to its inode. A cooperating publisher opening the replacement path can acquire a *different* OS lock. G21.165 verifies seven recovery data leaves but intentionally does **not** include this lock leaf.

G21.166 adds a **negative-only observation** of the account's advisory lock **path**: after the existing G21.164 bounded lock acquisition, read NOFOLLOW regular-file metadata and require a non-null fileKey. After the G21.165 seven-file physical window and G21.162 unconditional cutover refusal, compare lock path fileKey, creationTime, size and lastModifiedTime in `finally`; any difference vetoes with `G21.166 CUTOVER_LOCK_LEAF_CHANGED_UNSAFE_NO_AUTHORITY`. Missing, symlink or nonregular lock path also refuses. No new lock or successful disposition is introduced.

**Important limit:** JVM FileChannel does not expose a portable identity proof tying its already-open descriptor to the path at the first metadata read. A race before that first sample, instantaneous ABA, a raw actor after final check or unrelated historical/future writers remain possible. This is an alarm on observed lock path mutation, **not a full lock protocol or independent RAW writer fence**.

## Actual filesystem regression

`G21166MailboxCutoverLockLeafIdentityIntegrationTest` opens a real cooperating account lock, then uses the existing test-only in-lock callback to call `Files.delete(lockLeaf)` and `Files.write(..., CREATE_NEW)` directly **without cooperating**. POSIX success must trigger the new checked lock-path identity veto, preserve the replacement bytes, keep the original account intact and release JVM locks. Some Windows filesystems prohibit deleting an open locked file; that outcome must be reported separately, and original negative cutover remains enforced. A second clean denial after the test proves the optional code never produces authority.

**Acceptance:** new Java 11 test, focused manifest 466/466, Java 11 major55, full hosted Gradle on exact PR SHA. Keep PR Draft/unmerged until verified.

**Unchanged restrictions:** no independent signer custody/authenticated historical provenance, complete raw/default/legacy/future writer exclusion, atomic crash/power-loss epoch, original writer/scanner/World caller changes or cleanup, rollover, pruning, unlink, grant, replay, session, release/ACK authorization. Frozen R25 #1847, G21.87 and G21.88 untouched.
