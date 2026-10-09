# G21.64 — single-account terminal snapshot schema (NO GRANT)

**Parent:** G21.63 exact `06b0223231ea4d602a32ab46e15d4abb6cdf4bf8` with 363 focused Java11 hosted-green. [Issue #2304](https://github.com/Trexzo/SpawnPK-RE/issues/2304). Frozen R25 PR #1847 untouched.

A hypothetical Mailbox settlement MUST not scatter inventory credit, Mailbox CLAIMED, and terminal identity across independent mutable files. `MailboxAtomicTerminalSnapshot.compose` creates ONE immutable version-2 `PlayerSnapshot` that contains all three: proposed inventory, selected CLAIMED envelope, and `extension.mailbox-terminal-snapshot.*` transaction identity (account, message, G21.22 idempotency key, prepared/postimage SHA-256 and checksum). The entire account state can be replaced by the existing strict single-file atomic rename primitive, but is **not** authorized as live settlement.

The read-only `inspect` validator requires canonical terminal schema, full nonterminal postimage hash, matching prepared journal identity, selected CLAIMED row, attachment fingerprint, and exact proposed 28-slot inventory. Incomplete/changed fields fail closed. The actual `MailboxPreparedRestartAdmission.inspect` checks the terminal namespace FIRST, so even orphaned terminal markers with missing G21.22 journals cannot bypass the existing no-journal admission fast path. All valid terminal postimages are quarantined until a separately proven positive settlement protocol exists.

The G21.64 real-file integration tests strict successful atomic postimage round-trip, pre-rename unchanged account, post-rename uncertain result, negative World restart outcomes, corrupted/partial terminal records, duplicate claim preflight refusal, and unrelated profile admission. Focused train 363→364. No grant/replay/rollback/auto-release/ACK. The terminal record checksum is **not** a cryptographic signature of server authorization; an apparent complete terminal record is not itself evidence of a crash-durable positive COMMIT.

Do not enable native widget32181 or merge R25 PR #1847. Keep Draft/unmerged. Exact-head hosted CI and full build are required prior to certification.
