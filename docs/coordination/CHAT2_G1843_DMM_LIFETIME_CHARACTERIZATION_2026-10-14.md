# Chat 2 G18.4.3 — Daily Money Making per-World lifetime characterization

Date: 2026-10-14  
Issue: [#2547](https://github.com/Trexzo/SpawnPK-RE/issues/2547)  
Parent: [Chat 2 G21.178 PR #2550](https://github.com/Trexzo/SpawnPK-RE/pull/2550), exact parent `9b7bc05a44e84bf6b0f990c3a213e8df3c5b1315`  
Ownership: **Chat 2 gameplay**, proposed on its own additive draft branch. Chat 1 retains core World/persistence/release ownership and merge control.

## Why this exists

The G18.4.1 and G18.4.2 UI guard checks are *session generation-bound* but `LocalLabDailyMoneyMakingRuntime.byPlayer` is *normalized-username keyed inside the World instance*. `World.unregisterPlayer` does not remove its entry. Neither the exact client nor the reconstructed server provides verified **original SpawnPK** reset/reconnect policy.

The existing implementation has a concrete observable lifetime that can be tested **without changing gameplay policy**. This contribution records that behavior and must not be read as an endorsement of any reset rule.

## Reproducible observations (characterization)

New main-method regression: `server/src/spk/local/G1843DailyMoneyMakingWorldLifetimeCharacterizationTest.java`.

1. Create isolated World A and register an exact WorldPlayer/username. Before activation, no activity exists.
2. Activate Easy PvM explicitly for that owner/generation, credit two events at the already-existing `recordMonsterSpawnerFinalization` semantic seam, verify 2/3 and incomplete.
3. Unregister that player. The World-owned runtime retains **one username-keyed entry**; the old owner/generation may not activate it.
4. Register a different WorldPlayer under the same username **in the same World**. Its snapshot sees 2/3; fresh activation is an idempotent reuse rather than a new objective. It can advance the reused objective to 3/3, while the old owner/generation remains rejected.
5. Fully close World A and construct isolated World B. The new runtime has no objective. Explicit fresh activation starts 0/3. This is **in-memory behavior**, not an account persistence migration test.

The check proves neither concurrent in-flight event attribution nor original reset cadence. In particular, `recordMonsterSpawnerFinalization(String playerRef,...)` does not accept the originating WorldPlayer/generation; before granting any broader authority, Chat 1 and Chat 2 should decide whether a late event from an old session can resolve under a new owner. Do not silently assume it is correctly attributed.

## Source touched and scope

- New characterization test only (no production behavior change).
- `server/build.gradle`: new dedicated `g1843DailyMoneyMakingWorldLifetimeCharacterizationRegression` task, test added to current focused manifest, count 482 → **483**.
- No modifications to `World`, `WorldPlayer`, `LocalSession`, runtime activity logic, Monster Spawner production code, client or save schemas.
- No automatic reset, reward, rollover, teleport, or original SpawnPK behavior asserted.

## Acceptance and coordination

Run on this exact proposed head:

```sh
./gradlew :server:g1843DailyMoneyMakingWorldLifetimeCharacterizationRegression :server:currentTrainFocusedRegression :server:build
```

Expected dedicated marker **only after execution**:
`G1843_DMM_WORLD_LIFETIME_CHARACTERIZATION_PASS`, and focused `GRADLE_CURRENT_TRAIN_FOCUSED_PASS count=483 bytecodeMajor=55`. Full Gradle and Java11 outcomes must be confirmed from Actions; the presence of test source is not proof of a green run.

**Chat 1:** Decide whether this explicitly CUSTOM_LOCALLAB objective should live per player-generation, per World+username, or persist by account. Align with final World-save and reconnect semantics; defer policy mutation until chosen.  
**Chat 2:** After policy agreement, add owner/generation attribution to late credit if needed; write dedicated concurrency and terminal-spawn tests instead of inferring correctness from this characterization.  
**Chat 3:** Do not infer original server policy from pinned v308/v309 UI. Exact client evidence can establish UI transport, not backend credit lifetime.

This branch is draft/unmerged. It should be reconciled into the canonical G21 frontier only by Chat 1 after exact-head CI and policy review.
