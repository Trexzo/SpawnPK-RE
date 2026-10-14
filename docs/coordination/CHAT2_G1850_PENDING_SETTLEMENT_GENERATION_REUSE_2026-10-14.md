# Chat 2 G18.5.0 — terminal retry credits replacement generation after old owner disconnects

Date: 2026-10-14. Draft / unmerged.
Parent: Chat 2 cumulative [G21.187+G18.4.9 #2581](https://github.com/Trexzo/SpawnPK-RE/pull/2581), exact `77f3bd54745e8694268941c4fec04869fa5f8bc8`, carrying unchanged core G21.187 process-kill and G21.186 genuine cross-JVM TCP checks. Earlier G18.4.9 standalone [#2577](https://github.com/Trexzo/SpawnPK-RE/pull/2577) head `031bd37f6ad3e1166d3e0f66db4bf0ead55748e9` hosted GREEN (G1847/G1848/G1849, 492 focused).

## Separate timing / credit hazard
Prior G18.4.7 A→B case disconnects A before the NPC takes lethal damage. G18.4.9 tests queued respawn tick after login change. **Neither alone** verifies a terminal settlement that became pending while **A was still online** and is retried only when **B owns the same username**.

`MonsterSpawnerPvmRuntime` captures immutable `RecipientOrigin(A,generationA,username)` at spawn and carries it across SETTLEMENT_PENDING retries (G18.4.5). The production LocalLab completion callback only fires after successful ground settlement and uses `finalization.recipientRef` to resolve a **current** player by username for G1 PvM record and G4/G14/G15/G16/G18 progression. Therefore A→B replacement while the settlement is pending risks crediting B for an NPC whose death occurred while A was still current. The actual original SpawnPK kill-credit/settlement policy remains `UNKNOWN_SERVER_AUTHORITY`.

## Existing-test extension
Extend the already covered `G1847MonsterSpawnerNameReuseTerminalAttributionTest` with `pendingSettlementBeforeReconnect()`, not a new test class:
1. Register A and activate G18 Easy objective, open/select/activate real LocalLab Monster Spawner, spawn canonical NPC with A/generation immutable stamp.
2. Insert a canonical ground-item `995,Integer.MAX_VALUE` blocker at the NPC tile for that username, then kill the NPC at tick 75 and call actual `finalizeIfOwned`. Expect `SETTLEMENT_PENDING`, no G18 progress and no G1 record while A still owns the login; immutable A-origin preserved.
3. Unregister A, register B with identical username, clear blocker, retry **actual** `MonsterSpawnerPvmRuntime.retrySettlement(npcId)`.
4. Expect successful `FINALIZED`, old A origin unchanged/not-current, the installed observer credits B's DMM objective progress 1 and G1 kill record 1, while A's record stays 0.
5. Repeating retry after finalized must not produce another terminal credit or accepted G1 receipt.
6. Add distinct marker `G1850_PENDING_SETTLEMENT_GENERATION_REUSE_PASS`, retain G1847/1848/1849 markers.

**No new class/task/manifest entry, no production source changes.** Focused checked suite remains **494**. There is no new reward rule, no actual-client exploit claim, and no original SpawnPK respawn/credit assumption. This is a deterministic negative **CUSTOM_LOCALLAB** source characterization with a real installed terminal observer. It is not a security remediation.

## Test / integration
Fresh exact-head hosted Java11 bytecode major55, G1847/1848/1849/G1850, G21186/187, 494 focused, full Gradle are required. This successor stacks only on #2581; do not merge older stacked Chat 2 branches independently if cumulative promoted. Chat 1 owns promotion and core/session/account persistence; Chat 2 gameplay, Chat 3 client deobfuscation. Open issue [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558) remains original kill credit; [#2540](https://github.com/Trexzo/SpawnPK-RE/issues/2540)/[#2559](https://github.com/Trexzo/SpawnPK-RE/issues/2559) remain durable account authority. Frozen R25 unchanged.
