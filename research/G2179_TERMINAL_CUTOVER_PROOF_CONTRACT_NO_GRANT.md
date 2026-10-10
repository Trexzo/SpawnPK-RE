# G21.79 — Terminal settlement cutover contract (NO GRANT)

**Parent:** G21.78 exact-head `ff7c817591385e1d813ce11cb07edc7411ed3fc8`; [Actions #38037434965](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38037434965) attempt 2 **SUCCESS**, 378 focused Java 11. Tracks [issue #2337](https://github.com/Trexzo/SpawnPK-RE/issues/2337).

## Why this exists

G21.64 can construct an internally coherent persisted *hypothetical* inventory+CLAIMED snapshot. G21.66 can publish it under current owner checks, G21.67 can reconcile it read-only, G21.68 can stage it onto a detached player, G21.69/70 can attest the live World owner while checking disk under one lock/deadline, and G21.71–78 can inspect files safely after interruption or restart.

**None** establishes the four independent proofs needed for a safe **positive** native claim. A successful strict save, coherent terminal file, matching SHA-256 portable witness, or real World tick does not, by itself, prove durable exactly-once settlement. Existing terminal/quarantine and review marker vetoes are authoritative and remain intact.

## Four missing proof families (cutover blockers)

1. **Positive durable COMMIT and ordering:** prove that atomic account inventory+CLAIMED and an unambiguous commit identity survive power loss, including directory flush failures and platform-specific fallbacks; a forensic hash is not a signed commit.
2. **Idempotent cross-restart reconciliation:** using only durable data, distinguish already committed, PREPARED-only, partial/uncertain and foreign state without double credit, unsafe rollback or needing an extinct in-memory reservation; quarantine unresolved states.
3. **World-owned live apply:** after commit durability, transition the exact owner generation and account snapshot on the World tick atomically from the perspective of gameplay, while excluding concurrent World saves and session changes. A detached postimage is not a live player.
4. **Post-COMMIT reservation release + ACK:** release the account reservation, publish correct packets and success acknowledgement exactly once or with idempotent recovery semantics, **only after** a provable commit and live-apply boundary.

## Implemented in this PR

- `MailboxSettlementCutoverReadiness` is a **pure, read-only, explicit policy contract** over the nine G21.71 recovery states. It does not read/write files, change account admission, unlock reservations, or create a grant token.
- PREPARED_UNCLAIMED: normal previously-existing session loader MAY admit that file but a Mailbox reward is unsettled and cannot be replayed automatically.
- COHERENT_TERMINAL: quarantined despite valid internal inventory+CLAIMED coherence; there is no external positive COMMIT.
- Durable-review, uncertain-commit, and stranded-write-intent markers: negative/manual holds. Missing or invalid account: quarantine. Legacy no journal: not a G21.22 transaction.
- All results enumerate the four missing proof families, with `transactionCommitted`, `liveApplied`, `grantAuthorized`, `replayAuthorized`, `releaseAuthorized`, `clientAckAuthorized` fixed false.
- `G2179MailboxSettlementCutoverContractIntegrationTest` exercises real persisted PREPARED, coherent TERMINAL, invalid prepared/terminal, three negative markers, legacy and missing account; checks the original terminal session admission veto, preservation of live UNCLAIMED/empty inventory, foreign evidence rejection, unchanged marker and account bytes, no temporary/JVM lease leaks. All nine states must remain exhaustively mapped.
- Focused Java 11 train 378 → **379**, plus standalone `g2179MailboxSettlementCutoverContractRegression`.

## Explicit non-goals

No positive COMMIT, no native C2S185/widget32181 claim, no actual inventory or Mailbox CLAIMED mutation, no client ACK, no automatic restart replay, no marker deletion, no reservation release, no relaxation of the strict World session admission path. No replay of previously quarantined terminal files. This is a contract/checklist for later separate design and proof work, **not** reward settlement.

Frozen R25 PR #1847 untouched. This PR must remain Draft/unmerged until exact-head full Gradle hosted certification of 379/379 Java11 focused tests.
