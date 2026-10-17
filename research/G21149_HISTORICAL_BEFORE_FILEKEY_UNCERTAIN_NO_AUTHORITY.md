# G21.149 — historical BEFORE-only absent fileKey is UNCERTAIN (NO AUTHORITY)

**Parent:** certified G21.148 [Draft PR #2476](https://github.com/Trexzo/SpawnPK-RE/pull/2476), exact parent `7f180a2667759b15ea9f6b70cf08166fb281f933`, [Actions #38298548412](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38298548412) SUCCESS, focused 448/448 on Java 11. Issue #2477.

## Bug / evidence

G21.145 correctly classified missing NOFOLLOW `fileKey` in historical AFTER and current BEFORE/AFTER observations as UNCERTAIN. In G21.147 the historical *within-report* BEFORE/AFTER comparison also detects an absent key, but `requireReport` discards this local uncertainty, deliberately only throwing for concrete internal drift. The outer `compare` subsequently inspects historical AFTER versus both current captures. Therefore an absent fileKey in **historical BEFORE only** could be silently dropped and yield MATCH, which cannot establish original source identity.

## Bounded correction

Keep G21.147's concrete internal drift veto and G21.148's bounded SHA verification intact. In G21.149's in-memory `compare`, fold the already-validated historical BEFORE/AFTER pair's **uncertain labels only** into the returned report. The original cross-report comparisons still determine CHANGED and UNCERTAIN for all other captures; concrete drift still has precedence. This adds no lock, disk IO, trusted pin, copy, writer action or authority.

## Verification contract

Standalone `G21149MailboxHistoricalBeforeMissingKeyIntegrationTest` uses six actual NOFOLLOW physical source attribute sets as test fixtures, four synthetic but structurally valid retained report positions, and correctly self-hashed in-memory v1/v2/v3 portable snapshots. In-memory reflection simulates a provider returning null fileKey individually in every one of **6 source categories × 4 capture positions = 24** cases, including the **six previously missed historical BEFORE-only** cases. Assert exactly one UNCERTAIN source label and no CHANGED or authority labels each time. Restore the original field in `finally` and require immediate clean MATCH; no World writer invoked. The inherited G21.148 real four-bound World pipeline remains in the focused manifest, without duplicating its large fixture.

**Acceptance gate:** Java11 bytecode 55, `g21149MailboxHistoricalBeforeMissingKeyRegression`, focused 449/449 and full Gradle hosted exact-head CI. Commit alone is not certification.

**Strict non-claims:** An absent fileKey means identity cannot be verified from that captured report; it is not direct proof of tampering. Neither old nor current reports are cryptographically authenticated. A pair of forged consistent reports or a post-lease RAW writer may still evade observation. No complete original/default/historical/future writer census, signed independent provenance, atomic multi-file epoch or hardware power-loss guarantee. No archival rollover, pruning, cleanup, unlink, World reward grant, replay, session, release or client ACK is enabled. Frozen R25 #1847 and G21.87/88 remain untouched.
