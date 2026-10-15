# Chat 2 G18.7.4 — valid G14/G15 terminal progress, then invalid G16 blocks G18

Date: 2026-10-15.

**Exact certified parent**: [G21.207/G18.7.2+G18.7.3 combined #2663](https://github.com/Trexzo/SpawnPK-RE/pull/2663), SHA `9fd00c90edcbbd28607806234ab6c2fa915df958`, [GitHub Actions #38743138076](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38743138076) SUCCESS, **522 unique focused**, Java11 major55/full Gradle. This child is independent of Chat1's still-building G21.208 core [#2664](https://github.com/Trexzo/SpawnPK-RE/pull/2664) and 523-focused cumulative [#2665](https://github.com/Trexzo/SpawnPK-RE/pull/2665). All branches draft/unmerged.

## Source-backed rationale

The ACTUAL installed `LocalLabMonsterSpawnerProvisioning` terminal observer sequentially runs G1 canonical kill receipt/respawn, then G4 Slayer, G14 Daily Challenge, G15 Task Scroll, G16 Adventure, and G18 Daily Money Making. Any G16 throw is caught only at the outer `MonsterSpawnerPvmRuntime` terminal-observer boundary *after* ground settlement, which still returns FINALIZED. Separate G18.7.2 test [#2656](https://github.com/Trexzo/SpawnPK-RE/pull/2656) shows G14 failure skips G15/G16/G18; G18.7.3 [#2659](https://github.com/Trexzo/SpawnPK-RE/pull/2659) shows G15 failure after G14 progresses. G18.7.4 isolates the last intermediate fault stage: **G16 throws after G14 AND G15 have both genuinely progressed**, so G18 alone is downstream and skipped.

## Deterministic installed-observer acceptance

1. Register one WorldPlayer and legitimately create G4 Monster Hunter ACTIVE0, G14 Daily Challenge ACTIVE0, G15 Task Scroll ACTIVE0, and G18 EASY0. Unlike those activated consumers, G16 must not have hydrated the owner before fixture setup. Inject ONLY a **test-created malformed** `LocalLabAdventurePersistence.NAMESPACE` extension with invalid version into the current owner. The normal G16 decoder latches an identity-scoped invalidPersistence marker on the first real terminal.
2. Spawn a canonical Monster Spawner NPC capturing exact player/generation origin, genuinely apply lethal damage and finalize through the installed unchanged observer. Ground settlement1, G1 kills/receipt1, G4 completion1, G14 progress1, **G15 progress1**, then G16 invalid decode throws; G18 activated objective remains0. No synthetic observer, direct reward grant or event replay.
3. Execute real G1 delayed World respawn and kill a genuinely different NPC under the same exact current player/generation. Second terminal FINALIZED, G1 receipts2, two coin settlements, G14 progress2, **G15 progress2**, G4 completion remains1, G16 error remains sticky, **G18 still progress0**. No original NPC replay or duplicated terminal.
4. Verify G16's normal `activate` path throws the same owner-local `Adventure persistence invalid` exception after both terminals. Because invalid G16 cannot simultaneously have a legitimately activated G16 task on the same owner, **this test does not claim a valid G16 objective itself missed credit**; the directly observed lost downstream consumer is **pre-activated G18**. Separate clean valid-G16 baseline exists elsewhere; no original SpawnPK rule inferred.

Marker: `G1874_G16_INVALID_PERSISTENCE_BLOCKS_G18_PASS`.

## Explicit authority boundaries

This is **CUSTOM_LOCALLAB** gameplay reliability characterization only. Malformed G16 bytes exist only because the test inserted them into an in-memory WorldPlayer extension; no remote client exploit, normal account-file corruption path, authenticated TCP session final save, original SpawnPK Adventure/Money Making rewards policy, global replay mechanism, crash recovery or durable per-consumer ACK is established. A future solution must not resettle coins or repeat the same canonical death; see [#2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587) and original rewards [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558).

**Exactly three changed files:** dedicated NEW `G1874G16InvalidPersistenceTerminalConsumerIsolationTest.java`, `server/build.gradle` ONE focused entry and both count guards **522→523 unique**, this source handoff. **ZERO production Java, account G21.208 or G21.207, older G1847, any G1872/G1873, client/main/default modifications**. Fresh hosted exact-head CI required G1874/G1873/G1872/G21207, **523/523 focused**, Java11 major55/full Gradle. Draft/unmerged; Chat 1 should later import ONLY this new class+note and manifest entry on the newest core-certified branch without duplicating ancestors. Account #2540/#2559 remain open.