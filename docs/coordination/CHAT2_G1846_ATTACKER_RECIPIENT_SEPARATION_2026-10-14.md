# Chat 2 G18.4.6 — canonical lethal attacker versus configured spawn recipient

Date: 2026-10-14.  
Parent: [G18.4.5 #2556](https://github.com/Trexzo/SpawnPK-RE/pull/2556) exact `ffcdbeda2f2b941a23c1e8509891997d7fd5feb2`, [Actions #38536560785](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38536560785) **SUCCESS** (485 focused, Java11 major 55, full Gradle).  
Gap owner: [Chat 2 #2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558).

## Source-backed distinction

`LocalCanonicalNpcAttackHandler` is bound to an **exact** `WorldPlayer` and an explicit generation supplier. Its `NpcCombatResolutionService.resolveImmediateOwnedAdmitted` verifies the live attack admission and commits canonical NPC HP mutation, then invokes a target-only `LethalFinalizer(WorldNpc)` after publishing hit presentation.

That finalizer does not currently convey a proven attacker generation into `MonsterSpawnerNpcDeathFinalizationService`. Instead `MonsterSpawnerPvmRuntime.Entry` supplies `recipientRef` fixed at spawn to `finalizer.finalizeDead(entry.ownerRef, entry.npc, entry.recipientRef)`. The successful terminal `NpcDropResolutionService.DeathContext` keeps that configured recipient string. G18.4.5 additionally carries the WorldPlayer+generation that occupied that recipient name **at spawn**, not the actual attacker.

## Permanent characterization

Extend the already-covered `LocalCanonicalNpcAttackPvmRuntimeBridgeTest`:

- Register player A named `spawn-recipient`, player B named `lethal-attacker`.
- Spawn an exact canonical NPC with `recipientRef=A`, capture A/generation witness in G18.4.5.
- Bind the actual NPC attack handler to **B**; perform a real synthetic exact option-2 NPC ATTACK against a scene-projected canonical NPC.
- Check lethal hit, terminal result, original A witness retained, **finalization recipientRef=A**, and the current `CUSTOM_LOCALLAB` loot settlement creates owner-scoped loot for A rather than B.
- No speculative server-only policy changes, no damage rule substitution, no production code.
- Emit `G1846_CANONICAL_ATTACKER_VS_SPAWN_RECIPIENT_PASS` if the hosted test executes.

The checked current-train manifest stays **485** since this extends an existing covered integration test. This is a source/test proof of the **current mismatch**, not a fix or a claim about original SpawnPK reward allocation.

## Remaining authority work

1. Original SpawnPK server rule for kill credit / reward recipient remains `UNKNOWN_SERVER_AUTHORITY`. Any proposed last-hit, top-damage, spawn-owner or fallback rule must be specifically proven or explicitly named `CUSTOM_LOCALLAB`.
2. To use `G18.4.4.recordMonsterSpawnerFinalizationIfCurrent` safely, exact *combat* generation must be carried through the lethal event and settlement retry, **not inferred from** `recipientRef` or a terminal-time `byName`.
3. Keep G4/G14/G15/G16/G18 progression consumers unchanged until actor policy and life-cycle consistency are demonstrated together.
4. Chat 1 owns core World/session, G21.180–G21.182 lease/failed-save work, cross-World admission, and merge promotion; Chat 2 stays on draft gameplay branch. Chat 3 client recovery remains distinct.

## Hosted gate

Before certification require exact PR head Java11 major55, existing `localCanonicalNpcAttackPvmRuntimeBridgeRegression` or its corresponding composed task, 485/485 focused, and full Gradle success. Historical recovery and actual gameplay parity claims are **not** supported.
