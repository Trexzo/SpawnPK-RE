# Chat 2 G18.5.2 — controlled post-G1 failure yields partial progression delivery

Date: 2026-10-14. Parent: [Chat 2 + Chat 1 G21.188 / G18.5.1 cumulative #2588](https://github.com/Trexzo/SpawnPK-RE/pull/2588) exact `cbbebcc8e8135f9de0dae9d6edb74d87ba86e8bd`, [Actions #38561270563](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38561270563) **SUCCESS**: `G1851_TERMINAL_OBSERVER_FAILURE_NONRETRYABLE_PASS`, G1850, G21188, 495/495 focused, Java11 major55, full Gradle.

## Distinct experiment and exact limits

Installed `LocalLabMonsterSpawnerProvisioning` chains (1) real `G1MonsterSpawnerPvmLoopService.onFinalized` and (2) `creditTerminalProgression`, which sequentially calls G4 Blood Slayer, G14 Daily Challenges, G15 Task Scrolls, G16 Adventures, and G18 Daily Money Making. Source `MonsterSpawnerPvmRuntime.settlePending` commits canonical ground drops and **removes the NPC entry** before invoking the terminal observer, then catches observer `Throwable` without rollback or delivery-retry record.

G18.5.1 #2585 already confirmed a generic synthetic failing observer is non-replayable. This test adds an explicitly **controlled partial-delivery cutpoint**: instantiate the **actual G1 consumer class** using the same `World`, `MonsterSpawnerService`, spawn executor, and `PvmRecordService` as the actual `LocalLabMonsterSpawnerProvisioning.create(world)` graph. In test only, **replace** the installed combined terminal observer with a wrapper that first calls this real G1 `onFinalized` and then deliberately throws `G1852_INJECTED_POST_G1_PRE_G18_FAILURE`, before the later progression consumers run.

The checked existing `G1847MonsterSpawnerNameReuseTerminalAttributionTest` asserts:
- Real LocalLab canonical spawned NPC and death finalization return `FINALIZED`; one ground drop settlement is committed.
- G1 `PvmRecordService` increments exactly one kill and records exactly one receipt; G18's already-active Easy objective remains **zero** because the test intentionally did not execute it.
- `retrySettlement(npcId)` and duplicate `finalizeIfOwned(npc)` both return `NOT_OWNED`, do not re-run G1, do not deliver the skipped G18 consumer and do not re-settle drops.
- New `G1852_PARTIAL_TERMINAL_CONSUMER_DELIVERY_PASS` marker; all prior G1847/1848/1849/G1850 markers remain.

**Important claim boundary:** This is NOT an actual failure of the currently installed combined terminal observer and does NOT prove a G4/G14/G15/G16/G18 real consumer ever throws. A test-only observer is deliberately substituted to simulate a process-stage failure AFTER a successfully executed real G1 consumer. It demonstrates **the existing runtime cannot repair partial delivery at this cutpoint**, not the frequency/cause of such a failure in ordinary gameplay.

## Scope / coordination

Only one already covered test class plus this note; **no production code, tests manifest, Gradle, default flags or account session behavior changed**. Parent focused manifest remains **495 unique**. Separate engineering [issue #2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587) tracks the prospective **CUSTOM_LOCALLAB** outbox/idempotent terminal event with per-consumer receipts. Replaying ground loot would be incorrect. Any original SpawnPK server reward/credit transaction policy stays `UNKNOWN_SERVER_AUTHORITY`; actor attribution unresolved [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558), daily activity lifetime unresolved [#2547](https://github.com/Trexzo/SpawnPK-RE/issues/2547).

**Fresh hosted CI required:** exact commit `G1852_PARTIAL_TERMINAL_CONSUMER_DELIVERY_PASS`, G1851/G1850/G1849/G1848/G1847, G21188 review-intent primitive (NOT login-wired), 495 focused, Java11 major55 and full Gradle. Prior #2588 green is not this child certification. Chat 1 owns any promotion, do not double merge prior Chat 2 gameplay PRs; frozen R25/release pointers unchanged.
