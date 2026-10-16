# Chat 2 G19.6.1/62 — spawn-recipient identity survives same-name account replacement

**Exact parent:** [#2828](https://github.com/Trexzo/SpawnPK-RE/pull/2828) `79960adb2bc5a2174e2ccfdeffa61eb4c2036c70`, cumulative Chat1 G21.284 + Chat2 G1960 with 687 focused candidates. No G21 account/forensic/World ownership production code changed.

## Source-level problem

`MonsterSpawnerPvmRuntime` already stores `RecipientOrigin(player,generation,recipientRef)` at canonical NPC spawn and exposes `stillCurrentIn(World)`. But the installed `G1MonsterSpawnerPvmLoopService.onFinalized` credited by a NEW `world.players().byName(finalization.recipientRef)` followed by that *new* player's current generation. Default `LocalLabMonsterSpawnerProvisioning.creditTerminalProgression` similarly only checked terminal-time current username/generation. Opt-in `LocalLabTerminalStageIsolation` calculated `spawnOriginMatchedAtDispatch` but used it merely as an observational report value; stages still ran against current same-name recipient. A real NPC spawned before logout could therefore credit a different WorldPlayer registered under the same username.

This is strictly a LocalLab spawn-time identity-fencing correction. It does **not** identify the original SpawnPK kill-credit authority; existing configured recipient policy is unchanged.

## Narrow production changes

1. `G1MonsterSpawnerPvmLoopService.onFinalized` only attempts `records.recordIfCurrent` for the EXACT `terminal.recipientOrigin.player` and `.generation` when `recipientRef` matches and `stillCurrentIn(world)`. When spawner ownerRef equals old recipientRef, a replacement owner session cannot inherit the old NPC's automatic respawn ticket; unrelated genuinely separate owners preserve prior scheduling policy.
2. Installed **default** G4→G14→G15→G16→G18 delivery now carries `terminal.recipientOrigin` and rechecks `stillCurrentIn(world)` before EACH stage. No current/matching origin yields `SKIPPED_NO_CURRENT_RECIPIENT`. A mid-callback retirement preserves already-run stage evidence and marks only remaining ones skipped. The old direct public `creditTerminalProgression(World,...)` overload retains its existing caller-validated contract; only installed canonical terminal dispatch is narrowed.
3. **Opt-in** `LocalLabTerminalStageIsolation` now acts on its already-captured `spawnOriginMatchedAtDispatch` witness and rechecks origin for each stage. No stage can mutate a newly registered same-name recipient when original origin is stale. Both normal and experimental stage order, exception containment and diagnostics remain unchanged otherwise.

## Real execution gates

**G1961** constructs the actual installed default graph, spawns a canonical NPC for WorldPlayer A, registers the exact spawn player/generation, unregisters A and registers **different WorldPlayer B with the SAME username**, activates healthy G15/G16/G18 objectives on B, then performs real lethal NPC damage and ground settlement. Assert B gets ZERO G1 receipt, G15/G16/G18 objective progress remains zero, all five default stages are `SKIPPED_NO_CURRENT_RECIPIENT`, configured owner-name 995 ground settlement still occurs once, and terminal no-retry/identity evidence persists.

**G1962** repeats with explicitly injected isolated-stage dispatcher. Assert the existing diagnostic's `spawnOriginMatchedAtDispatch=false`, all five stages `SKIPPED_NO_CURRENT_RECIPIENT`, and no replacement G1 or progression credit. Both tests use genuine `MonsterSpawnerPvmRuntime`, lifecycle and World registration, not constructed terminal-result mocks.

Manifest **687→689 unique**, 2 JavaExec gates. Mandatory exact-head **689/689**, G1961/62 markers, Java11 class major55/full Gradle to certify. Parent PR and donor builds do not substitute. Draft, `main` untouched.

## Boundaries

This prevents a known terminal-time **new WorldPlayer under same name** misattribution. It does not make progression credits transactionally idempotent under crash, stop concurrent World close after a previously admitted stage, prove user-account persistent ownership or original SpawnPK recipient policy, or support safe post-crash replay. Real owner scheduling when owner != recipient retains existing semantics, separately needing provenance research. Issue [#2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587) remains open for durable per-consumer apply/ACK, and [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558) remains open for original kill-credit attribution.
