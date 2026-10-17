# Chat 2 G18.5.8 — real installed G18 consumer throws after G1 terminal receipt

Date: 2026-10-14.

**Exact certified G21.195/G18.5.7 parent:** [joint draft #2613](https://github.com/Trexzo/SpawnPK-RE/pull/2613), exact SHA `709e85da11635f1d0137fa864d8cf32bf09ae80d`, hosted [Actions #38590398280](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38590398280) **SUCCESS**, **502 focused**, Java11 bytecode major55 and full Gradle. The head is not main; it remains draft/unmerged.

## Source contract and new distinction

This extends [#2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587), after G18.5.7 showed a **nonthrowing** G1 `INVALID_STATE` rejection while downstream DMM still advances. G18.5.8 characterizes an actual **throwing installed downstream G18 consumer**, not a synthetic replacement observer or manual replay:

1. Register one exact WorldPlayer; call actual `LocalLabDailyMoneyMakingRuntime.activateEasyIfCurrent` (objective starts at 0/3).
2. **Test-only controlled in-memory corruption:** reflection removes the one objective entry from the private `ObjectiveProgressService.entries` map while leaving the World owner and active DMM PlayerState in place. Preflight checks that `dmm.snapshot(account)` now throws `NullPointerException("objective")`. No production APIs or persistence bytes are modified.
3. Spawn and lethally finalize ONE canonical NPC via unchanged `LocalLabMonsterSpawnerProvisioning.create(world)` and installed combined terminal observer.
4. G1 invokes `PvmRecordService.recordIfCurrent` first, accepting one PvM kill/receipt and scheduling a respawn. The later G18 record consumer calls `state.snapshot()`, which throws due to the missing objective; `MonsterSpawnerPvmRuntime.settlePending` catches the observer's Throwable **after** one ground settlement and entry removal, returns `FINALIZED`, and logs `G1_PVM_TERMINAL_OBSERVER_FAILED`.
5. **Restore the exact original G18 objective entry** removed by test-only reflection, before retry. Validate that `dmm.snapshot(account)` is healthy again but still has **0/3 progress**. Assert the original terminal result is FINALIZED, one ground item, G1 kills=1/receipts=1, original NPC absent from runtime, `retrySettlement(npcId)` returns NOT_OWNED, G18 remains 0/3, and real due-time World queue still creates the next NPC. This proves the normal settlement retry does **not** recover a missed consumer even after its fixture is repaired; no automatic outbox delivery exists.

Marker: `G1858_INSTALLED_G18_CONSUMER_FAILURE_NO_RETRY_PASS`.

## Interpretation / limits

**CUSTOM_LOCALLAB test-only reflection fixture**, not evidence that the actual external SpawnPK client causes this state, a runtime exploit, original SpawnPK kill rules or routine production exception probability. We do not assert which earlier G4/G14/G15/G16 consumers recorded progress. This is a source-backed demonstration of an atomicity/recovery boundary: **finalized + drop settled + G1 recorded != all terminal consumers acknowledged**. A future LocalLab per-consumer outbox must retain immutable canonical death identity, recipient/generation authority, and each consumer's delivery status rather than treating `FinalizeStatus.FINALIZED` as an all-consumer acknowledgment.

No automatic replay, per-consumer redelivery, reward grant, account marker repair, final-save recovery, duplicate ground-drop settlement or production behavior change. G21.195 cross-JVM ACTIVE marker review remains separate and experimental/default-off; #2540/#2559/#2558 OPEN. Original server reward authority is UNKNOWN_SERVER_AUTHORITY.

Exactly **2 files** (existing permanent G1847 characterization test + this document), zero production Java, zero `server/build.gradle` changes, **502 focused unique** manifest entries unchanged. Fresh exact-head hosted CI required for new G1858 marker, existing G1857/G1856 and G21195, Java11 major55 and full Gradle. Draft/unmerged, Chat 1 controls integration/promotion.
