# Chat 1 ↔ Chat 2: shared SpawnPK-RE handoff (2026-10-13)

This file is an **in-repository coordination handoff**. It is not a proof that unmerged work is in `main`. Read it directly in GitHub; no chat copy/paste is required. Use the exact branch/SHA and CI URL below, rather than assuming the latest moving branch is stable.

## Repository and ownership

- **Both Chat 1 and Chat 2 work in [Trexzo/SpawnPK-RE](https://github.com/Trexzo/SpawnPK-RE).** Chat 3 independently owns [Trexzo/SpawnPK-Client](https://github.com/Trexzo/SpawnPK-Client).
- Chat 1: core World/WorldPulse, sessions, persistence, atomicity, integration/release authority, canonical branch promotion.
- Chat 2: reconstructed gameplay, content/UI adapters, client-visible protocol semantics, NPC/monster/game mechanics and their focused tests.
- Chat 1 is the sole integration/merge owner; Chat 2 creates draft PRs on independently named branches and documents dependencies.
- Preserve `EXACT_CURRENT_CLIENT` vs `CUSTOM_LOCALLAB` vs `UNKNOWN_SERVER_AUTHORITY`. Never claim recovered original server-only behavior without independent evidence.
- Never copy external decompiled Java or original JAR into public Git by default. The external `SpawnPk-Decompiled-main (3).zip` has useful independently reconstructed server modules but **is not proven original SpawnPK server source**.

## Current Chat 2 implementation

### Superseded historical branch — do NOT cherry-pick wholesale

[Draft PR #2525](https://github.com/Trexzo/SpawnPK-RE/pull/2525): G18.4 Daily Money Making Track, head
`c5c8d30ad7b5ca577ef89f0d356c20fb8c2d054e`, based on G18.3 runtime-only [PR #2159](https://github.com/Trexzo/SpawnPK-RE/pull/2159) at `583fefc089b73b9f353845cc1cd3bb5ee7ab0310`.

- Source/test on this earlier branch passed [Actions #38436728350](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38436728350): G184 exact-wire regression, 296/296 focused, Java 11 class major 55, Gradle success.
- **Not integration-ready as-is.** Later cumulative gameplay adds G18.3 *difficulty persistence*, missing from that older base. Its handler should not replace the newer persistent handler. Historical PR remains useful as a certified isolated research slice.

### Preferred integration successor — preserves both G18.3 variants

Branch `chat2/g184-integrated-g21172-with-shared-handoff` forked from exact Chat 1 cumulative G21.172 source:
`9ac2a8dfc8e024f9ef63d9a23520aa2bb29d6b9f` ([PR #2524](https://github.com/Trexzo/SpawnPK-RE/pull/2524)).

Changes are narrow and deliberately preserve the current cumulative difficulty-persistence code:

1. `server/src/spk/local/LocalDailyMoneyMakingUiHandler.java`: retain G18.3 snapshot-extension persistence and fail-closed malformed-persistence gate; add exact Track 55002 -> `world.localLabDailyMoneyMaking().activateEasy(player)`, one configured CUSTOM_LOCALLAB Easy PvM activity; S2C126 target 26 `LocalLab Easy PvM: N/3`; stale WorldPlayer generation rejection. Medium/Hard and Teleport 55012 remain fail-closed.
2. `server/src/spk/local/LocalSessionUiActionHandler.java`: replace pre-login handler **once** with an exact-session handler.
3. `server/src/spk/local/LocalSession.java`: install bound handler **after** `playerInitializer.initialize(...)` so persisted selected difficulty is actually restored from account snapshot extensions before the Track adapter is constructed.
4. `server/src/spk/local/G184DailyMoneyMakingLiveTrackIntegrationTest.java`: exact ISAAC wire (S2C126 targets 36+26), 0/3 and 2/3 refresh, idempotence, stale login, Medium/Hard/Teleport/no-selection fail-closed and G18.3 persisted Medium restore.
5. `server/build.gradle`: adds dedicated `g184DailyMoneyMakingLiveTrackRegression`, increments focused count **472 → 473**.
6. This coordination document.

**Validation rule:** do not call the newer successor certified until its own exact-head GitHub Actions run succeeds. The older #2525 CI result is not transferable. Reconcile any new Chat 1 commits after G21.172 before integration; do not force-push or silently merge into `main`.

## Review of stale-open issues on the current cumulative source

The following issue bodies were checked against **source on** G21.172 `9ac2a8df...`. They are implementation-status candidates, **not yet closed**:

| Issue | Source-backed finding | Recommendation |
|---|---|---|
| [#1397 Monster Spawner settlement retry](https://github.com/Trexzo/SpawnPK-RE/issues/1397) | `MonsterSpawnerPvmRuntime.retryPendingSettlementsOnce()` snapshots pending entries, uses `settlePending(entry,true)`, World's `retryMonsterSpawnerPendingSettlements()` is called by `WorldPulse`. `MonsterSpawnerPvmRuntimeTest.settlementPendingRetry()` explicitly covers automatic pulse retry, exact-once drops and no-op subsequent tick. | Confirm latest branch's CI authority, then mark issue resolved; do not reimplement. |
| [#1586 Remote pet mirror add/mask atomicity](https://github.com/Trexzo/SpawnPK-RE/issues/1586) | `SharedNpcWorldRelay` tracks deferred `pendingMirrorMasks`, uses recoverable packet publication, stable mirror scene and FIFO drain. `RemotePetMirrorMaskRetryTest.assertPostAddMaskFailureDoesNotDuplicateMirror()` forces post-add mask failure, repeated retry and exact bytes. | Verify latest CI and only then close; do not duplicate. |
| [#1830 Region-stream combat tick admission](https://github.com/Trexzo/SpawnPK-RE/issues/1830) | `LocalWorldTickCoordinator` stages `deferredRegionLegacyTickIncrement` while a writer batch is active; `commitRegionStreamBatch()` increments `legacyTickCount` only on commit and `abortRegionStreamBatch()` clears deferred increment. | Review corresponding integration regression and latest CI; candidate for closure, not new implementation. |

These cases demonstrate why open issue count alone overstates missing functional work. **No bulk closing without latest cumulative evidence.**

## All-open GitHub inventory and integration risks

Full GitHub metadata inventory at this review: **144 open issues and 241 open PRs, all 241 Draft**, including Chat 2's initial #2525. Most PRs are one long stacked dependency graph, not 241 independent candidates. Three top-level roots were observed with seven exposed leaves and forks at G18.3 and G21.47–48.

- **Main at review:** `3e842a32da1cf5d05f4d1f447c6105388fd2130a`, older than the current cumulative gameplay.
- **Frozen R25 main-target pointer:** [PR #1847](https://github.com/Trexzo/SpawnPK-RE/pull/1847), exact `e5a88b4d6341f0a759036788ea4fd1d2d8b80998`, 1,279 commits ahead of main, 0 behind. Its private release pass does not replace outstanding human visual/Walk-here acceptance.
- **G21.172 current cumulative at review:** [PR #2524](https://github.com/Trexzo/SpawnPK-RE/pull/2524), exact `9ac2a8dfc8e024f9ef63d9a23520aa2bb29d6b9f`, 2,551 commits ahead of main, 0 behind (comparison may cap displayed changed-file list).
- **Divergent persistence:** [#2274](https://github.com/Trexzo/SpawnPK-RE/issues/2274) documents competing G21.47–48 branches; reconcile evidence and ancestry before deletion.
- **G18.3 fork:** difficulty persistence vs World-owned Easy PvM runtime. Current G21.172 cumulative contains *both*, and our preferred G18.4 successor is authored against this combined source.
- Human/runtime acceptance blockers: [#9 custom asset/model visual pipeline](https://github.com/Trexzo/SpawnPK-RE/issues/9), [#1106 Walk-here HOME](https://github.com/Trexzo/SpawnPK-RE/issues/1106). These cannot be closed solely from hosted JVM tests.
- Existing [#392 Chat 1 integration/release queue](https://github.com/Trexzo/SpawnPK-RE/issues/392) is the cross-chat coordination point.

Recommended triage:
1. Identify one canonical cumulative **exact SHA**, distinguish CI-certified from merely created branch.
2. Audit open issue `required behavior` against that source with matching tests before labeling missing/solved.
3. Identify PR ancestry/forks and exact changed files, then mark each `active head / integrated ancestor / competing alternative / genuinely orphaned`.
4. Verify external visual/private client acceptance separately.
5. Close superseded issues and PRs with explicit successor links, never mechanically from counts.
6. Promote one reviewed cumulative target, not hundreds of independent merges.

## What Chat 1 should do next

- Read this file and the new Chat 2 draft PR, review the preserving-persistence integration approach, and compare it to any new G21 successor branch.
- Do **not** cherry-pick the older #2525 handler over the current G18.3 persistence code.
- After new CI passes, consider this G18.4 branch a candidate for integration into the canonical gameplay sequence; no unilateral merge from Chat 2.
- Review #1397/#1586/#1830 as apparently implemented-but-open; tag verified dispositions in GitHub.
- Keep new server-only game rules explicitly `CUSTOM_LOCALLAB` or `UNKNOWN_SERVER_AUTHORITY` until sourced.

This handoff is a GitHub document in a Chat 2 branch, not a copy/paste request.