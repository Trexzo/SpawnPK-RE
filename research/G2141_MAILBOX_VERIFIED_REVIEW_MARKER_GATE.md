# G21.41 — opt-in exact persisted PREPARED review marker

**Certified parent:** G21.40 exact `810a44631a60490f7b6159d89d8fa8995660ca65`; hosted [workflow #37836308385](https://github.com/Trexzo/SpawnPK-RE/actions/runs/37836308385) SUCCESS; 339 focused Java11. Frozen R25 promotion PR #1847 untouched.

## Bounded gap

G21.32 introduced an independently persisted no-grant review marker, whose immutable record includes canonical G21.30 SHA-256 fingerprints of a caller-provided PREPARED account preimage and a hypothetical CLAIMED postimage. G21.34 ensured the marker itself is write-once, while G21.38–G21.40 coordinate **cooperating** file-backed World account saves and marker publication using the same account-local cross-JVM file lock. However, the original `MailboxDurableReviewFence.arm(proposal)` deliberately permits a **negative-only** marker even if the underlying account file no longer exists or differs from the proposed PREPARED snapshot. That path must stay available for manual review of missing/corrupted data, but its receipt alone cannot truthfully attest to an exact on-disk PREPARED state.

## G21.41 addition — opt in only

New method `MailboxDurableReviewFence.armVerifiedAgainstCurrentFile(proposal)` reuses the same G21.32 proposal checks, immutable record format, unique temporary file, file force, G21.34 no-clobber hard-link publication, directory force, and G21.38–G21.40 cross-JVM account publication lock as the historical `arm(proposal)` API. It differs **only** by an extra read-only G21.41 check **inside that same publication lock**, immediately before the final marker link:

- Load the **current** snapshot from the exact `FilePlayerRepository` path for the named account; absent/unreadable/corrupt fails closed with no receipt.
- Validate and normalize the complete file-backed account snapshot using `PlayerSnapshotCodec.validateAndNormalize`, demand canonical unchanged snapshot values/version/account identity, and verify `MailboxPreparedRestartAdmission.inspect` produces exactly `VALID_PREPARED_UNCLAIMED` rather than a CLAIMED/credited or stripped/invalid journal.
- Compute `StrictDurablePlayerSnapshotWriter.canonicalSnapshotSha256` for the full snapshot and require equality with the record's expected complete PREPARED digest. A single changed field rejects the opt-in arm.
- On refusal, do not publish a marker, never overwrite account bytes, delete any unpublished temporary marker file in the existing `finally` path, release the JVM and filesystem locks, and do not claim a negative receipt.
- The old `arm(proposal)` is unchanged and **still explicitly supports a purely negative review obligation for missing or corrupted accounts**. Neither API creates a positive grant, replay, recovery, rollback or marker-release authority.

This additional verified receipt attests only to a **matching file snapshot as observed while the cooperating publication lock was held**, not positive delivery. It is deliberately not wired to native Mailbox C2S185 widget32181.

## Regression

`G2141MailboxVerifiedReviewMarkerIntegrationTest` covers exact PREPARED persisted roundtrip success, new-World session veto, absent/corrupted/hypothetical CLAIMED account refusal, changed canonical PREPARED snapshot refusal with original account untouched, duplicate marker no-clobber, unaffected separate verified account and retained unconditional `arm` semantics with an absent file. It also stages a deterministic concurrency ordering: a verified-marker call is held before acquiring publication lock; **an actual cooperating WorldPlayerPersistence account save** modifies the PREPARED file and completes; resuming the verified marker must detect the stale expected digest under lock and **fail before marker publication**. Verifies no temp leaks, no live inventory credit/Mailbox CLAIMED mutation and no JVM lock registry leaks.

Java11 focused manifest **339→340**, new `g2141MailboxVerifiedReviewMarkerRegression` Gradle task. Exact-head hosted CI must pass before certification.

## Remaining limits

The lock protects only cooperating G21.34/G21.36 file writers on the tested filesystem; direct/manual/legacy uncooperative writes can still race. The account file and negative marker are **not** one atomic durable multi-file transaction, so this does not establish hardware power-loss persistence, a durable account+marker COMMIT record, actual crash-recovery eligibility or exactly-once reward settlement. Read-only marker records and all grant/replay/release authorization flags remain false. R25 #1847 untouched.
