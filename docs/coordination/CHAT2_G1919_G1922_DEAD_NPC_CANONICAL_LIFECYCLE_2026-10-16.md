# G19.1.9–G19.2.2 — dead NPC canonical and lifecycle revalidation

**Parent:** [G1915–18 #2775](https://github.com/Trexzo/SpawnPK-RE/pull/2775) exact `45f474748cfdf67a685ded86fc9ba566291cf1c9` (618 focused, hosted verification required). Its inherited parent [#2772](https://github.com/Trexzo/SpawnPK-RE/pull/2772) at `8f7c1d390a4c6d46bd7ba545879936930a7d6f4c` is independently green 614/614. Original policy issue [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558) stays open.

## Source gap
The dead-NPC retry path resolves the exact `WorldNpc` and `NpcLifecycleService.Snapshot` BEFORE sampling `generationSupplier`; unlike immediate live-hit resolver, it did not recheck these canonical owners after this callback and before `retryDeadFinalization(target)`. Even if the viewer's scene `NpcEntity`, player, and World stay valid, removing the **WorldNpc** or its **lifecycle entry** in this window could send a stale target to an injected terminal retry callback.

## Narrow source change
Only in the already-dead target branch, directly before retry: verify `world.npcs().byId(canonicalId)==target` and reject with existing `STALE_CANONICAL` if not; independently verify `world.npcLifecycle().get(canonicalId)` exists and is still a dead snapshot with matching deathTick/maxHP, otherwise return `LIFECYCLE_MISSING` or new `STALE_LIFECYCLE` respectively. All of this follows G1915–18 World/scene checks and current player-generation ownership. No changes to world/lifecycle APIs, original packet opcode72, combat damage, G1, Monster Spawner drop/recipient or account RAW security.

## Four focused regressions
- G1919: genuine decoded opcode72, selected dead `WorldNpc` actually removed inside generation supplier; current viewer scene remains, yet terminal retry callback must NOT run.
- G1920: genuine decoded opcode72, other canonical `WorldNpc` removed inside supplier, selected dead NPC remains; terminal retry callback runs once.
- G1921: selected dead target lifecycle unregistered inside supplier; `LIFECYCLE_MISSING`, no retry.
- G1922: unrelated NPC lifecycle unregistered inside supplier; existing selected dead target still retries once.

Focused Gradle manifest 618→**622 unique classes**, four JavaExec tasks. Must verify own exact-head **622/622**, Java 11 major55, full Gradle and G1919–22 markers. This is observational LocalLab defensive behavior; does not establish an atomic NPC-lifecycle lease (a concurrent removal after these checks remains possible), original SpawnPK hit/loot authority, or durable progression consumer ACK ([#2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587)). Draft; main untouched.
