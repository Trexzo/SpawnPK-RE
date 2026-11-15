# R62 — Offline pair completion certificate and quarantine readiness evidence

## Status and scope

R61 can roll a **previously committed** two-account trade forward from the four R56 split-save states using disposable account files, but correctly leaves both participants quarantined because its R57/R59 PREPARED intent remains indexed. **R62 does not remove that quarantine**. It introduces an immutable, independent `VERIFIED_OFFLINE` completion certificate, then tests whether a later JVM can re-validate the certificate against the same committed pair without writing any account files or declaring a real login safe.

This is a **standalone offline recovery-proof component** in a draft, stacked branch. Real LocalSession, TradeService, account autosave, logout, login admission and persistent owner coordination are NOT wired to it.

## Read-only committed-pair verifier

`R61TradePairReplay.verifyFullyPublishedUnderLock(journalRoot, accountRoot, payloadRoot, txId)` is a new package-private, **read-only** method. Its caller MUST hold R60's cooperative exclusive OS file lock. It verifies:

- A strict, bounded `R61` COMMIT decision with matching canonical transaction UUID and ordered participants.
- The complete `R59` index and all `R57` manifests. **Each participant must belong to exactly one pending pair**, namely this transaction; both reads must identify the same positive index revision.
- Both immutable post-trade snapshot payloads match the COMMIT hashes, and the R57 journal recognizes exactly `BOTH_AFTER`.
- Both *existing* canonical account files load successfully, hash exactly to the COMMIT post-state values and are recognized as the journal's `BOTH_AFTER` state.

If either account is still old, in an R56 split state, missing, corrupted, has received a later legitimate gameplay mutation, or the index has another pending pair or unknown staging, the verifier throws. **It neither saves accounts nor repairs them.**

## Separate immutable completion marker

`R62TradePairCompletionCertificate.publishCandidate(journal, accounts, payloads, completion, transaction)`:

1. Requires a preexisting dedicated **completion directory**, distinct from the account, payload and journal directories. It never creates/fixes those directories or reads from a real user profile.
2. Acquires the same R60 cross-JVM OS lock that governs cooperative R57/R59 preparation and R61 replay.
3. Refuses an existing marker. Calls the R61 strict read-only verifier, then writes a versioned exact-key `completion-<UUID>.properties` file via R16's forced-staging/required-atomic-replace writer. The marker holds only UUID, pair identities, their SHA-256 post-state hashes and the exact R59 index revision, **not raw account contents**.
4. Reloads and verifies the marker while still holding the lock. It never deletes, retires, relabels or edits the original R57 journal or R59 index.

`inspectCandidate(...)` repeats the check under the R60 lock. A result is either `OFFLINE_COMPLETION_CANDIDATE` (evidence currently matches) or `BLOCK_INDETERMINATE` (any mismatch, stale revision, missing/invalid marker, ambiguous stage or unavailable lock). Even when candidate evidence matches, `loginAuthorized()==false`, `journalQuarantineReleased()==false`, and `automaticAccountRecoveryAttempted()==false`.

**A certificate is not a durable account login grant, a release decision or cryptographic authorization.** It has no power to bypass R59's pending-account gate.

## Windows acceptance — November 15, 2026

- Isolated JDK21 Gradle `jar testClasses verifyJava11Bytecode --offline --no-daemon`: **BUILD SUCCESSFUL**, 2,219 Java11 major-55 classfiles.
- `R62_OFFLINE_COMPLETION_CERTIFICATE_PASS`: refuses a candidate before full replay; verifies a completed pair; publishes in a separate child JVM followed immediately by `Runtime.halt(92)`; opens the marker in another fresh JVM; confirms that duplicate publication is refused and that both account and index files are unchanged by candidate creation.
- Negative controls: ambiguous/unfinished stage preserved, overlapping lock denied, unexpected later canonical autosave denied without overwriting it, malformed/stale certificate rejected, removed COMMIT record denied, and a later R59 index revision invalidates the earlier certificate. Neither participant becomes eligible for real admission; R59 still blocks them.
- Inherited R61 replay, R60 cross-process lock, R59 revisioned index and R17 crash/lease tests **PASS** locally. The dedicated Linux/Windows GitHub CI additionally exercises the full R55–R61 regression set and ordinary trade flow.

## Remaining safety boundaries

1. **No live integration:** R62 has no active link to `LocalSession`, `TradeService`, `WorldPlayerPersistence`, the profile repository's live write path or account login admission. The live dual-account save gap proven by R56 is not fixed.
2. **No full quarantine release:** neither `R59` index retirement nor durable COMPLETED phase nor atomic pair generation advancement exists. A valid offline certificate still leaves both accounts blocked. A reader cannot simply use a returned boolean as an authorization token after the lock is released.
3. **Cooperative lock only:** unrelated autosaves, non-cooperative JVMs and malicious filesystem writers may bypass R60. The tests inject unexpected account mutation and verify denial, but cannot certify all races or account generation authority.
4. **Per-file durability only:** forced staged files and required atomic rename do not prove persistence through actual hardware power failure, parent-directory metadata loss or unusual filesystem semantics. `Runtime.halt` is a software process interruption.
5. **No cryptographic trust anchor:** hashes detect mismatched evidence; they do not authenticate a privileged writer who can rewrite the index, COMMIT record, payloads and completion marker together.
6. **No safe cleanup:** R57 manifest, R59 index, R61 COMMIT/payloads and R62 certificate are retained. An orphan stage is never automatically deleted or promoted. Manual recovery still requires a fully specified ownership and durability protocol.

## R63 direction

Build a **test-only generation and account-save admission fence** that makes all cooperative writer paths (save, logout, login, recovery and transaction completion) share the same authorization boundary, then inject concurrent save/restart attempts while holding the lock. Do **not** enable live quarantine release until that boundary is demonstrated and dual-account durable finalization is separately proven.

**R62: DRAFT, STACKED ON R61, UNMERGED.**
