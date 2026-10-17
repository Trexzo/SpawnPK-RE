# Chat 2 — G2029–2032: live journal disk integrity before every append

## Baseline and authority
Exact parent: [cumulative PR #2925](https://github.com/Trexzo/SpawnPK-RE/pull/2925), commit `276c7e490387c80d179347b15cfe551f612de286`, hosted **833/833** success including existing G2025–28 real NPC disk-journal tests, Java 11 bytecode and full Gradle. All new work remains separately UNWIRED, process-local CUSTOM_LOCALLAB observation-only; no original SpawnPK policy, reward delivery, consumer mutation, durable ACK, or restart-safe replay.

## Source issue and fix
Prior `LocalLabExperimentalDiskAttemptJournal` validated disk files strictly only at `open()`. While a journal remained open, an unrelated external writer could alter an event record, create an unknown orphan temp, add hardlink aliases, or atomically substitute an old record's inode. A subsequent `reserve` trusted cached entries and could overwrite those externally changed bytes with a new valid record without surfacing ambiguity.

Now `reserve` re-scans **all on-disk records under its cooperative lock** before every append, comparing complete canonical parsed record contents, exact UUID set/count, and original observed `fileKey` values. The directory, original lock-file identity, event and lock single-link count (`unix:nlink == 1`) and symlink/no-follow boundary are checked. Each record is read through a bounded, NOFOLLOW FileChannel; unknown temps and malformed/damaged records fail closed. Snapshot and disk storage remain non-ACKing, non-granting. A successful atomic replacement updates the in-process file key, so later checks distinguish expected self-replacement from unexplained outside replacement.

### Platform and race limitations
This security-conservative prototype explicitly fails closed on a filesystem/provider without `unix:nlink` or usable `fileKey`: **Windows runtime portability is not established**. Rescans plus a cooperative directory lock are NOT a hostile multi-process atomic transaction: external changes can still race between scan and move, and directory fsync/cross-process epochs are not proven. It does not make replay safe or guarantee exactly-once credits.

## Focused real-runtime regressions
- **G2029** actual canonical settled NPC, then same-inode external tamper to record checksum while journal is still open. New second-stage reservation denied, tampered bytes preserved, reopening rejects corruption.
- **G2030** actual settled NPC, then unknown partial temp appears while process stays open. Reservation denied and temp retained for operator investigation. Test fixture removes its own injected temp and verifies legitimate distinct stage still reserves, never re-settling the drop.
- **G2031** real settled event, external hardlink alias to record while open and after close, and alias to lock file at reopen. Both refuse; old reservation remains after test-only cleanup.
- **G2032** 12 threads race to reserve same consumer of one genuine settled NPC; exactly one succeeds, 11 duplicates denied. Byte-identical new inode is swapped under the open journal and any further stage reservation must fail. A read-only reopen still shows original single claim, never a grant ACK.

Four new classes, individual Gradle JavaExec tasks and new critical retention group. Focused manifest **833→837 unique**.

## Certification requirements
Hosted exact-head **837/837**, G2029–32 markers, Java 11 major 55 and full Gradle. Draft branch only; all account, actual drop settlement, reward and default gameplay code unchanged. #2587 durable authority/grant+ACK remains blocked; do not merge into main without independent review.