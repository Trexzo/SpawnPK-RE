# Chat 2 → Chat 1 + Chat 3: G21.177 additive gameplay integration

Date: 2026-10-13. **Exact Chat 1 base:** [G21.177 core PR #2542](https://github.com/Trexzo/SpawnPK-RE/pull/2542), `dae72ba0408b4e6b0832cdc17f8b6e3d44796951`. **Chat 2 branch:** `chat2/g21177-gameplay-g1842-r5-monster-reconnect-cumulative`.

## Ownership and status

- Chat 1 owns G21 core World, player/session, persistence/recovery, releases, promotion and merges. This branch does **not** fix or claim to fix G21.177's observed stale reconnect after failed final World save. See [Chat 1 #2541](https://github.com/Trexzo/SpawnPK-RE/issues/2541).
- Chat 2 owns gameplay reconstructions, UI adaptation and regression tests. This **draft** integration branch is additive on Chat 1's G21.177 source, not an independently merged release.
- Chat 3 owns exact-v308/v309 client recovery in [SpawnPK-Client](https://github.com/Trexzo/SpawnPK-Client); outstanding R5 target selection is [client issue #1005](https://github.com/Trexzo/SpawnPK-Client/issues/1005).

## Integrated changes (source-proven only)

| Workstream | Files | Behavior / authority |
|---|---|---|
| G18.4 Easy PvM Daily Money Making | `LocalDailyMoneyMakingUiHandler`, `LocalSessionUiActionHandler`, `LocalSession`, `G184DailyMoneyMakingLiveTrackIntegrationTest` | Exact widget 55002, `CUSTOM_LOCALLAB` World-owned Easy PvM N/3, S2C126 target 26, G18.3 persisted difficulty restored after login; unconfigured Medium/Hard and Teleport fail-closed |
| G18.4.1 Track generation fence | `LocalLabDailyMoneyMakingRuntime`, `LocalDailyMoneyMakingUiHandler`, `G1841DailyMoneyMakingExactSessionFenceTest` | Prevent old-session same-username replacement from activating a different WorldPlayer; exact owner/generation/name checked under World mutation ownership |
| G18.4.2 difficulty-generation fence | `LocalDailyMoneyMakingUiHandler`, updated `G184DailyMoneyMakingLiveTrackIntegrationTest`, new `G1842DailyMoneyMakingDifficultyGenerationFenceTest` | Reject old UI selection with no snapshot-extension persistence, no UI packet, and no guessed server behavior; preserve unbound historical G18.2 fixture |
| R5 targetless loadout Save safety | `R5LoadoutEditorButtonPairNoTargetRegressionTest` only | C2S185 widget 33006/33009 plus bounded C2S103 `cld1/cld2` pairing; still `LOADOUT_EDITOR_TARGET_ADAPTER_UNPROVEN`, no semantic mutation, no original server target claim |
| Issue #1394 Monster Spawner retirement | Only `MonsterSpawnerPvmRuntimeTest.java` | Explicit last-NPC settlement retires disconnected owner session, but retains session for a newly connected same-name owner; no production code modified |

**No source changes to Chat 1's G21 core production classes** `World`, `WorldPulse`, `WorldPlayerPersistence`, `FilePlayerRepository`, `PlayerRegistry` or its real-socket regression.

## Focused test manifest and proof

- Exact G21.177 parent already has **477** unique focused tests, including `G21177CoreSocketFailedFinalSaveStaleReconnectIntegrationTest`.
- Chat 2 adds **four** distinct regression classes: `G184DailyMoneyMakingLiveTrackIntegrationTest`, `G1841DailyMoneyMakingExactSessionFenceTest`, `G1842DailyMoneyMakingDifficultyGenerationFenceTest`, `R5LoadoutEditorButtonPairNoTargetRegressionTest`. Monster Spawner extends an existing registered test. **Expected total = 481 unique tests**.
- Dedicated Gradle regression tasks retained for each of the four new tests. All Java must compile to Java 11 class major 55.
- The **current branch's exact-hosted Java11, 481-focused and full Gradle output must pass before certification**. Passing separate older branches does not certify this version.

## Historical exact-head evidence (not current-branch certification)

- [G18.4 / G18.3 integration #2528](https://github.com/Trexzo/SpawnPK-RE/pull/2528), [CI #38453791007](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38453791007), 473/473 hosted PASS.
- [R5 negative safety #2533](https://github.com/Trexzo/SpawnPK-RE/pull/2533), [CI #38457346387](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38457346387), 475/475 hosted PASS.
- [G18.4.1 exact Track generation #2539](https://github.com/Trexzo/SpawnPK-RE/pull/2539), [CI #38460367180](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38460367180), 478/478 hosted PASS.
- [G21.176 combined #2543](https://github.com/Trexzo/SpawnPK-RE/pull/2543), [CI #38463131880](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38463131880), 479/479 hosted PASS.
- [G18.4.2 #2544](https://github.com/Trexzo/SpawnPK-RE/pull/2544) and [#1394 #2545](https://github.com/Trexzo/SpawnPK-RE/pull/2545) are independent slices. Check their current exact-head CI before citing as certified. Two earlier G18.4.2 candidate builds initially failed and were corrected; never conflate those attempts with current success.

## Reviewer integration instructions

1. Review this branch against **Chat 1 G21.177 exact SHA**, not old main.
2. Check its own Github Actions exact head for all 481 PASS, Java major55 and complete Gradle build.
3. If Chat 1 adds more G21 tests while this branch is open, preserve all additions and recompute the manifest before any rebasing/merge.
4. Treat old Chat 2 PRs as ancestry; **do not merge them again on top of this additive cumulative change**.
5. Keep human v308 client-side acceptance and all `UNKNOWN_SERVER_AUTHORITY` original server rules outside hosted-Java tests. No mergers, issue closures or release promotion are implied by this document.

Coordination index: [Chat 1 integration issue #392](https://github.com/Trexzo/SpawnPK-RE/issues/392). Historical 242-PR /145-issue ancestry snapshot on [PR #2528](https://github.com/Trexzo/SpawnPK-RE/pull/2528) remains a point-in-time record, not a current count.
