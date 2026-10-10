# G21.86 — receipt-guarded write-once disk COMMIT record (NO GRANT)

Parent exact certified G21.85 commit \`789dee10a4bad94d9e127593254914cfdfc113f0\`, [Actions #38045637468](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38045637468) SUCCESS (385 Java11 focused); [issue #2351](https://github.com/Trexzo/SpawnPK-RE/issues/2351).

## Actual disk protocol step

G21.86 implements an opt-in write-once, versioned, checksum-validated **disk COMMIT record** under \`MailboxGuardedDiskCommitRecord\`. It may be published only after the caller supplies a matching G21.66 strict *file-operation* \`Receipt\` (including pinned account file and complete coherent G21.64 terminal snapshot), AND the G21.83 PREPARED intent journal still identifies that account, message, G21.22 idempotency key, original PREPARED canonical SHA-256 and exact terminal SHA-256. The actual account file is reloaded and revalidated under the same G21.39 exclusive publication lock before the COMMIT record is created. Existing G21.32/47/48 review fences veto publication.

Ordering: pre-existing PREPARED journal -> confirmed forced terminal account file and genuine strict file Receipt -> forced COMMIT record temp -> create-only hard link -> force the containing directory. Any failure *after* link reports explicit \`UnconfirmedRecordException\` and preserves the record for read-only restart review. Duplicate writes never overwrite an old COMMIT record; there is no cleanup/release API. These guarantees are subject to ordinary filesystem API semantics, cooperating locks and system durability assumptions, **not proof of survival under arbitrary power loss**.

## Restart classification

A fresh instance can classify ABSENT, DISK_COMMIT_MATCH_NO_LIVE_APPLY, NEGATIVE_MARKER_MANUAL_HOLD, MISSING_ACCOUNT_QUARANTINE, PREPARED_NOT_COMMITTED (raw test rollback), INTENT_CONFLICT_QUARANTINE, DIVERGENT_TERMINAL_QUARANTINE, or INVALID_RECORD_QUARANTINE. Bounded 1024-byte NOFOLLOW record reads, exact journal matching and coherent terminal reconstruction fail closed.

A matching record **only proves that a particular disk-level COMMIT record exists and matches the current file**. It does NOT prove an authenticated transaction, durable ordering across arbitrary power loss, a live World item credit, exactly-once restart replay, release, ACK or safe normal admission. The returned observation fixes \`transactionCommitted\`, \`liveApplied\`, \`grantAuthorized\`, \`replayAuthorized\`, \`restartAdmissionAuthorized\`, \`releaseAuthorized\`, \`clientAckAuthorized\` all FALSE, with an independent \`diskCommitRecordMatched\` bit.

## Real integration evidence

\`G2186MailboxGuardedDiskCommitIntegrationTest\` first uses real \`WorldPlayerPersistence.reservePreparedAccount\` and the actual FIFO \`publishReservedTerminalStrictly\` to acquire a genuine G21.66 strict receipt, then publishes the disk record and reloads it through independent journal instances; checks terminal remains World-quarantined and no live owner inventory/CLAIMED changes. For each of five record publication fault windows, tests before-link absence vs post-link uncertainty preserving an inert record. Additional cases cover duplicate no-clobber, missing/foreign receipt, missing/mismatched PREPARED journal, changed terminal after receipt, journal deletion after COMMIT, raw PREPARED rollback, corrupted/oversized/symlink COMMIT records, negative marker precedence, and no leaked temp files or JVM leases.

**No user-facing widget32181/C2S185 claim path is enabled.** The COMMIT record is not yet in G21.84 guarded session admission or G21.85 portable restart witness. End-to-end positive settlement remains blocked until cross-restart transaction ownership, World-owned atomic application, replay semantics and postcommit release/ACK are demonstrated. Strict writer, production World save/publisher, Mailbox ledger, and frozen R25 #1847 unchanged.

Focused test manifest: 385 -> **386** unique Java11 tests plus standalone \`g2186MailboxGuardedDiskCommitRegression\`; stacked Draft PR remains unmerged pending exact-head full hosted CI.
