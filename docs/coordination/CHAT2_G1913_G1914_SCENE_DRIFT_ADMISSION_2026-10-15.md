# G19.1.3/14 — second scene-object admission check before canonical hit

Parent: cumulative [#2768](https://github.com/Trexzo/SpawnPK-RE/pull/2768) updated exact head `a6726638f97c23f0e66862de55b457847c4743c6` (611 focused, own exact-head CI required). Historical attribution question remains [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558).

## Narrow source gap
G1911/12 validates the requested viewer-local `NpcRegistry.scene(action.sceneIndex)` by Java object identity at handler **entry**. But after that first check and before `NpcCombatResolutionService.resolveImmediateOwnedAdmitted`, an injected pre-resolution hook, or an independent viewer scene transition, may remove the projection. Canonical HP could then mutate before `NpcRegistry.sendMaskLocal` refuses the detached object, causing side effects without corresponding packet-65 hit presentation.

## Change
Inside `LocalCanonicalNpcAttackHandler.handle`, **after** `beforeResolution.run(target,generation)` and immediately **before** damage resolution, check again `npcs.scene(action.sceneIndex)==clicked`, and return the existing `STALE_SCENE` zero-damage status if not. Do not suppress hook exceptions, change combat/kill/drop semantics, recreate views, or treat this as an atomic lease or a complete concurrency solution.

**G1913**: genuine ISAAC client opcode72 decoded for a valid active canonical NPC scene, then pre-resolution hook actually calls `retireMirroredNpcLocal(selected.sceneIndex)`. Verify post-hook `STALE_SCENE`, no canonical HP mutation, packet, cadence, or G1. This is a deterministic source-level time-of-check/time-of-use witness.

**G1914**: equivalent genuine attack where hook retires *another* viewer projection; selected view remains current, 10 damage lands and packet-65 mask is published, no reward.

Gradle focused manifest **611→613** unique entries, two targeted JavaExec tasks. Requires fresh exact-head hosted **613/613 focused, Java 11 major55 and full Gradle**, plus independent 611 parent verification. The underlying race may still occur after the second check because viewer registry projection mutation and damage do not share a complete atomic lease. Original SpawnPK client wire has no scene-generation token, no server reward policy established. All draft/unmerged.
