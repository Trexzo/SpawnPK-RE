# Chat 2 G18.7.2 — invalid G14 persistence aborts G15/G16/G18 terminal delivery after G1/G4

Date: 2026-10-15.

**Certified cumulative parent**: [G21.205 + G18.7.1 #2655](https://github.com/Trexzo/SpawnPK-RE/pull/2655), exact SHA `64611aed96477a129f58a90c12491cdefcee8174`, hosted [Actions #38737819317](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38737819317) SUCCESS, **518 unique focused**, Java11 major55/full Gradle. This child is DRAFT/unmerged.

## Why move beyond the G4 file-reconciliation series

The **actual** installed terminal observer in `LocalLabMonsterSpawnerProvisioning` first executes the G1 `G1MonsterSpawnerPvmLoopService` (canonical receipt/respawn) and then invokes `creditTerminalProgression`: G4 Slayer, G14 Daily Challenge, G15 Task Scroll, G16 Adventure, G18 Daily Money Making **sequentially**. `MonsterSpawnerPvmRuntime.settlePending` catches ANY `Throwable` from the entire observer only **after** it has already settled ground and removed terminal ownership; it logs `G1_PVM_TERMINAL_OBSERVER_FAILED` yet returns `FINALIZED`. Any G14 exception can leave G1/G4 applied and block downstream G15/G16/G18, with NO exact consumer replay path. [Issue #2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587) tracks the unresolved per-consumer post-settlement outbox, and the original server policy remains UNKNOWN_SERVER_AUTHORITY.

G18.5.9 already demonstrated earlier G4-throw partial delivery. G18.7.2 tests **a different actual later consumer, G14**, with G4 successfully completing before G14 throws; this isolates stage ordering and downstream consumer loss rather than claiming all errors behave alike.

## Deterministic installed-observer regression

1. Register a real WorldPlayer. Legitimately start G4 Monster Hunter ACTIVE0, G15 Task Scroll ACTIVE0, G16 Adventure ACTIVE0 and G18 Daily Money Making EASY0/3. Before G14 is first read, insert ONLY a **test-owned intentionally malformed** `daily-pvm-g14` namespace (`version=invalid-g1872-test-only`) into that player's in-memory snapshot extension. G14's normal `LocalLabDailyChallengePersistence.decode` rejects the keyset, caching an owner-identity-local `invalidPersistence` marker.
2. Spawn a genuine Monster Spawner canonical NPC with exact WorldPlayer+generation `RecipientOrigin`, apply actual lifecycle lethal damage and finalize via installed unmodified observer. Ground settles one coin, G1 kill receipt1 and G4 legitimate Monster Hunter completion1 are committed. Then G14 `recordMonsterSpawnerFinalization` throws due to invalid cached persistence, swallowed ONLY by the outer installed observer. G15 Task Scroll, G16 Adventure, G18 DMM remain at progress0 even though all three were independently activated before death. Terminal returns FINALIZED and original NPC is no longer owned.
3. Run the genuine G1 delayed respawn ticket and kill a **second different NPC** via the same actual installed observer. G1 receipt2, two coin settlements, G4 still one completion; G14 remains invalid and G15/G16/G18 still zero. Verify `localLabDailyChallenges().status` continues to reject the same owner with the same validation error. No same-NPC replay, synthetic observer, direct grant, or production hook.

Marker `G1872_G14_INVALID_PERSISTENCE_BLOCKS_G15_G16_G18_PASS`.

## Explicit scope and required next engineering decision

All behavior is **CUSTOM_LOCALLAB-only** in-process testing of malformed **test-created** G14 extension (NOT evidence a remote client can create this file). No original SpawnPK Daily Challenge/Task Scroll/Adventure/Money Making reward rule is inferred. This confirms an order-dependent delivery interruption, **not a fix**. Do not retry by respawning NPCs, resettle ground drops, or simply call downstream consumers again without a canonical event identity, per-consumer idempotent stage ACK, exact owner/generation fencing, and explicit replay policy; see #2587 and original credit policy [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558).

**Exactly three additive files:** a dedicated new `G1872G14InvalidPersistenceTerminalConsumerIsolationTest.java`, one entry plus both count guards **518→519 unique** in `server/build.gradle`, and this note. **ZERO production Java, G21.205 core/account, original G1847 tests, other gameplay, main/client/default flags**. Fresh hosted exact-head CI required G1872/G1871/G1870/G21205, 519/519 focused, Java11 major55 and full Gradle. Draft/unmerged; future Chat1 promotion should only import new dedicated test, one manifest update and note. Account #2540/#2559 and gameplay issues remain OPEN.