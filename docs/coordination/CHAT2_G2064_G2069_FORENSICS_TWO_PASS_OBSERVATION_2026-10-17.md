# Chat 2 G2064–2069 — detect visible in-inspection journal mutations with two read-only scans

Parent [PR #2951](https://github.com/Trexzo/SpawnPK-RE/pull/2951) corrected exact `946ee1285a206bb889d4cb95911ff105471cda9f` independently verified **878/878** Linux/full Gradle+Java11 and targeted Windows source preflight. New source is only read-only `LocalLabExperimentalDiskJournalForensics`, six actual filesystem regression tests, Gradle and this document. No account, World, real NPC drop/grant, Windows action or original client changes.

## Source bug addressed
Former inspector scanned existing directory only once and could report READABLE_UNAUTHENTICATED_HISTORY while an external actor replaced an already visited event inode or lock file between directory traversal and the returned result. Some outside changes also preserve checksums and serialized byte count. This could look like stable historical evidence despite in-progress external mutation.

New `inspectExisting` performs TWO sequential bounded full NOFOLLOW DirectoryStream passes. It retains a private per-filename witness of the observed fileKey and exact bounded on-disk bytes (including `.journal.lock`) plus the directory fileKey; all witnesses must agree exactly across passes and counts must match. If new/unsafe/corrupt entries appear, files change in bytes or inode, or the directory/parent path changes between passes, it returns `BLOCKED_UNSTABLE_OBSERVATION` / `DISK_FORENSICS_UNSTABLE_OBSERVATION`, which operator CLI already maps to exit3. A test-only after-first-pass cutpoint permits deterministic real NIO fault injection without production callbacks. Existing parser semantics, orphan/corrupt direct diagnostics, and 0/2/3/64 CLI codes otherwise preserved.

## Genuine tests
- G2064 one canonically ground-settled NPC record survives unchanged two-pass scan. Every report explicitly denies original server event identity, durable consumer grant ACK, safe replay and global owner epoch.
- G2065 byte-for-byte same event moved onto NEW inode between scans -> BLOCKED_UNSTABLE_OBSERVATION.
- G2066 in-place changed G1 to G4 with valid recomputed checksum, equal byte length and restored original mtime -> BLOCKED_UNSTABLE_OBSERVATION.
- G2067 orphan `.tmp` introduced after first scan -> unstable; next standalone direct scan accurately classifies unresolved pending. No auto cleanup.
- G2068 `.journal.lock` filename inode atomically replaced with byte-identical file between scans -> unstable. No implication of cross-JVM ownership.
- G2069 parent moved and replaced by symlink into SAME underlying journal directory inode between scans -> unstable, previous event remains.

Focused manifest 878->884 unique. Six dedicated JavaExec regression gates and critical-retention family. Existing G2052–63 and G21374–76 gates unchanged. Exact-head required hosted 884/884, G2064–69 markers, Java11 major55/full Gradle and Windows source-only smoke; leave draft/unmerged.

## Nonclaims
Two sequential unlocked reads are only a best-effort **change detector**. A hostile process may mutate and restore records within a pass or between passes, and reading bytes/fileKey is not transactional; no guaranteed race exclusion, signed original SpawnPK event ID, durable consumer credit+ACK, cross-JVM lease epoch, directory-fsynced power-loss stability or safe reward replay. `READABLE_UNAUTHENTICATED_HISTORY` still is NOT a grant. Unsupported Windows `unix:nlink` providers still fail closed and original 179/GUI remains unverified. #2587 remains OPEN.
