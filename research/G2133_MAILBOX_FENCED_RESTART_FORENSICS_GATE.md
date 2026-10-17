# G21.33 — bounded read-only forensics for a fenced Mailbox account

**Certified parent:** G21.32 exact `3b9d76de82e69dda552dbb37c686954e0827a9d6`; hosted [37824514689](https://github.com/Trexzo/SpawnPK-RE/actions/runs/37824514689) SUCCESS, focused 331/Java11. Frozen R25 PR #1847 untouched.

## Problem and non-authority scope

G21.32 creates a separate write-once **REVIEW_REQUIRED_NO_GRANT** sidecar with the canonical G21.22 intent key and G21.30 complete-account preimage and hypothetical CLAIMED postimage digests. A G21.32 marker vetoes login even when the original account is missing or the PREPARED journal has been lost. However it did not provide a safe bounded way to inspect **which** account bytes remain visible after restart without using the production login path or mistaking any observed state for transaction completion.

New `MailboxFencedRestartForensics.inspect(persistence,fence,account)` is an **observation**. It verifies the marker and its integrity checksum, calls the **existing G21.31 `observeUntrustedMailboxAccount` single-FIFO reader** (not session `load`), then rechecks marker identity to detect some deletion/mutation races. There is no new worker, account loading into the live player, I/O writer, grant, replay or release. The marker and account are still separate files: double inspection is **not a multi-file atomic snapshot**, cryptographic authenticity proof or success receipt.

Typed read-only outcomes:
- `NO_FENCE_NO_AUTHORITY`: no G21.32 marker; not a declaration that the account is safe.
- `INVALID_OR_UNREADABLE_FENCE`: marker checksum/format/read failure; login veto unchanged.
- `FENCE_DISAPPEARED_OR_CHANGED`: marker mismatch after same-worker disk read; no stable forensic conclusion.
- `ACCOUNT_READ_FAILED` and `MISSING_ACCOUNT`: explicit no-grant outcomes.
- `INVALID_OR_DIVERGENT_ACCOUNT`: invalid/noncanonical/foreign file.
- `EXACT_PREPARED_UNCLAIMED`: G21.30 full account digest matches the marker PREPARED fingerprint **and** G21.31 validates original Mailbox and 28-slot inventory journal. It remains **review-required**, never automatic cancellation.
- `EXACT_HYPOTHETICAL_CLAIMED`: complete account fingerprint matches G21.32 expected hypothetical digest; detached canonical replay inspection confirms the G21.22 intent identity and attachment list, selected Mailbox `CLAIMED` and each of the 28 inventory AFTER slots. It remains **review-required**, not a successful transaction or permission to credit again.
- `OTHER_ACCOUNT_POSTIMAGE`: any other canonical full account state, including tamper and missing journal, is not a known proof of settlement.

Every outcome explicitly keeps `grantAuthorized=false`, `replayAuthorized=false`, `rollbackAuthorized=false`, `releaseFenceAuthorized=false`, `sessionAdmissionAuthorized=false`, `fileDurabilityConfirmed=false` and `automaticRecoveryAuthorized=false`.

## Focused acceptance

`G2133MailboxFencedRestartForensicsIntegrationTest` tests: unfenced other account not automatically authorized; exact original PREPARED and exact hypothetical CLAIMED classification under a real FilePlayerRepository; login still refused in both cases; unrelated snapshot-key divergence; stripped journal; deleted account; corrupted marker; fresh World and I/O worker retaining veto; deterministic marker modification **while the same worker's forensic read is blocked**, which invalidates apparent stable evidence; injected repository I/O read failure; and no live inventory claim, Mailbox ACK, or account-file writes by the inspector.

Focused manifest **331→332**, Java11; task `g2133MailboxFencedRestartForensicsRegression`. Only exact-head hosted CI SUCCESS can certify G21.33.

## Unresolved boundaries

No automatic operator repair, marker deletion, journal rewrite, exact-once inventory settlement, crash-durable positive ledger, concurrent marker writer arbitration, or protected native widget32181 claim is included. The G21.32 marker is currently opt-in and not a live claim transaction. A forensic classification cannot turn an uncertain post-rename disk observation into a committed grant. Bank widget32178 excluded.
