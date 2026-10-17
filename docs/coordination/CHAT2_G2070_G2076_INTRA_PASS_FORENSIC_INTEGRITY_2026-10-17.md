# G2070–2076: within-pass read-only journal integrity and exact unsafe-file classification

Main-aligned parent [#2954](https://github.com/Trexzo/SpawnPK-RE/pull/2954) exact 189bd2d73e20c3723103f0aed99e2ad7b4b6f140 independently verified **884/884**, Java11 full Gradle and targeted Windows source preflight. Chat2 owns only unwired NON-GRANTING NPC journal diagnostic source. No account production, World, real NPC settlement, player rewards, Windows workflow or original client modifications.

## Concrete correctness gaps
The prior observer performed two independent directory passes, but **within each pass** it did not verify a file still had the same inode or bytes after first read. A concurrent uncooperative actor could replace or alter a file between its original read and the continuation of DirectoryStream iteration. That actor could also replace the directory pathname after a scanned file. Best-effort pass-to-pass equality detects some drift, but per-file bounded rechecks provide another independent fail-closed boundary. Also, an oversized `.tmp` or unrelated unknown filename was read through the 4096-byte event reader before its name was classified; this could misreport an unresolved orphan as malformed event data.

## Source change
`LocalLabExperimentalDiskJournalForensics.scanOnce()` classifies `.tmp` and unknown file names BEFORE reading bytes; no attempt to parse their payload or auto-remove. For known `.event` / `.journal.lock`, it pins the observed `fileKey`, reads <=4096 bytes, uses a strictly test-only after-first-file-read cutpoint when requested, and verifies NOFOLLOW ancestor/directory key, entry symlink, single-link count, fileKey and byte-for-byte bounded reread **within that same directory traversal**. Detected drift produces `BLOCKED_UNSTABLE_OBSERVATION` reason `DISK_FORENSICS_FILE_CHANGED_DURING_SCAN` and CLI exit 3. The end of each pass checks that the directory pathname still resolves to the original directory key. Then the existing second full pass compares the per-file witnesses and whole-directory identity. No source bytes/fingerprints appear in operator outputs.

## Seven deterministic genuine-NPC regression gates
- G2070: healthy actual ground-settled NPC, both known-file within-pass checks run, two-pass report remains read-only and CLI exit 0 still safe.
- G2071: event bytes externally altered after FIRST file read, BEFORE identity reread; no clean result or repair.
- G2072: byte-identical event moved onto new inode inside one pass; refused despite equal bytes.
- G2073: `.journal.lock` filename replaced with new same-content inode inside one pass; refused, no cross-JVM owner epoch claim.
- G2074: directory moved and original pathname recreated at the cutpoint; original event retained at relocated directory, refusal.
- G2075: 8192-byte orphan `.tmp` identified as BLOCKED_ORPHAN_PENDING, then 8192-byte unknown sidefile as BLOCKED_UNSAFE_PATH, without mislabeling as a corrupt V1 record.
- G2076: foreign hardlink added to event after initial bytes read; nlink reread refuses and does not grant a consumer.

Manifest 884→891 unique focused tests, seven dedicated JavaExec tasks and critical-retention family. The test-only hook is NOT accessible as a production callback or connected to gameplay.

## Limits and certification
Even read-twice within each of two scans is NOT a hostile-process transaction: a sufficiently timed actor may mutate-and-restore between observations, with no authenticated original SpawnPK event ID, durable global owner epoch, atomic credit+ACK, directory-fsynced crash recovery or safe restart reward replay (#2587). Windows unix:nlink unsupported remains fail-closed, original client v308 GUI/179 acceptance NOT RUN. Exact HEAD must show hosted Linux **891/891**, all G2070–76 and G21374–76 markers, Java11 bytecode major55/full Gradle, Windows targeted source-only gate. Draft/unmerged; main unchanged.
