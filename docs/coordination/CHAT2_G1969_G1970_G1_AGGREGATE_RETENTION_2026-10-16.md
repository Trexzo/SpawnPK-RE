# Chat 2 G19.6.9 / G19.7.0 — G1 aggregate outcome continuity

## Exact parent and scope
- Source parent: [Chat1+Chat2 cumulative #2844](https://github.com/Trexzo/SpawnPK-RE/pull/2844), exact `6d08473bb3f276c2d561bec1c5780f1de6854796` (701 focused candidate). G19.6.7/68 G1 pre-stage trace exists in that branch.
- Source scope: **CUSTOM_LOCALLAB, World/process-local diagnostic only**. Original SpawnPK kill-credit/transaction authority remains unknown.
- No G21 security/account source changes; no G1 player record, spawn/respawn, G4/G14/G15/G16/G18 consumer, persistence, packet, settlement or grant path modified.

## Real gap
The existing trace retains 128 detached reports and per-stage G4/G14/G15/G16/G18 outcome totals after eviction, but its new `G1Outcome` is only in per-event `Report`. After 128 observations, older G1 `THREW` outcomes become invisible to operators even though the outer observer failure counter still exists; the outer count does not identify *which stage failed*.

## Production module
- Extend `LocalLabSequentialTerminalStageTrace` with saturating exact collector-local `EnumMap<G1Outcome,Long>` (NOT_OBSERVED, RETURNED, THREW); increment only on successful `record(Capture)`, after verifying exact World and collector and rejecting already-recorded captures.
- Snapshot exposes a detached `Map<G1Outcome,Long>` plus exact `g1OutcomeCount`; map is unmodifiable. Older snapshots cannot change as later events arrive.
- Continue to evict individual reports after 128; only aggregate observational counts survive, **not** the identity or full evidence of an evicted event. Do not treat G1 RETURNED as reward ACK. NOT_OBSERVED is explicitly distinct from a successful G1 callback.

## Real-regression coverage
- G1969: actual installed World/provisioned spawner, 131 canonical NPC deaths with a registered recipient and detached session owner to avoid G1 respawn collision. Reuses and strengthens G1966 real-runtime fixture. Verify 131 G1 RETURNS and zero THREW/NOT_OBSERVED, three evicted reports, immutable detached initial and current snapshots and canonical settled ground items.
- G1970: actual installed two-death G1 duplicate pending respawn scenario, first returns, second throws after G1 record/ground settlement; assert aggregate G1 RETURNED=1/THREW=1 and stage NOT_REACHED. Separate same-World diagnostic collector captures a genuine settled terminal without observing G1: its count is NOT_OBSERVED=1, while foreign collector rejection counts remain zero. Reuses and strengthens G1967 and G1965 real-runtime source fixtures.
- Existing tests also updated to assert new invariants; manifest **701→703 unique** plus explicit G1969/G1970 JavaExec tasks.

## Certification and non-goals
Require independent hosted exact-head **703/703**, explicit G1969/G1970 markers, Java11 major55 and complete Gradle, plus separately successful 701 parent. Draft; main untouched. No durable reward outbox, persisted event ID, per-consumer idempotency or ACK, replay, origin attribution recovery, or cross-restart preservation. Issue #2587 remains open.
