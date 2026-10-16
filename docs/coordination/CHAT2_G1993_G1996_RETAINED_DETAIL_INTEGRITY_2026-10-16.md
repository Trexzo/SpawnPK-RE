# Chat 2 G19.9.3–96 — retained real NPC terminal detail validation

## Source authority and exact parent
Parent is [#2880](https://github.com/Trexzo/SpawnPK-RE/pull/2880) `092b4dfbc47ac4f3b3166e13e5cac76a14317f20` (749-test hosted CI PASSED). Its World-local `LocalLabTerminalEvidenceConsistencyAudit` reconciles six-consumer marginals, origin×consumer cross-tabs, first blocking callback totals and bounded ring size, but does not prove that the **128 retained individual death reports** belong to the aggregates.

## Production change
Extend `LocalLabTerminalEvidenceConsistencyAudit.inspectComponents` with bounded `inspectRetainedDetails`:
- For each retained report, validate nonnull canonical NPC/tile/recipient/origin evidence, exact current-origin match flag, absent-recipient/spawn-generation contradictions, G1 and stage THREW vs failure-type consistency, and the existing canonical `LocalLabTerminalFailureBoundaryTally.classify` fail-fast sequence.
- Reject **duplicate `EntityId`** within the retained report ring, even when independent aggregate counts mathematically reconcile. Duplicated *diagnostic* observations do not re-finalize or reward the NPC; they simply cannot be considered reliable per-event evidence.
- Recompute retained counts for all 5 origin statuses, first-blocking categories, all six consumers×five outcomes, and origin×consumer×outcome intersections. Every retained cell must be a **lower bound** of its aggregate cell after ring eviction; before any eviction, every cell must be exactly equal.
- Saturated aggregate counters are **INDETERMINATE**, never treated as exact comparisons. Findings are bounded to the existing 64-item immutable read-only report. No new mutable ledger, network path, persistence write, recipient replay or grant.

## Genuine source-backed regression entrypoints
- **G1993**: 131 real canonical NPC spawns/deaths/ground settlements, 3 detail evictions, 128 retained reports; all detail counts must be subsets of existing process-local totals.
- **G1994**: genuine post-ground G1 exception and real malformed G14 fail-fast vs healthy controls; retained failure-type and first-blocker classifications remain coherent.
- **G1995**: from one genuinely settled NPC, construct two *test-only* collector captures referencing its same `EntityId`. The existing aggregate checker would balance; the new detail checker flags `DETAIL_DUPLICATE_NPC_ID` as DRIFT. This does not re-run the terminal observer or settle the ground drop twice.
- **G1996**: real disconnected recipient, missing-at-spawn original and foreign World tests cannot be misclassified as delivered grants.
- Real fixture G1965 strengthened with a substantive duplicate-evidence assertion. Manifest **749→753 unique**, four dedicated JavaExec gates, **69→73 exact MUST-RETAIN critical test anchors**.

## Certification and limitations
Hosted exact-head **753/753**, markers G1993–96, Java11 bytecode major55, full Gradle and independent certified 749 parent required. Draft only, main unchanged.

This is process-local **CUSTOM_LOCALLAB_OBSERVATION_ONLY**. Retained detail checks do not reconstruct evicted death identities or original SpawnPK server kill-credit authority. A mathematical CONSISTENT report cannot prove reward application, idempotent per-consumer delivery, durable event IDs, final-save ACKs, restart recovery or permission to replay. #2587 remains open.
