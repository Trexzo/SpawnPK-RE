# G21.82 — two-observation cross-restart idempotency classification (NO GRANT)

Parent: [G21.81 draft PR #2342](https://github.com/Trexzo/SpawnPK-RE/pull/2342), exact certified commit `57eabca28738de3691dab1c9d97832bda490b925`, [Actions #38041026941](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38041026941) SUCCESS, 381 unique Java 11 focused regressions. Tracks [issue #2343](https://github.com/Trexzo/SpawnPK-RE/issues/2343).

## Scope and boundary

G21.81 can reconstruct a coherent G21.64 terminal identity from an account snapshot independently decoded after restart. G21.82 adds `MailboxRestartIdempotencyReconciliation` to compare TWO separately captured observations for the SAME canonical account. A match only means **the observed terminal identity strings are identical**; it does **not** establish durable COMMIT, no duplicate replay in the intervening time, a lock on uncooperative processes, signed evidence, or World live-apply state. A matching fingerprint cannot authorize a claim.

Deterministic non-admitting comparison states:
- REPEATED_TERMINAL_IDENTITY_QUARANTINED: same complete identity fields and terminal snapshot hash, no replay.
- CONFLICTING_TERMINAL_IDENTITIES_QUARANTINED: two coherent but differing terminal transactions for one account, manual reconciliation.
- PREPARED_TO_TERMINAL_UNCONFIRMED: an apparent transition, but receipt, durable COMMIT and live apply are not proven.
- TERMINAL_TO_PREPARED_REGRESSION_QUARANTINED: a former terminal observation followed by PREPARED/legacy.
- UNSETTLED_NO_TERMINAL_RECORD: both PREPARED/legacy with no terminal; do not infer settlement.
- NEGATIVE_MARKER_MANUAL_REVIEW: any marker in EITHER observation dominates, including same terminal bytes.
- MISSING_ACCOUNT_QUARANTINE / INVALID_OR_CONFLICTING_QUARANTINE: absence or inconsistent/replaced evidence fails closed.
- Cross-account comparison is refused even if fingerprints happen to match.

All results always enumerate G21.79's four still-missing positive proof families and explicitly fix COMMIT, durable confirmation, live apply, grant, replay, rollback, admission, reservation release and ACK to false.

## Disk-backed coverage

`G2182MailboxRestartIdempotencyIntegrationTest` uses real `StrictDurablePlayerSnapshotWriter` file saves and independent freshly constructed `FilePlayerRepository` readers for repeat, conflict (two separate World fixture owners with the same account and distinct message/key), PREPARED->terminal, terminal->PREPARED raw-rollback injection, repeated PREPARED, legacy, missing, missing->terminal, invalid checksum, three independent negative-marker classes, and foreign account denial. It checks actual World restart veto for quarantined terminals and marker states, live owner UNCLAIMED/inventory empty, preserved marker bytes and identical terminal file bytes during read-only comparisons, no temp/lease leaks.

**No positive durable COMMIT, no cross-restart transaction ledger, no true exactly-once runtime behavior and no replay are implemented here.** This only makes idempotency-relevant observations explicit and deterministic; a later implementation must use durable write-ahead and commit authority, restart reconciliation, World-owned atomic apply and postcommit ACK/release semantics.

Focused test manifest: 381 -> **382 unique Java 11** plus standalone `g2182MailboxRestartIdempotencyRegression` Gradle task. No production writer/World/packet changes. Draft / unmerged until exact-head full hosted CI. Frozen R25 #1847 untouched.
