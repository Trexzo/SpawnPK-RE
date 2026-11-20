# R85 — disposable combat record: OS file lock and stale-generation fence

**DRAFT / STACKED ON R84 PR #3129 / OFFLINE ONLY / UNMERGED**

## Baseline and retained negative control

R84 demonstrated a concrete lost-update bug in an **isolated single-file
model**: two writers holding the same nominal owner epoch can each
atomically replace a snapshot, and the second can erase the first
consumer's completed checkpoint. An atomic rename is NOT a
compare-and-swap, and merely storing an epoch in the file is not a
cross-process lock.

This R85 branch **does not change `R84OfflineAtomicCombatRecord`**.
Its unsafe direct-writer negative control remains part of CI, so
the limitation is still visible.

## New cooperating-writer admission

`R85OfflineFencedCombatRecord` wraps the R84 disposable record. It
uses a persistent per-fixture lock-file path
`r85-offline-writer.lock`, opened with Java NIO `FileChannel`
and `tryLock()`. It deliberately **never deletes the lock file**:
deleting and recreating a lock inode while another process still
holds the first could split lock domains.

The actual OS lock is held across **all** of the following:

1. Re-open and validate the current canonical R84 record and owner
   epoch. Refuse malformed, missing or wrong-epoch canonical files.
2. Compare the latest exact bytes to the caller's recorded snapshot.
   Compare an additional logical generation inferred from R84's
   intentionally monotone *single event* fixture:
   generation 0 = before source commit; 1 = committed HP/event pair;
   each of the three independent consumer checkpoints advances it
   once (maximum 4).
3. Reject a stale writer **before any mutation**. The writer handle
   becomes invalid and requires a new, explicit open.
4. Run R84's actual atomic replacement under the same lock, with no
   fallback for filesystems that cannot provide `ATOMIC_MOVE`.
5. Re-open and validate the canonical record, check the generation
   delta (0 for an idempotent no-op, 1 for a new mutation), and only
   then update the writer handle's expected canonical snapshot.

An OS lock held by another JVM or overlapping lock in the current
JVM fails immediately with `R85_LOCK_DENIED`. A refusal before lock
acquisition does not poison a valid writer; it can retry after the
lock is released because no mutation occurred.

**Any exception during the locked operation poisons the writer**:
the file might already have been replaced before an acknowledgement
failed. Further writes on that handle are forbidden until an explicit
open/reconciliation. The wrapper does not replay any actual combat
or projection callback.

## Regression tests

`R85OfflineFencedCombatRecordTest` exercises:

- Two independently opened, same-epoch writer handles. Writer A
  commits lethal HP/event identities. Stale writer B cannot commit
  the same event with outdated source bytes.
- Both handles reopened at identical producer generation; A commits
  the objective checkpoint. B's stale match write is refused with
  **no lost objective checkpoint**. A subsequently becomes stale
  when a newly reopened handle commits match. The third consumer
  then commits Collection Log. All three values/checkpoints survive
  reopening. A repeated completed consumer remains a no-op.
- **Real OS locking in a second Java process**, using the JDK
  executable from the test JVM, temporary release/ready markers and
  a bounded wait. While the child JVM holds the lock, the parent
  fails closed and the canonical bytes remain identical. Following
  release the parent can commit normally.
- Overlapping lock contention in the same JVM, followed by a
  successful commit after release.
- Both R84 injected fault boundaries (before atomic move and after
  atomic move before acknowledgement) for a source commit and an
  objective checkpoint, checking poisoned handles and correct
  canonical state after deliberate reopening.
- Wrong epoch, syntactically corrupted canonical data and refusal
  to proceed without validating the restored bytes.
- Inherited R84 unsafe-writer negative control, all R83–R63
  regressions, full Linux server and Windows atomic-persistence CI.

All fixture files are created beneath a fresh temporary directory;
the second JVM is an isolated Java test process, **not a game server**.

## Safety and remaining limitations

This is an **opt-in cooperative fixture wrapper**. A caller using
R84's raw write API (or modifying the fixture file directly) can
bypass R85; the experiment does not provide a secure process
boundary against hostile writers. Only the wrapper's own cooperating
writers are serialized by this lock.

The derived generation is valid only for R84's finite monotone
single-lethal-event model. R85 has **no actual producer-side durable
sequence counter**, general-purpose commit revision, account owner
takeover, cross-account transaction or verified lease expiry.
The stored R84 epoch rejects a mismatch but does NOT enforce owner
transfer. There is no live `World`, `LocalSession`, actual account,
producer event ID, rewards or automatic recovery wiring.

The file contains SHA-256 checksums against accidental corruption,
not tamper-proof authentication. Atomic rename and file force are
not claimed to prove hard power-loss durability or directory-fsync
guarantees. The second-JVM test proves OS lock exclusion only while
the holder is running, not correctness after host power failure.
Replacing multiple real account/observer files would still require
a separate, coordinated transaction protocol.

**R85 DRAFT / UNMERGED / NOT DEPLOYED / NO LIVE CHANGES.**
