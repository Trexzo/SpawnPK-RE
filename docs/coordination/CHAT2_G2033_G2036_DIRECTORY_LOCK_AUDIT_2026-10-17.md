# Chat 2 G2033–2036 — disk journal directory/lock identity and audited read

**Exact source parent:** [Chat1 review snapshot PR #2930](https://github.com/Trexzo/SpawnPK-RE/pull/2930), commit `cfd3e725711afc53b7940e6212eb7d3ae7499b84`, main-aligned 841 focused classes. That snapshot is an alternative to #2929 and must not be merged alongside it. The substantive G2029–32 prior combined source [#2928](https://github.com/Trexzo/SpawnPK-RE/pull/2928) has already passed its own 841/841 Java11/full Gradle; #2930 exact-head verification must be separately checked.

## Problem
A `FileChannel` lock protects a FILE INODE, not a directory name. An external process can move or rename the directory and create a new directory at the same path, or atomically replace the `.journal.lock` filename with an unheld new inode while the old owner retains a valid channel lock. Journal inspection must not silently confuse the path with the originally opened directory. Separately, ordinary `snapshot()` is an in-memory view, not audited on-disk evidence. Valid SHA256 file checksums are not authentication; a writer who can rewrite bytes can recompute them.

## Production changes
- Pin the NOFOLLOW, non-null `BasicFileAttributes.fileKey` of the original journal directory on `open()` and reverify exact directory key on each later `scanDirectory()` / reservation. Root directory swaps now fail closed with `DISK_ATTEMPT_DIRECTORY_IDENTITY_CHANGED`.
- Existing lock-inode/fileKey checks now defend both reservation and the new explicitly audited inspection method `auditedSnapshot()`, which rescans canonical checksummed event data, nlink, live inodes, path/lock identity and pending unknowns before returning immutable copies.
- After a successful atomic file move, read back and parse the published exact event payload as an additional postmove sanity check before updating in-memory reservations; mismatches become `DISK_ATTEMPT_POSTMOVE_UNCERTAIN`. Still not a durable journal ACK.

## Real canonical NPC regressions
- **G2033** move original directory inode while journal is held open; make a fresh same-name directory; both audited read and new reservation refuse; previous event remains in relocated directory.
- **G2034** atomically substitute a new empty lock inode at the same `.journal.lock` path while the old lock remains held; audited read and reservation refuse.
- **G2035** real death, checked snapshot, inject unknown partial temp while open, fail audited read; remove test-injected artifact only; check two valid distinct stage reservations and snapshot immutability.
- **G2036** malicious/synthetic in-place valid rechecksummed G1→G4 event rewrite with same byte count, original modified time restored; explicit audit rejects `DISK_ATTEMPT_DISK_STATE_DIVERGED`, proving checksum alone is unauthenticated.

New manifest **841→845 unique** with four JavaExec tasks and critical retention group.

## Important NONCLAIMS
All modules are **experimental UNWIRED NON-GRANTING**. The local fileKey and `System.identityHashCode`-based fingerprint are NOT globally authoritative. An outside writer can still replace a lock path and launch a new owner: pinning the old journal does not create a cross-process epoch. SHA256 detects accidental corruption but is not a signature/MAC. No hostile TOCTOU exclusion, directory-fsync, independent JVM restart replay, original SpawnPK kill attribution, atomic per-consumer player-credit+ACK, or Windows portability is established. #2587 remains OPEN. Default gameplay and Chat1 account code unchanged.

## Hosted verification contract
Do not call this exact HEAD certified until **845/845**, G2033–36 PASS markers, G21367–70 retained, Java11 major55 and full Gradle. Draft, unmerged; no main mutation.
