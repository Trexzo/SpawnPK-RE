# Chat 2 G18.4.4 — exact-origin Daily Money Making credit seam

Date: 2026-10-14  
Target: [Chat 2 G18.4.3 #2551](https://github.com/Trexzo/SpawnPK-RE/pull/2551), certified `01e27dcb9b3de65379aacc3f4255fb80d415bf87` by [run #38529095748](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38529095748).  
Issue: [#2547](https://github.com/Trexzo/SpawnPK-RE/issues/2547).  
Owner: Chat 2 gameplay. Chat 1 retains World/persistence/promotion authority.

## Observed unsafe boundary, not yet a proven live exploit

`LocalLabMonsterSpawnerProvisioning.creditTerminalProgression(World,String,int,Tile,long)` looks up the CURRENT WorldPlayer by `recipientRef`, obtains its current generation, and calls five gameplay progression runtimes by username. `MonsterSpawnerPvmRuntime.Entry` and `FinalizeResult` carry `ownerRef` / `recipientRef` strings and canonical NPC/finalization data, but **do not carry the original recipient WorldPlayer plus generation**.

Consequently, a true old-session kill that settles late could be evaluated against a replacement's *current* username identity, rather than its original event recipient. That is a source-based attribution **hazard**; this branch does not claim to have proven a real delayed callback occurred. The generation captured with a terminal-time `byName` lookup **is not an origin generation**.

## Narrow change

The G18 Daily Money Making runtime now offers a separate **opt-in** method:

`recordMonsterSpawnerFinalizationIfCurrent(String playerRef, WorldPlayer expectedOwner, long expectedGeneration, int definitionId, long deathTick)`

It checks the provided WorldPlayer and generation inside `World.withOpenPlayerMutationOwnershipIfCurrent`, plus exact canonical username object identity, before calling the established semantic credit path under reentrant lifecycle/owner locks. On a mismatch it returns nonmutating `ProgressResult(false,eligible,false,false,null)`. Its preexisting username-only method is unchanged. All other gameplay runtimes remain unchanged.

Crucially, **production Monster Spawner callbacks are NOT wired to this API**. Wiring terminal-time `world.players().byName(name)` into it would be falsely described as an origin fence and must not be done.

## Permanent test

`G1844DailyMoneyMakingExactOriginCreditTest` verifies an original owner can earn 1/3, activity survives its disconnect, and after same-World username replacement:

- old owner + old generation refused without progress change;
- current owner + wrong generation refused;
- unrelated owner of a different username refused;
- mismatched username and owner refused;
- eligible definition with exact current replacement owner credits 1/3→2/3;
- wrong definition does not credit;
- closed World refuses new credit.

New Gradle task: `g1844DailyMoneyMakingExactOriginCreditRegression`. Checked current train 483→**484**. CI expected marker `G1844_DMM_EXACT_ORIGIN_CREDIT_PASS` and `GRADLE_CURRENT_TRAIN_FOCUSED_PASS count=484 bytecodeMajor=55` only if exact-head hosted run proves them.

## Remaining cross-chat actions

**Chat 1:** Evaluate World/PvM late settlement semantics and lock order. Do not infer durable credit state from World runtime. Only Chat 1 should integrate changes to core lifetime and persistence.

**Chat 2:** The actual missing task is to capture immutable recipient session identity **at trusted spawn/authorization time** and transport it through canonical NPC binding, finalization, settlement retry, and terminal observer without impacting existing drop/reward semantics. Then wire that verified identity to the opt-in API and add synthetic old-generation settlement race tests. Do not retrofit a terminal-time lookup and claim success. Other G4/G14/G15/G16 progression consumers also need independent review before claiming an end-to-end fix.

**Chat 3:** Continue exact-v308/v309 client recovery; client UI can establish intent but not original server-only credit-lifetime rules.

This branch provides only an opt-in safety primitive plus tests, not a complete original SpawnPK server reconstruction or a full inter-generation fix. Draft/unmerged pending exact-head Actions and Chat 1 review.
