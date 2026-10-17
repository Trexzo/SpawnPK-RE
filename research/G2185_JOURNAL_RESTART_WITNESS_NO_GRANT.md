# G21.85 — G21.83 PREPARED journal in guarded restart continuity (NO GRANT)

Parent exact certified G21.84 commit \`b866512067faa39c53e71d9317ca8a96196a1511\`, [Actions #38044420946](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38044420946) SUCCESS, 384 unique focused Java11. [Issue #2349](https://github.com/Trexzo/SpawnPK-RE/issues/2349).

## Integration

The G21.72–78 \`FilePlayerRepository.captureRestartContinuityTokenReadOnly\` forensic census now covers **six filesystem objects**: the pinned account file, the four existing G21.32/47/48 negative sidecar paths, and the G21.83 opt-in PREPARED idempotency journal. The account/JVM+OS cooperating publication lock, two complete witness passes, NOFOLLOW identity, ctime where supported, directory ancestor identity and read-only semantics remain the same. The G21.83 journal gets a separate 1024-byte hard cap vs 4096 bytes for negative markers and 64 MiB for account snapshots; an oversized/symlink journal rejects the witness even if its account bytes look fine.

The raw journal **bytes and physical inode identity** are observed in the same witness scope as the account and negative markers. Its validated semantic status is added to the hash input by calling a lock-free \`inspectInsidePublicationLock\` while the outer forensic lock is already held; this refactors the standalone G21.83 inspector to share exactly the same implementation without any nested file locking. This is not a COMMIT: \`PREPARED_MATCH_NO_REPLAY\` and \`TERMINAL_MATCH_NO_COMMIT\` remain non-authorizing. Existing negative sidecar precedence remains untouched.

## Versioning

**Portable witness version changes from G2172 to G2185** because the underlying file set and semantics changed. Old G2172 tokens must be rejected as incompatible, not silently compared as if they included journal content. The saved \`<version>|<account>|<classification>|<sha256>\` shape remains the same. No additional write, migration, automatic cleanup or replay.

## File-backed integration regression

\`G2185MailboxJournalRestartWitnessIntegrationTest\` checks independent repositories and actual disk mutations: journal absent stable, journal publication changes continuity, coherent terminal changes continuity and remains World-quarantined, journal byte corruption changes continuity and vetoes World loading, deletion changes continuity, >1024 B journal rejected at bounded preflight, journal symlink rejected, existing G21.47 marker takes priority, and a **same-byte, new-object atomic journal replacement** injected between the two witness passes is rejected by the G21.74 object census. A new stable witness is obtainable after replacement because source content did not change, but it never grants authority. Checks no live inventory/UNCLAIMED mutation and no temp/JVM lease leak.

No changes to the strict writer, reward handler, packet protocol, client ACK, positive COMMIT, replay, or WorldPlayer state mutation. G21.84 negative-only session journal admission remains as before. This forensic token is still unauthenticated and is NOT a signed exactly-once transaction ledger, durability proof, restart hydration credential or ability to defeat arbitrary uncooperative writes. Frozen R25 PR #1847 remains unchanged.

Focused manifest 384 → **385** unique Java 11 regressions, standalone \`g2185MailboxJournalRestartWitnessRegression\`; stacked Draft PR, exact-head full hosted CI required.
