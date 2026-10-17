# Chat 2 G21.188 + G18.5.1 — minimal cumulative gameplay overlay

Date: 2026-10-14. Draft/unmerged. Owner: Chat 2 gameplay; Chat 1 core account admission/persistence and promotion.

**Exact core parent:** [Chat 1 + Chat 2 G21.188 joint draft #2586](https://github.com/Trexzo/SpawnPK-RE/pull/2586), `568e4cf27f69e26638c7ef5199e46c208e32463f`; stacked on hosted-green G18.5.0 [#2583](https://github.com/Trexzo/SpawnPK-RE/pull/2583). This parent contains Chat 1's NEW, STANDALONE `LocalDurableAccountReviewIntentPrimitive.java`, G21.188 process-kill forced sidecar regression, and exactly **495** focused tests. The primitive is intentionally **not wired to LocalSession**: it does NOT fix stale BOOTSTRAP admission, does not prove durable account recovery, excludes uncooperative writers and remains opt-in/experimental.

**Exact Chat 2 source:** [G18.5.1 draft #2585](https://github.com/Trexzo/SpawnPK-RE/pull/2585), commit `e82910c8743dfd45ffdf69af8c786b084514a0d2`, hosted [Actions #38559892366](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38559892366) SUCCESS: `G1851_TERMINAL_OBSERVER_FAILURE_NONRETRYABLE_PASS`, `G1850_PENDING_SETTLEMENT_GENERATION_REUSE_PASS`, `G1849_MONSTER_SPAWNER_RESPAWN_NAME_REUSE_PASS`, 494 focused, Java11 major55, full Gradle.

## Composition exactness
1. Begin with the exact G21.188 #2586 tree. Preserve `server/build.gradle` and G21.188 manifest/task without changes: **495 unique** focused tests.
2. Overwrite only `server/src/spk/local/MonsterSpawnerPvmRuntimeTest.java` with exact tested Chat 2 blob `4ca099f91afea1ddb86260975adff139dd968888`.
3. Import exact Chat 2 `docs/coordination/CHAT2_G1851_TERMINAL_OBSERVER_NONREPLAYABLE_2026-10-14.md` blob `e107341321cb217d6d80f7e1182f448a6e8453ca`, plus this handoff.
4. The G18.5.1 extension is exercised by the already-existing dedicated `monsterSpawnerPvmRuntimeRegression` Gradle task in the current-train verification. No new test class or manifest member. **Zero production Java / account lock / socket / persistence / generated asset changes.**

## Scoped evidence / no policy inflation
The runtime's canonical ground-item settlement succeeds and NPC ownership entry is removed *before* calling `TerminalObserver.onFinalized` inside an exception-catching boundary. A synthetic observer that throws causes the runtime to return `FINALIZED`; subsequent `retrySettlement` / duplicate `finalizeIfOwned` are `NOT_OWNED` and cannot replay the missing observer delivery, while drops remain committed exactly once. This is a confirmed **CUSTOM_LOCALLAB runtime reliability limitation**, not an observed failure in the actual installed G1/G4/G14/G15/G16/G18 handlers. Future explicit durable/idempotent event/outbox design belongs to [issue #2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587), subject to original server reward attribution unknowns [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558).

Existing G18.4.7/8/9 and G18.5.0 source-backed username/generation name-reuse regressions remain intact. Their outcomes are **negative LocalLab characterizations**, not evidence of original SpawnPK server kill ownership or intended PvM respawn behavior. Chat 1 G21.188 forced review-intent helper remains a NOT-LOGIN-WIRED prototype, distinct from durable admission/recovery policy [#2540](https://github.com/Trexzo/SpawnPK-RE/issues/2540) and [#2559](https://github.com/Trexzo/SpawnPK-RE/issues/2559). No release/R25 promotion.

## Validation
Before claiming *combined* certification require NEW **exact-head** hosted Actions `G1851_TERMINAL_OBSERVER_FAILURE_NONRETRYABLE_PASS`, `G21188...` primitive marker, G18.5.0/4.9/4.8/4.7, G21.187 real socket crash negative, **495/495** checked focused, Java11 major55 and full Gradle SUCCESS. Independent green ancestors do not certify this new combined tree. Chat 1 owns promotion. Never double-merge predecessor slices if this cumulative child is accepted.
