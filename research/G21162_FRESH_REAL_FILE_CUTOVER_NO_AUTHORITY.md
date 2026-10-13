# G21.162 — fresh real-file cutover inspection before hypothetical production admission (NO AUTHORITY)

**Certified parent:** [G21.161 Draft PR #2502](https://github.com/Trexzo/SpawnPK-RE/pull/2502), exact `4f482c1d29b4cb0f042187f0a9681a9e40966f32`, [GitHub Actions #38378989594](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38378989594) SUCCESS, Java 11 461/461 focused, full Gradle. Issue #2503.

## Read-only enforcement gap

G21.161 exposes three additional missing-proof categories on G21.79's **real** cutover-readiness assessment and offers `requireProductionSignerBeforeCutover(Decision,key)`. But a `Decision` is a retained post-lease observation: an account or negative marker could change after the G21.71 cooperating file observation ends. A previously observed LEGACY account is not evidence that no review marker exists **now**. Even a coherent terminal is not COMMITTED, does not show independent signer custody and lacks a raw-writer fence and atomic crash epoch. No existing live recovery or settlement code should infer authority from G21.161 metadata.

G21.162 adds a separate `MailboxFreshFileCutoverAdmissionV1` with **no stale Decision/Review input**. Both `inspectFreshReadOnly(repository,account)` and the deny-only `requireFreshProductionCutover(repository,account,candidatePublicKeyDer)` call `FilePlayerRepository.inspectRestartRecoveryReadOnly` anew, which uses the existing cooperating account publication lock and read-only marker/account NOFOLLOW+digest checks. Each invocation then re-runs G21.79 `MailboxSettlementCutoverReadiness.assess` and ensures the entire seven-proof missing-set remains present and all positive authority flags stay false.

The fresh review returns `FRESH_FILE_FORENSIC_REVIEW_NO_AUTHORITY` with the **current** G21.71 disk state/disposition, seven unresolved proofs, and all grant/replay/session/release/ACK and cleanup authority permanently false. The require method is **always fail-closed**: missing accounts, prepared-only, legacy, manual/uncertain/stranded markers, invalid accounts or invalid prepared/terminal records yield checked `NO_POSITIVE_CUTOVER_STATE_...`; a coherent terminal must pass G21.161's production signer-root gate, which has **zero independently enrolled roots** and always rejects. Even future unsafe root enrollment cannot bypass the final unconditional veto.

**Security qualification:** Fresh reinspection is NOT a continuous lease, does not close a time-of-check/time-of-use window to uncooperative RAW/default/future writers, does not validate signed historical custody, nor provide an atomic multi-file crash/power-loss epoch. A review may become stale immediately after return. **This new class does not modify any original World/restart/settlement call site** and therefore cannot be claimed as a mandatory live production gate.

## Real disk regression

`G21162MailboxFreshFileCutoverAdmissionIntegrationTest` builds real temporary FilePlayerRepository accounts with StrictDurablePlayerSnapshotWriter and uses an ephemeral test-only P-256 public key. It covers:

- missing account fresh review and refusal (file remains absent);
- real legacy account bytes and refusal (file remains identical);
- creation of an on-disk G21.32 manual hold marker **after** retaining an earlier LEGACY review: a subsequent fresh call sees manual hold, not legacy;
- explicit **test-fixture-only** marker deletion followed by another fresh inspection: sees legacy again, proving no stale cached review is accepted;
- an actual coherent terminal account constructed using G21.79's planner and isolated World: fresh inspection returns COHERENT_TERMINAL_NOT_COMMITTED and root-denial is mandatory, both for valid key and malformed-key diagnostics. No inventory reward or claimed mailbox message and no on-disk mutation;
- malformed account input rejected; no admission method takes a retained `Decision` or `Review`, zero active JVM publication leases and local watches.

Expected diagnostics: five real-file fresh reviews, two marker-transition refreshments, four negative-state refusals, one production signer-root veto, two other checked vetoes. Positive authorization stays **zero**.

**Acceptance:** dedicated `g21162MailboxFreshFileCutoverAdmissionRegression`, full Java 11 major 55 and hosted Gradle focused **462/462** on the exact final head. This milestone is not certified without logged CI success.

## Continuing major blockers

Independent historical signer/capture-time custody, operator-authenticated root provisioning/rotation, complete legacy/default/RAW/future writer exclusion, atomic power-loss-safe multi-file epoch, positive durable COMMIT/order/idempotent recovery, and grant/cleanup/rollover/replay/session/release/ACK eligibility are still **unproved**. No original R25 frozen PR #1847, G21.87/88, G21.129 source scanner, session code, writer code, real World settlement or reward path changed. Keep the stacked PR Draft and unmerged.
