# Chat 2 G18.4.5 — immutable Monster Spawner recipient witness at spawn

Date: 2026-10-14.  
Parent: [Chat 2 G21.179 integration PR #2555](https://github.com/Trexzo/SpawnPK-RE/pull/2555), exact `266be682738864cb29a841f3eaaff15b36807815`, [Actions #38533900692](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38533900692) completed **SUCCESS**, 485/485 focused, Java 11 bytecode major 55, full Gradle success.  
Related gameplay boundary: [issue #2547](https://github.com/Trexzo/SpawnPK-RE/issues/2547). Chat 1 owns core World/session/persistence and final merge.

## Problem observed in source

Before this change, `MonsterSpawnerPvmRuntime.Entry` retained only `ownerRef` and `recipientRef` **strings**, and successful `FinalizeResult` returned `runtime=null`. A terminal observer had no reliable object/generation for the account occupying `recipientRef` at the NPC's **spawn**. The separately introduced G18.4.4 opt-in exact-origin DMM credit method could not be wired safely using a terminal-time `players().byName` lookup.

## Added witness, limited authority

A new immutable `MonsterSpawnerPvmRuntime.RecipientOrigin` contains the original `WorldPlayer` reference, registered generation, and recipient name. It is captured **only in** `MonsterSpawnerNpcLifecycleBindingService.RegistrationCommitAction` invoked by the canonical `MonsterSpawnerCombatBindingService.spawnAndBindComposedExpected`, whose commit action runs inside `World.withOpenLifecycleOwnership`. Player register/unregister uses the same lifecycle lock, so a concurrent replacement cannot slip between recipient lookup and the spawn commit. When `recipientRef` has no currently registered `WorldPlayer`, origin is **null**, not inferred.

The witness remains on the runtime `Entry` and `Snapshot`. `FinalizeResult.recipientOrigin` carries it through FINALIZATION_PENDING / SETTLEMENT_PENDING and **successful FINALIZED, even when runtime Snapshot is null and the entry has been removed**. It is never recomputed during retry. `RecipientOrigin.stillCurrentIn(world)` is observational only; no reward admission may rely on an unowned check.

## Regression

The existing permanent `MonsterSpawnerPvmRuntimeTest` (already covered by `monsterSpawnerPvmRuntimeRegression` and the full Gradle build) now exercises:

1. Register `killer` as player object A and record generation GA.
2. Spawn NPC; assert `SpawnResult.runtime.recipientOrigin` points exactly to A/GA.
3. Unregister A, register different player B under same `killer` username; origin remains A/GA, reports not-current.
4. Kill NPC; force settlement-pending with ground-item merge quantity overflow; confirm pending result and persistent entry still reference the **same** origin; no terminal observer yet.
5. Remove blocker and retry; FINALIZED result and observer reference **the exact original A/GA object**, even though B now occupies the username, with drops resolved once.
6. A separate fixture uses an unregistered `absent-recipient` and confirms origin stays null.

Expected permanent log marker **after hosted execution only**: `G1845_MONSTER_SPAWNER_RECIPIENT_ORIGIN_WITNESS_PASS`. The focused count stays **485** because the existing covered `MonsterSpawnerPvmRuntimeTest` was extended, not a new test class inserted into the manifest.

## Explicit nonclaims / next boundary

- Does **not** prove that recipient at *NPC spawn* is the correct owner of a *later kill*. This is an original spawn-time identity witness only; canonical combat/kill attribution must still be recovered.
- Does **not** reconnect the production Monster Spawner terminal observer to the G18.4.4 DMM credit overload. Existing `LocalLabMonsterSpawnerProvisioning.creditTerminalProgression` and all five G4/G14/G15/G16/G18 progression consumers are unchanged.
- Does **not** change drops, NPC lifecycle, settlement retry, combat, packet protocol, session login, account storage, custom objective lifetime or original SpawnPK server policy.
- Must not assume that a null witness can be replaced by a terminal-time username lookup.
- This is an additive Chat 2 draft. Before any live mutation, Chat 1 + Chat 2 must agree whether original *spawn owner*, damage contributor, last hitter or another actor is eligible for reward attribution. Chat 3's recovered client can support UI evidence but not original server-only reward policy.

## Exact-head release gate

Verify on the exact PR SHA using `monsterSpawnerPvmRuntimeRegression`, current-train 485 focused, Java 11 bytecode, and full Gradle. Keep draft/unmerged until hosted green; Chat 1 has final acceptance.
