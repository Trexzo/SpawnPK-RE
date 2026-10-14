# Chat 2 G18.5.9 — installed G4 early consumer exception prevents later G18 delivery

Date: 2026-10-14.

**Exact certified parent:** [Chat 1+Chat 2 G21.196/G18.5.8 #2616](https://github.com/Trexzo/SpawnPK-RE/pull/2616), SHA `d3ea1398d0d671c02a37ca5614d78127b7d204da`, [Actions #38596218922](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38596218922) **SUCCESS**, **503 focused / 503 unique**, Java 11 major55/full Gradle. Core G21.197 standalone [#2618](https://github.com/Trexzo/SpawnPK-RE/pull/2618) is separately owned by Chat 1 and does not modify this gameplay source.

## Source-backed distinction from G18.5.8

The unchanged `LocalLabMonsterSpawnerProvisioning` observer invokes G1, then G4 Blood Slayer, then G14/G15/G16 and finally G18 Daily Money Making in direct sequence. If **G4 throws**, the consumer loop stops *before* G18 runs. In G18.5.8, a late G18 throw followed earlier consumers. `MonsterSpawnerPvmRuntime.settlePending` has already committed one canonical drop and removed its tracked entry before calling the installed observer, catches its exception and returns `FinalizeStatus.FINALIZED`, leaving no ordinary replay route.

New G18.5.9 test-only method `actualG4FailureStopsLaterG18()`:

1. Register one live WorldPlayer and start a legitimate G4 Monster Hunter task; assert 0/1 G4 task objective. Activate a separate legitimate G18 EASY PvM DMM objective; assert 0/3.
2. Via **test-only reflection**, change the private `BloodSlayerModeService.selectedByPlayer` entry from `MONSTER_HUNTER_PVM` to `BOSS_HUNTER_PVM`, while leaving the active Monster Hunter task untouched. This deliberately bypasses G4's ordinary no-mode-switch-while-active guard; no external client route or production corruption is claimed.
3. Execute one canonical NPC spawn, lifecycle death and finalization through the **actual installed combined terminal observer**. G1 commits one PvM receipt/kill and schedules respawn first; G4 `recordOwned` detects `active task/mode mismatch` and throws; G18 objective remains 0/3, the single ground drop settles and terminal result is FINALIZED.
4. Restore the exact original mode in the test fixture. Both G4 and G18 status/objectives remain valid but uncredited; `retrySettlement(npcId)` and `finalizeIfOwned(oldNpc)` return NOT_OWNED. The real World queue still executes G1 delayed respawn; do not count new NPC appearance as a second kill/drop.

Marker: `G1859_INSTALLED_G4_EARLY_FAILURE_BLOCKS_G18_PASS`.

### Limits / engineering implication

This is strictly a **CUSTOM_LOCALLAB** deterministic fixture. No claims of normal production frequency, external exploit, original SpawnPK server kill/Slayer/DMM policy, proof of effects on intermediate G14/G15/G16 credits, successful account replay, or consumer repair. Earlier G18.5.7 nonthrowing G1 rejection and G18.5.8 late throwing G18 are separately covered. The combination demonstrates that a single monolithic terminal `FINALIZED` return cannot prove **which** consumer stages committed; a future per-consumer outbox [#2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587) must preserve actual immutable canonical death ID, provenance/recipient-generation authority and independent delivery outcomes. Original reward authority [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558) remains UNKNOWN_SERVER_AUTHORITY.

Exactly **two changed files**: existing checked G1847 terminal characterization class plus this coordination note. All production Java, Gradle manifest/task, world/account persistence and G21.196 epoch prototype **byte-identical** to certified parent. **503 focused entries unchanged**. Fresh exact-head hosted CI required for G1859/G1858/G1857/G21196 markers, Java11 major55, 503/503 focused and full Gradle. Draft/unmerged; Chat1 controls integration/promotion; frozen R25/main/client untouched.
