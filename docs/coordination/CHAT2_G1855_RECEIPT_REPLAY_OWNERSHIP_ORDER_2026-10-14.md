# Chat 2 G18.5.5 — PvM REPLAY receipt is not player-generation authority

Date: 2026-10-14.
Parent: Chat 1 + Chat 2 cumulative [G21.193/G18.5.4 draft #2605](https://github.com/Trexzo/SpawnPK-RE/pull/2605), exact immutable `f5dca567476fdb79297ab15e25b733fbe7c5d699`, [hosted Actions #38577275465](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38577275465) **SUCCESS**: G21193, G1854, Java11 major55, **500/500 focused** and full Gradle. This is a child of the actual latest cumulative green gameplay + core tree, not a separate source fork.

## Newly isolated G1 receipt authority limitation

In `PvmRecordService.recordIfCurrent(WorldPlayer player,long expectedGeneration,EntityId npcId,long deathTick)`:
1. Before acquiring `world.withOpenPlayerMutationOwnershipIfCurrent`, the method tests whether its service-local `receipts` map already contains the `(npcId,deathTick)` key.
2. A previous receipt causes an immediate `Receipt.replay(...)` response, regardless of the caller's `WorldPlayer` identity or whether its generation is still admitted. This response carries the prior receipt's `kills` value. It performs **no mutation**.
3. Only a never-seen event reaches generation admission and can return `STALE_PLAYER_GENERATION` for a detached player.
4. Thus a REPLAY status is a fact about the service's **event identity**, NOT a proof that the requesting caller is the account generation credited originally. A future terminal outbox must not interpret REPLAY alone as per-player successful delivery.

## Permanent characterization

Extend the **already-checked** `server/src/spk/local/G1847MonsterSpawnerNameReuseTerminalAttributionTest.java`:
- Use actual `LocalLabMonsterSpawnerProvisioning.create(world)` with its *unchanged installed* combined observer to settle a canonical NPC under original WorldPlayer A/generation GA; the G1 service has receipt count 1 and A's PvM kills 1.
- Unregister A; register a distinct B under the **same username**, confirm B's PvM kills 0.
- **Test-only manual** `recordIfCurrent(A,GA,sameNpcId,sameDeathTick)` returns REPLAY even though A is stale. Call also as B/new generation: returns REPLAY even though B was not credited, and B's count remains 0.
- On the same stale A, use a **different deathTick** (no receipt): the method reaches the ownership fence and rejects `STALE_PLAYER_GENERATION`.
- No ground resettlement, PvM mutation, receipt insertion or new actual NPC death occurs after the original terminal callback.
- New marker `G1855_PVM_RECEIPT_REPLAY_PRECEDES_OWNERSHIP_PASS`; earlier G1847/48/49, G1850/51/52/53/54 markers and G21193 real-TCP forced-save marker all remain covered.

This is a conservative **CUSTOM_LOCALLAB** API-contract test, NOT evidence of original SpawnPK server kill ownership, automatic replay, a runtime exploit, cross-World admission, or any production policy fix. It does NOT establish that `PvmRecordService` improperly increments another player's counter: it explicitly verifies the opposite.

## Integration and unresolved engineering

Only one existing test source class plus this research/coordination note. **ZERO production source, zero Gradle changes; focused manifest stays 500 unique**. Combined exact-head hosted G1855/G1854/G1853/G1852 and G21193, Java11 major55, 500 focused and full Gradle **MUST PASS**. Do not inherit the green parent as certification for this new test.

If eventual `CUSTOM_LOCALLAB` per-consumer outbox/recovery under [#2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587) uses G1 `Receipt.replayed`, it must separately verify original event target/generation or a specifically chosen credit transfer policy; a replay receipt alone is not per-recipient acknowledgment. Original server reward attribution [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558) remains UNKNOWN_SERVER_AUTHORITY. Core G21.193 ACTIVE review marker remains non-retireable even following healthy forced file save; no durable account epoch/recovery claim. Chat 1 owns core/promotion, Chat 2 gameplay, Chat 3 client recovery; do not merge old stacked PRs twice, and frozen R25 unchanged.
