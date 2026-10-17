# G19.1.5–G19.1.8 — dead-NPC retry World lifetime and scene identity

**Parent:** certified [G21.256+G19.1.4 #2772](https://github.com/Trexzo/SpawnPK-RE/pull/2772), exact `8f7c1d390a4c6d46bd7ba545879936930a7d6f4c`, 614/614, Java 11 bytecode and full Gradle.

## Source gap

`LocalCanonicalNpcAttackHandler.handle` uses `World.withOpenPlayerOwnership` on the live NPC HP mutation path, but its **already dead** NPC branch invokes `retryDeadFinalization(target)` after checking player generation without checking `World.closed()`. A `World.close()` between the initial scene resolution and the dead-target terminal callback could therefore enter a mutating settlement/retry handler in a closed World. Further, an injected/supplied `LongSupplier generationSupplier` can retire its viewer's `NpcEntity` projection after the entry-time `NpcRegistry.scene` identity check; G1913/14's post-`beforeResolution` guard never executes for an already-dead NPC.

## Narrow G1915–18 local source change

- Add explicit `WORLD_CLOSED` zero-damage handler result before sampling the player generation for both paths when the World is already closed; after generation sampling, recheck World closed immediately before an already-dead NPC retry.
- In the already-dead branch, recheck `npcs.scene(action.sceneIndex)==clicked` **after** generation sampling, before retry. Return existing `STALE_SCENE` otherwise.
- Preserve exact current player/generation ownership, active-world dead retry callback, current live NPC attack damage, all current packet codecs, Monster Spawner recipient, G1/drop settlement, account core, LocalLab terminal diagnostics unchanged.

## Four focused tests

- **G1915:** actual `World.close()` occurs with a dead canonical NPC, a previously valid player, and genuinely ISAAC-decoded client opcode72 before handler call; callback must not run and no new hit/packet/credit may occur.
- **G1916:** identical *open* World has a real dead canonical NPC and valid current scene/session; exactly one expected retry callback, no new attack or reward.
- **G1917:** scene actually retired in `generationSupplier.getAsLong()` while target dead, with player session still current; handler must return `STALE_SCENE`, no retry.
- **G1918:** supplier retires *other* view, leaving selected scene current; exactly one legal dead retry with no added reward.

Focused manifest 614→**618 unique**, four JavaExec tasks. Hosted exact-head **618/618**, Java 11 major 55 and full Gradle required; no claim until logs verified. The two `world.closed()` reads are **not an atomic World close lease**, and this does not solve concurrent projection mutation after final read, original SpawnPK kill-credit policy ([#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558)), or durable outbox ([#2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587)). All changes remain LocalLab reconstruction, draft/unmerged and main untouched.
