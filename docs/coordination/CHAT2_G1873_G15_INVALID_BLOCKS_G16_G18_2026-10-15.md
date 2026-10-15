# Chat 2 G18.7.3 — G15 Task Scroll exception interrupts G16/G18 after G14 credits

Date: 2026-10-15.

**Exact certified parent:** [G21.205/G18.7.1 #2655](https://github.com/Trexzo/SpawnPK-RE/pull/2655) SHA `64611aed96477a129f58a90c12491cdefcee8174`, [Actions #38737819317](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38737819317) SUCCESS, **518 focused unique / Java11 major55 / full Gradle**. The independent G18.7.2 [#2656](https://github.com/Trexzo/SpawnPK-RE/pull/2656) G14 error case is being built separately on that same certified parent and is NOT imported in this branch.

## Source-backed installed-observer boundary

The actual `LocalLabMonsterSpawnerProvisioning` post-terminal progression chain is G1 loop -> G4 -> G14 -> G15 -> G16 -> G18. It is a single synchronous observer. The installed `MonsterSpawnerPvmRuntime` catches a thrown `Throwable` after a REAL NPC finalization and ground settlement, logging observer failure and returning `FINALIZED`. There is no per-consumer ACK/retry. G18.7.2 isolates G14 before G15/16/18; **G18.7.3 isolates G15**, proving G14 can progress while G16/18 fail to receive the same terminal.

## Deterministic fixture and expected observations

1. Register source WorldPlayer A; legitimately start G4 Monster Hunter ACTIVE0, assign one real G15 Task Scroll ACTIVE0, activate World-owned G18 EASY0. Spawn canonical NPC with immutable exact A/generation recipient origin, then unregister A.
2. Register same-name B with **empty G4** persisted extension (preserving visible World-owned G4 task); legitimately initialize G14 challenge0 and G16 Adventure0 for B; confirm G18 World objective0. Before B's FIRST G15 hydration, inject a **test-only invalid `LocalLabTaskScrollPersistence.NAMESPACE`** map (only `version=invalid-g1873-test-only`). No forged client input or production hook.
3. Deliver one genuine NPC death through installed `MonsterSpawnerPvmRuntime.finalizeIfOwned`. G1 receipt1, G4 completes and G14 Daily Challenge progresses1; G15 `ensureRestoredOwned` caches a validation error and `requirePersistenceValid` throws. The *outer* terminal observer catches this post-settlement failure and returns FINALIZED. The pre-activated G16 Adventure and G18 DMM remain0. G15 task in the World is still assigned; no G15 progress.
4. Run actual G1 delayed respawn, capture new NPC origin=B/generation, kill/finalize **a different canonical NPC**. G1 receipt2, two independent one-coin settlements, G14 challenge2 while G4 completion remains1; G15 failure repeats, G16/G18 remain0. In the same B identity, G15 public `active` still throws the exact same invalid cached-persistence error.
5. Unregister B; register fresh same-name C with **empty** G15 extension. Query `localLabTaskScrolls().active(name)` solely to inspect the retained World Task Scroll objective0 and assignmentCount1, not to replay old deaths. G14 remains2 and World G18 remains0. This independently verifies the loss is not merely a bad B progress display.

Marker: `G1873_G15_INVALID_PERSISTENCE_BLOCKS_G16_G18_PASS`.

## Authority and engineering implications

This is **CUSTOM_LOCALLAB** behavior; malformed G15 namespace is intentionally injected by the test only. Does not demonstrate an exploitable client input, original SpawnPK rules, actual TCP account admission/logout, external reward policy, or a valid way to replay the same NPC event. G1 canonical receipt/ground settlement have already happened, so consumer recovery requires durable per-event, per-stage acknowledgment, immutable recipient owner+generation, idempotency and partial failure isolation rather than re-running full NPC death. See [#2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587), unknown original credits [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558).

**Exactly 3 additive files:** dedicated `G1873G15InvalidPersistenceTerminalConsumerIsolationTest.java`, one entry and both hard-coded count guards **518→519** in `server/build.gradle`, plus this note. **ZERO production Java, G21.205 core/accounts, older G1847, client/main/default changes**. Fresh hosted exact-head CI REQUIRED: G1873/G1871/G1870/G21205, 519 unique focused, Java11 major55 and full Gradle. Draft/unmerged; Chat1 should import only the new class/manifest/note after GREEN, no duplicated earlier G18 tests.