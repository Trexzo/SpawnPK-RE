# G21.30 — canonical strict-file snapshot receipt (no Mailbox reward grant)

**Certified parent:** G21.29 commit `f643f6e896c01f58d870cdd2b7e5149afb1ba719`, hosted run [37798012227](https://github.com/Trexzo/SpawnPK-RE/actions/runs/37798012227) SUCCESS, focused 328/Java11.

## Narrow evidence added
G21.23's successful `StrictDurablePlayerSnapshotWriter.Receipt` previously contained only account/path/authority. That did not identify which account snapshot was written, and a legitimate receipt for a PREPARED record could be mistaken for evidence of a later hypothetical inventory+CLAIMED file.

The existing opt-in strict writer now computes a **SHA-256 content fingerprint** before writing, covering a domain-separated, deterministic, unambiguous encoding of complete versioned `PlayerSnapshot`: version integer, normalized account, number of keys, and every length-prefixed UTF-8 key/value in sorted order. The fingerprint is independent of `Properties.store` nondeterministic ordering, `saved.at`, platform newlines, and comments. A `Receipt` now also carries `snapshotVersion` and `snapshotSha256`, plus an exact `matchesSnapshot` verifier. Those fields become available **only after** the unchanged sequence of file-force, required atomic replacement and directory-force succeeds. Pre-rename errors and uncertain post-rename failures still **return no Receipt**.

This establishes what immutable snapshot was supplied to the successful G21.23 strict file operation. It is NOT cryptographic proof against an adversarial caller, concurrent external file replacement, compromised application process, storage hardware failure or a completed World-level Mailbox transaction. No persistently recoverable signed receipt or transaction log was introduced.

`MailboxBoundStrictReceiptEvidence` supplements G21.29 without weakening its veto. It first requires G21.29's live owner/generation/immutable-envelope guard and the G21.28 point-in-time same-worker account observation. It distinguishes:
- `EXACT_PREPARED_NO_GRANT`: selected account still PREPARED; never automatically release from this classifier.
- `HYPOTHETICAL_MISSING_RECEIPT`: exact hypothetical disk bytes visible without any strict success record.
- `HYPOTHETICAL_RECEIPT_FOR_DIFFERENT_SNAPSHOT`: a genuine same-account strict save record for a different snapshot (including PREPARED) cannot certify a hypothetical postimage.
- `BOUND_HYPOTHETICAL_FILE_OPERATION`: in the fixture, the exact hypothetical full-account snapshot was the argument to a successful strict operation, and exact hypothetical bytes were observed after a G21.28 FIFO drain. This is **only** a bounded in-memory file-operation evidence classification; it is NOT a grant.
- `REJECTED_OR_UNTRUSTED_OBSERVATION` and `RESTART_QUARANTINE`: stale identity, malformed observation, foreign receipt, or missing in-memory reservation.

Every result retains `restartRecoveryAuthorized=false`, `liveGrantAuthorized=false`, `replayAuthorized=false`, `liveReconciliationAuthorized=false`, `clientSuccessAuthorized=false`. A successful matching file-operation receipt cannot bypass these flags.

## Permanent regression
`G2130MailboxBoundStrictReceiptIntegrationTest` checks complete matching PREPARED content, nonmatch against hypothetical, sorted-key determinism, unrelated key / changed version / wrong account veto; no receipt for pre-atomic failure; no receipt even with visible hypothetical bytes after post-rename directory-force failure; no grant for exact PREPARED; missing/incorrect/genuine PREPARED receipt veto against hypothetical; exact hypothetical receipt recognized only as **file-operation-bound**; foreign receipt, forged observation, restart, retired generation, safe explicit G21.28 PREPARED repair/release and zero live inventory or Mailbox claim mutation.

Test-only strict calls on an isolated temporary account file are deliberately not wired to the real WorldPlayerPersistence FIFO or C2S185 reward handler. Focused manifest: **328→329 Java 11**; Gradle regression `g2130MailboxBoundStrictReceiptRegression`; exact-head hosted CI required before certification.

## Unmet safety and implementation gates
1. No live strict postimage commit is admitted through the *same* reserved account persistence FIFO as all ordinary saves. The G21.30 test-only direct strict calls are NOT an integration path.
2. G21.27 reservations are in memory, not reconstructible after restart. G21.30 receipts are also in memory; failure immediately after strict success can still leave no recovered independent proof.
3. There is still no crash-durable, once-only server transaction log coordinating immutable inventory credit, selected Mailbox CLAIMED, in-memory World state, disconnect/re-registration, final saves and replay.
4. No v308 native visual/manual reward acceptance or original SpawnPK server reward policy proven.

Until all gates are established, widget **32181 remains NO_GRANT**. Bank widget **32178** excluded. Frozen R25 promotion PR #1847 unchanged.
