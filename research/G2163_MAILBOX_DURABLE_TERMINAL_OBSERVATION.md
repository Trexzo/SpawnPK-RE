# G21.63: write-once, non-granting terminal postimage observation

Parent: G21.62 certified e1a0e4f7f81cf194392547c556bfd355f51da440 (362 focused Java11, Actions #37941177224 SUCCESS). Issue #2302.

## New bounded evidence primitive
MailboxDurableTerminalObservation writes a versioned, SHA256-checksummed, account-scoped record only after an exact hypothetical inventory+CLAIMED account postimage is already visible on disk. A second exact-disk check runs under the existing cooperating account-local publication lock immediately before write-once marker publication. File content is forced, marker published by no-clobber hard link, and its parent directory forced. Errors after hard-link creation are UNCONFIRMED and never auto-delete the potentially visible marker.

A restart reader performs bounded NOFOLLOW sidecar reads and strict canonical decoding, then compares the pinned account file to the exact postimage digest. States: ABSENT, OBSERVED_HYPOTHETICAL_POSTIMAGE, ACCOUNT_DIVERGED, INVALID_RECORD. Every outcome has grant/replay/rollback/release/ACK authority set false. Record checksum detects unintentional damage, is not a signature or authenticated COMMIT proof. This is deliberately an opt-in forensic/manual-review aid; no automatic player session hydration and no integration with native widget32181.

## Verification
Real-file integration checks absence, exact PREPARED refusal, successful postimage publication, duplicate no-clobber, divergent account, corrupted marker, synthetic after-link uncertain publication retaining the marker, World restart quarantine despite matching observation, independent-account isolation, and no temp/lease leaks. 362→363 focused Java11.

## Limits
This is NOT an atomic two-file transaction, trusted writer attestation, power-loss immunity, positive exactly-once settlement, autosave fencing, live inventory grant, replay/rollback, auto-release or client success authority. Never claim that an observed postimage is a durable reward COMMIT. Frozen R25 PR #1847 untouched; DRAFT/unmerged.
