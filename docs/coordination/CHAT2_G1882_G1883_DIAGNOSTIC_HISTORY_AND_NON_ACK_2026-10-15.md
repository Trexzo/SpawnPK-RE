# G18.8.2–G18.8.3: terminal-stage diagnostics are bounded and not ACKs

## Source lineage
- Base exact certified [G18.7.8–G18.8.1 draft #2691](https://github.com/Trexzo/SpawnPK-RE/pull/2691), `ddb12ddfddac44ca78f2eba7264e9327fdf0f39e`, [Actions #38764607384](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38764607384) **SUCCESS**, 540/540 focused/Java11 major55/full Gradle.
- This branch adds exactly two focused test files, new coordination note and one Gradle manifest/task change **540→542**; **ZERO production, flag/default, original-client, frozen R25, account or main changes**.
- This branch's own fresh exact-head CI must pass before a certification claim. DRAFT/UNMERGED.

## G18.8.2 — bounded in-memory journal with genuine NPCs
- Real World with registered WorldPlayer and the actual installed Monster Spawner observer using the opt-in stage isolation instance.
- Starts one real NPC, kills/finalizes it through actual `NpcLifecycleService.applyDamage`, `MonsterSpawnerPvmRuntime.finalizeIfOwned`, ground settlement and G1 record service. Uses actual `WorldEventQueue.runDue` after each death for G1 delayed respawn, then repeats until **130 distinct canonical NPCs** have independently died and settled.
- Each death must have unique NPC id, new settlement/record, and no canonical runtime entry after terminal. The diagnostic report history must equal `min(number of terminal events, MAX_REPORTS)`, and at 130 reports it must retain the **last 128** entries (oldest two evicted).
- Checks 130 independent G1 receipts, 130 actual credited kills and aggregate 130 ground coins, and that retrying the first finalized NPC still returns `NOT_OWNED`. Report eviction is **not** a reward deletion, a retry or proof of storage durability.
- Marker: `G1882_TERMINAL_DIAGNOSTIC_BOUNDED_128_REAL_NPC_PASS`.

## G18.8.3 — callback RETURNED versus actual credit
- Real spawned/killed/settled NPC under a live owner with **G15, G16, G18 never activated**.
- Installed opt-in observer returns from each absent-objective callback, and stage reports show `RETURNED` for G15/G16/G18.
- It must **still** be the case that G15 has no task assignment, G16 no Adventure objective and G18 no Daily Money Making snapshot. The real G1 record and one coin settle once; a duplicate finalized NPC cannot rerun the callback.
- Demonstrates a non-negotiable design constraint: `RETURNED` is only Java control-flow, **not** a durable applied reward or consumer ACK. The same applies to `THREW`: a consumer may mutate before throwing.
- Marker: `G1883_TERMINAL_STAGE_RETURNED_NOT_CREDITED_PASS`.

## Integration and nonclaims
- Both tests are strictly `CUSTOM_LOCALLAB` diagnostic/non-ACK boundary evidence. No original SpawnPK server gameplay policy, authenticated damage/kill attribution, cross-JVM session or crash durability can be inferred.
- The diagnostic FIFO deliberately drops old entries after 128; it is categorically unsuitable as a durable outbox. **Never replay from it, never use it for direct grant or ACTIVE marker cleanup.**
- Use [#2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587) for future durable per-consumer stage ACK design, [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558) for original kill credit, and G21 #2540/#2559 for account ownership. All remain open.
- Chat 1 may incorporate exactly the two Java tests plus this doc and ONE Gradle manifest increment onto a newer certified core or cumulative tree after independent hosted GREEN. No merging to main.
