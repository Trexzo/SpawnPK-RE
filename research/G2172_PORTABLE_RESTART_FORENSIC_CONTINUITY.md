# G21.72 — portable exact restart-recovery continuity witness (NO GRANT)

**Certified parent:** G21.71 `6233b95537864fce9882381aa093882eeb11cc36`, hosted [Actions #37967142710](https://github.com/Trexzo/SpawnPK-RE/actions/runs/37967142710) SUCCESS, 371 Java11 focused tests. [Issue #2320](https://github.com/Trexzo/SpawnPK-RE/issues/2320).

## Risk resolved

The G21.71 inspector reports **classification**, not cross-process byte identity. A valid G21.64 terminal record overwritten by another valid terminal record retains `COHERENT_TERMINAL_QUARANTINE`. An existing G21.48 review sidecar modified in place still retains `STRANDED_WRITE_INTENT_MARKER`. Neither unchanged category nor an old in-memory receipt permits positive settlement.

G21.72 adds opt-in read-only `FilePlayerRepository.captureRestartContinuityTokenReadOnly(account)` and `compareRestartContinuityReadOnly(account,previousToken)`. The caller can retain the bounded ASCII witness `G2172|<account>|<recovery-state>|<sha256>` and compare it with a **new** process/repository using the **same exact account root**. No live World state, G21.27 reservation, strict publication receipt, or past World command is trusted or reconstructed.

The fingerprint covers account identity, normalized pinned account pathname, G21.71 recovery classification, exact raw account bytes (or absence) and **all four** negative marker paths and byte contents (or absence): G21.32 durable review, G21.49 legacy strict review, G21.47 strict uncertainty, G21.48 strict write-ahead intent. Under G21.39 cooperating JVM/OS account publication FileLock, the implementation reuses the exact pinned G21.71 NOFOLLOW, SHA-256, marker and classification checks; the new digest side enforces NOFOLLOW regular-file evidence, before/after object stability and read bounds (64 MiB account, 4 KiB per sidecar), then repeats its entire fingerprint observation and refuses divergent reads.

The comparator returns one of:
- `UNCHANGED_FORENSICS_NO_GRANT`: new instance sees the exact same forensic evidence, not a positive commit.
- `CHANGED_FORENSICS_QUARANTINE`: account bytes, marker content/presence, path or classification differs.

Malformed, unknown-version and foreign-account witnesses are rejected; nonregular or oversized marker objects, unstable path/object/bytes, unreadable file and unsupported filesystem behavior fail closed. The token is a **plain SHA-256 comparison**, NOT authenticated evidence and NOT tamper-proof provenance: an adversary who can forge both the token and disk can create a false match. Even a genuine match is a point-in-time observation only, not proof of physical crash durability or protection from uncooperative writers.

**No result ever grants, admits, commits, replays, clears a marker, releases a reservation, rolls back, credits inventory, marks Mailbox CLAIMED or ACKs the client.** Native widget32181 stays disabled; restart admission remains G21.31/G21.64's separate quarantine policy.

## New regression

`G2172MailboxRestartContinuityWitnessIntegrationTest` tests missing-account continuity, missing→legacy change, fresh-repository equality on legacy and exact terminal records, raw same-length/mtime-preserving account change with the *same coherent-terminal category*, unrelated account isolation, G21.48 marker body mutation with unchanged marker presence/mtime, a later G21.32 marker outranking an existing intent, marker symlink refusal, identical account file copied to a different root, malformed and cross-account witness veto, no forensic writes, unchanged live owners and restart quarantine. Java11 train **371→372**, hosted exact-head full Gradle build required for certification. PR Draft/unmerged, frozen R25 PR #1847 untouched.
