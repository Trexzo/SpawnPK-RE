# Chat 2 G18.4.7 — real LocalLab Monster Spawner terminal callback and username reuse

Date: 2026-10-14.
Owner: Chat 2 gameplay.
Exact parent: [G21.182 cumulative gameplay #2563](https://github.com/Trexzo/SpawnPK-RE/pull/2563), SHA `ae380c5b819704fccb75850c1c7970ff673c4a6f`.
Evidence issues: [#2547](https://github.com/Trexzo/SpawnPK-RE/issues/2547) (DMM activity lifetime), [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558) (true kill-credit attribution).

## Source finding

`LocalLabMonsterSpawnerProvisioning.create(world)` installs the ACTUAL shared terminal callback:

`MonsterSpawnerPvmRuntime.FinalizeResult` → `G1MonsterSpawnerPvmLoopService.onFinalized(result)` → `LocalLabMonsterSpawnerProvisioning.creditTerminalProgression(world,finalization.recipientRef,...)`.

`creditTerminalProgression` obtains a `WorldPlayer` by **current username** (`world.players().byName(recipientRef)`), reads that current generation, and calls G4 Blood Slayer, G14 Daily Challenges, G15 Task Scrolls, G16 Adventures, and G18 Daily Money Making using the same string. The current-generation `owns()` check does **not** prove that the recipient belongs to the generation that spawned the earlier NPC, attacked it, or earned its kill.

The G1 PvM loop independently resolves the current username at terminal delivery for `PvmRecordService`. G18.4.5 has added `FinalizeResult.recipientOrigin`, captured at spawn. G18.4.6 proved actual lethal attacker can be a different WorldPlayer from the configured spawn recipient. Neither witness authorizes a reward policy.

## New regression (expected characterization, not yet certified)

`G1847MonsterSpawnerNameReuseTerminalAttributionTest` uses the **production LocalLab provisioning graph**, not a duplicated callback:

1. Register original `WorldPlayer A` as user U; activate `CUSTOM_LOCALLAB` Easy PvM objective at progress 0.
2. Use real `LocalLabMonsterSpawnerProvisioning.create(world)`, `MonsterSpawnerService` open/select/activate, then canonical PvM `spawnAndBind(U,U)`. Assert immutable spawn witness A/generation GA.
3. Unregister A and register new `WorldPlayer B` under identical U, without changing the NPC's canonical identity.
4. Synthetic lethal damage then the **actual installed terminal finalizer + settlement + observer**; assert successful terminalization and old immutable A/GA witness unchanged.
5. Characterize current result: the old NPC's terminal `recipientRef=U` credits **B's username-backed current DMM objective** to progress 1; separately, the G18.4.4 opt-in exact A/GA method refuses the stale original owner. This is an explicit observed **negative** result, not an acceptable attribution policy.
6. Repeat with no current U replacement and assert the observer does not credit the detached user's DMM objective.

The test has no packet/client claim and directly invokes canonical lifecycle damage instead of a real combat input; the terminal callback graph is real. It adds one regression class and Gradle task `g1847MonsterSpawnerNameReuseAttributionRegression`, increasing checked focused manifest from **488 to 489**.

Expected marker only **after** execution: `G1847_NAME_REUSE_TERMINAL_ATTRIBUTION_CHARACTERIZATION_PASS`. Required exact-head hosted checks: Java11 major55, 489/489 focused, full Gradle.

## Nonclaims / follow-up

- Not proof of a production exploited race: this is deterministic synthetic LocalLab sequence testing source behavior.
- Not proof that original SpawnPK should reward spawn owner, last hitter, top contributor, or current owner: server-only policy remains **UNKNOWN_SERVER_AUTHORITY**.
- Not a live remedy: source under `LocalLabMonsterSpawnerProvisioning`, G1 loop and all five progression consumers unchanged.
- Any next live correction requires clearly selected **CUSTOM_LOCALLAB** policy or original authority evidence, correct origin capture at admissible kill time, monotonic NPC finalization, and cross-generation race tests. Do not silently treat spawn-time witness as attacker.
- Keep #2547 and #2558 open. Chat 1 core G21.180–G21.182 account lease and persistence remain unchanged and retain separate integration authority. Chat 3 client recovery remains independent. Frozen R25 pointers unchanged.
- Draft only; no automatic merge.

### Ownership handoff

Chat 1: Review combined G21.182 branch before promotion; this new test is stacked on it. The G21.182 full-suite success cannot be inherited without fresh exact-head evidence.

Chat 2: Continue to document/recover original kill attribution and design evidence-tagged fail-closed progression, leaving live behavior unchanged until policy is verified.

Chat 3: Identify client-side combat packet/callsites separately; do not infer original server reward allocation from UI.
