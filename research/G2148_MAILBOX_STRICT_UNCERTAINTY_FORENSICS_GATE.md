# G21.48 — distinguish G21.47 strict uncertainty in read-only restart forensics

**Certified parent:** G21.47 exact commit `a12d7a2b5956c54e3e5a517064dcfc87371f5393`, hosted [workflow #37901350020](https://github.com/Trexzo/SpawnPK-RE/actions/runs/37901350020) SUCCESS, 346 focused Java11. Frozen R25 promotion PR #1847 remains untouched.

## Concrete operator-forensics gap

G21.47 added a separate negative `.g2147-strict-postpublication-review` sidecar when the strict file-backed World writer detects an already-published but no-longer-current PREPARED account snapshot. This marker stores the canonical account identity, saved snapshot SHA-256 and its own checksum; its presence is included in the existing G21.32/G21.36 restart, live-session and World-save veto. However, G21.33's **read-only** `MailboxFencedRestartForensics.inspect` still assumed every `MailboxDurableReviewFence.present` result referred to an original `.g2132-mailbox-review` proposal marker. It called the G21.32 parser on an account that legitimately had only a G21.47 sidecar and reported `INVALID_OR_UNREADABLE_FENCE`. That mislabeled a valid new safety marker as an old corrupted proposal marker, hiding the actual strict-account publication uncertainty from manual operators.

## G21.48 bounded classification

The original `MailboxDurableReviewFence.fencePath` is unchanged; a narrow package-private `accountFileForStrictReview(account)` helper reuses the same canonical account path that G21.32/G21.47 use for their sidecar lookups. At the start of G21.33 `MailboxFencedRestartForensics.inspect`, if the **separate G21.47 sidecar** is present, a new strictly read-only branch classifies it first. If **both G21.32 and G21.47** markers exist, G21.47's stricter *postpublication uncertainty* classification takes precedence—never downgrade to a claimed or even simply PREPARED proposal state.

New enum outcomes:
- `STRICT_UNCERTAIN_EXACT_SNAPSHOT`: checksum-valid G21.47 account/whole-snapshot marker, and the current separately observed account file is canonical with exactly the marker's G21.30 full-account SHA.
- `STRICT_UNCERTAIN_DIVERGENT_SNAPSHOT`: checksum-valid marker but different canonical account bytes.
- `STRICT_UNCERTAIN_MISSING_ACCOUNT`: marker remains but file is absent.
- `STRICT_UNCERTAIN_INVALID_ACCOUNT`: present file is wrong identity/version or noncanonical.
- `STRICT_UNCERTAIN_ACCOUNT_READ_FAILED`: the original FIFO forensic account read failed.
- `STRICT_UNCERTAIN_INVALID_MARKER`: marker bytes/format/identity/checksum cannot be validated.
- `STRICT_UNCERTAIN_MARKER_CHANGED`: the valid marker was detected missing or changed during observation.

The G21.47 record is checked **before** and **after** the existing `WorldPlayerPersistence.observeUntrustedMailboxAccount(account)` single-FIFO forensic load, and only if both observed account identity/record hash match is an exact/divergent classification reported. This is the same limited double-read anti-race pattern as G21.33, NOT a transaction-wide atomic snapshot.

All report flags remain **false**: `grantAuthorized`, `replayAuthorized`, `rollbackAuthorized`, `releaseFenceAuthorized`, `sessionAdmissionAuthorized`, `fileDurabilityConfirmed`, `automaticRecoveryAuthorized`. The inspector does not mutate accounts, hydrate sessions, delete markers, queue a reward claim, invent a hypothetical postimage, certify hardware durability or provide positive recovery authority. The older G21.33 classifications for accounts with only G21.32 markers are preserved exactly; unfenced accounts still return `NO_FENCE_NO_AUTHORITY`.

## Deterministic integrated acceptance

`G2148MailboxStrictUncertaintyForensicsIntegrationTest` uses a real file-backed World strict PREPARED write that completes its ATOMIC_MOVE and directory force, then a real owner movement mutation in the G21.46 post-force test seam. That produces the genuinely unconfirmed G21.47 negative sidecar. Read-only FIFO inspection must classify exact persisted PREPARED digest, then a manual/offline divergent snapshot, absent account, corrupted account, and corrupted G21.47 marker distinctly. It also verifies:
- Existing G21.32 `EXACT_PREPARED_UNCLAIMED` classification still works on another account.
- If both marker formats coexist, valid strict-postpublication uncertainty is prioritized over the old G21.32 exact proposal.
- A clean account reports only `NO_FENCE_NO_AUTHORITY`.
- A fresh World still denies accounts with either marker or both, and loads only the clean account.
- Every report authorization flag is false; all original live Mailbox rows remain UNCLAIMED with no inventory credit; no automatic marker removal or temp files.

Java11 focused manifest **346→347**, task `g2148MailboxStrictUncertaintyForensicsRegression`, hosted exact-head full CI required before certification.

## Limitations

The observed G21.47 marker and account snapshot are **separate files**. Double-reading the marker only detects some races and cannot prove atomicity, disk durability, crash safety or an exactly-once reward COMMIT. In particular, the G21.47 crash window **after account replacement but before sidecar publication** remains. No native C2S185 widget32181 reward grant, auto rollback/replay or review marker release is implemented. Bank widget32178 and frozen R25 promotion PR #1847 remain untouched.
