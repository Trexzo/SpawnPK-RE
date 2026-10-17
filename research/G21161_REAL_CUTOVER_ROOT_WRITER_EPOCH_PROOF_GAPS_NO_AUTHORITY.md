# G21.161 — expose the root, writer and epoch gaps on real restart cutover observations (NO AUTHORITY)

**Certified parent:** [G21.160 Draft PR #2500](https://github.com/Trexzo/SpawnPK-RE/pull/2500), exact `1a8874ed6708b638d355aaf8bb79ac9b9d16671c`, [Actions #38376092370](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38376092370) SUCCESS Java 11 focused **460/460** and full Gradle. Issue #2501.

## Existing production-adjacent read-only entry point

`MailboxSettlementCutoverReadiness.assess(account, FilePlayerRepository.RestartRecoveryEvidence)` is the original G21.79 *real file-backed restart cutover contract*: it maps each observed disk state to a conservative non-admitting disposition, rejects positive authority inputs, and surfaces four transaction-level `MissingProof` prerequisites. It does **not** perform a live World grant, attach a session, or initiate a rollback/cleanup/replay. G21.159 and G21.160 separately deny unsigned/untrusted recovery admission and account-scoped production signer trust, but **G21.79 did not expose their additional missing guarantees** to consumers of its cutover decision.

G21.161 changes this **existing** cutover contract (not merely another isolated after-lease class) to list three more permanently outstanding missing proofs:
1. `INDEPENDENT_SIGNER_ROOT_AND_HISTORICAL_CUSTODY`
2. `COMPLETE_RAW_DEFAULT_HISTORICAL_FUTURE_WRITER_FENCE`
3. `ATOMIC_MULTI_FILE_CRASH_RECOVERY_EPOCH`

Thus **seven** missing proofs now accompany every existing G21.79 cutover disposition; the original four durable commit, reconciliation, World apply and reservation-release/ACK prerequisites remain. The decision also exposes explicit **false** fields for enrolled signer root, historical custody, old/RAW/default writer exclusion, future writer fence, and atomic epoch. `assess` checks that G21.159 still has **zero** independently enrolled production signer roots and fails closed if an unexpected root registry entry appears, instead of silently upgrading authority. Original account/state classification is unmodified; no new side effect is introduced.

`MailboxSettlementCutoverReadiness.requireProductionSignerBeforeCutover(Decision, byte[])` adds a **strictly optional, deny-only** post-assessment root check on the existing cutover decision. It first verifies the decision has zero positive authority claims, delegates to G21.159 `requireProductionSignerRoot` (zero enrolled roots), then contains a final unconditional `CUTOVER_RECOVERY_ADMISSION_NOT_IMPLEMENTED_NO_AUTHORITY` veto if that root check were ever weakened. This method does not call any writer, acquire a lock, read disk, release a reservation or return an admission token. **Existing production recovery and World settlement callers are not rewired to call it; this milestone does not assert mandatory gate enforcement on all live paths.**

## Regressions and exact-head acceptance

G21.80's `MailboxStrictTerminalOrderingAudit.Result` also constructs its missing-positive-proof set from the G21.79 enum; its inherited integration regression now asserts the complete seven-proof set instead of the obsolete count four, while retaining every original strict ordering and no-grant assertion. The G21.81 terminal-identity and G21.82 restart-idempotency audits likewise forward the same enum-derived missing-proof set; their inherited tests now require all seven, without changing original dispositions or no-admission gates. The first hosted run failed at this stale inherited assertion after G21.79 passed; it is not the certified head.

The inherited **real-file-backed G21.79 test** `G2179MailboxSettlementCutoverContractIntegrationTest` remains in the focused train and now asserts **7/7** missing proofs and all new false flags for every genuine restart-recovery state, retaining its existing disposition matrix and protected no-grant World assertions.

The new `G21161MailboxCutoverTrustProofIntegrationTest` creates a temporary real `FilePlayerRepository` and strict writer, inspects a missing account, a saved legacy account, and the same account with a durable manual review marker. Assert correct original file-backed dispositions, all seven missing proofs on each observation, **four** production-signer root vetoes across original and replacement ephemeral P-256 public keys, **three** malformed candidate-key vetoes, rejected foreign-account assessment, file bytes unchanged, zero independently enrolled roots, and zero active JVM leases/watches. No ephemeral signer private key is persisted or installed; no permission is granted.

**Acceptance:** dedicated `g21161MailboxCutoverTrustProofRegression`, hosted Java 11 bytecode major 55, **461/461** focused regressions, and full Gradle SUCCESS on final exact PR head. G21.160 remains the certified fallback until CI passes.

## Explicit limitations

This adds deny-by-default trust-proof exposure to a *read-only* real cutover evaluation; it does not supply authenticated operator key provisioning, source custody, complete noncooperating RAW/default/historical/future writer exclusion, or a durable atomic multi-file crash/power-loss epoch. It does **not** enable live grant, settlement, replay, release, session, client ACK, unlink, archive rollover/pruning or cleanup. The G21.129 physical scanner, G21.100 lifecycle lock, original persistence writer, frozen R25 PR #1847 and G21.87/88 remain untouched. Keep stacked PR **Draft and unmerged**.
