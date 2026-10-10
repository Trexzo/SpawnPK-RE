# G21.81 — restart-load terminal transaction identity (NO GRANT)

Exact parent: G21.80 commit `a62b3613f583bac437465a9838978da1754e1b85`, [Actions #38040454201](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38040454201) SUCCESS, 380 Java 11 focused regressions. Tracks [issue #2341](https://github.com/Trexzo/SpawnPK-RE/issues/2341).

## New, narrow authority boundary
The existing G21.64 immutable terminal account snapshot already contains the message ID, G21.22 prepared idempotency key, canonical PREPARED and hypothetical postimage SHA-256 values and a field checksum; G21.64 enforces complete inventory+CLAIMED consistency. G21.80 distinguishes strict file operation from a durable transaction.

G21.81 adds `MailboxRestartTerminalIdentity`: an **inert reconstruction of those identity fields** from a freshly decoded account snapshot, combined with a separate G21.71 read-only forensic classification. No pre-crash WorldPlayer, original PREPARED proposal, in-memory reservation, or G21.66 `Receipt` is needed to *describe* a terminal file. It returns account, message, intent key, before/after canonical hashes, terminal-snapshot canonical SHA-256, and a deterministic diagnostic SHA-256 over those fields. It is not an authenticated credential.

- A coherent terminal plus coherent G21.71 state -> terminal identity is visible but **QUARANTINED**.
- A negative marker (durable review / uncertain / write intent) -> **manual hold dominates**, even if the terminal record is otherwise coherent.
- PREPARED or legacy -> no terminal identity, no positive settlement.
- Missing account -> missing/quarantine. Invalid or conflicting file/forensic evidence -> no identity and quarantine.
- Foreign account, mismatched snapshot or noncanonical account -> explicit rejection.
- Every result lists all four missing G21.79 positive cutover proofs, fixed-false COMMIT, live apply, reward grant, replay, rollback, admission, release, ACK and durability fields.

## Real file-backed regression
`G2181MailboxRestartTerminalIdentityIntegrationTest` seeds PREPARED/TERMINAL through existing strict writer, reloads coherent terminal independently through *two new* FilePlayerRepository instances and verifies stable identity fields/fingerprint, then checks real World restart admission still rejects the terminal. It also checks:
- PREPARED and legacy are not recoverable positive terminal commitments
- Stale prepared G21.71 observation combined with a later terminal snapshot fails closed
- Terminal checksum and postimage hash tampering cause quarantine
- Stranded write-intent marker on a coherent terminal overrides identity and remains byte-identical; the real World loader still vetoes
- Missing and foreign accounts fail closed
- No forensic write, live inventory/UNCLAIMED mutation, temporary file or account JVM lease leak

These are actual filesystem fixture operations, **not an electrical power-loss simulation**. The G21.71 observation and the detached account load are not atomic, and the diagnostic fingerprint is SHA-256, not signed; the result has ZERO positive authority.

## Still missing (unchanged blockers)
1. Positive durable COMMIT record and proven on-disk ordering across power loss
2. Cross-restart idempotent exactly-once reconciliation
3. Owner-atomic World live apply
4. Post-commit reservation release and client ACK safety

The strict writer, World admission, original client C2S185/widget32181 handler, permissions, and frozen R25 PR #1847 are unchanged. Keep PR Draft/unmerged until full exact-head CI PASS with 381 unique focused Java11 tests.
