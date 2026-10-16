# G19.4.3/44 — real Monster Spawner terminal settlement despite packet-65 failure

Parent: [Chat 2 G1941/42 #2804](https://github.com/Trexzo/SpawnPK-RE/pull/2804) at exact `c9e18daba74d68da0fa7b3b6f55be7113d8c4d3a`, containing cumulative Chat 1 G21.275 base (659 focused candidate). G1941/42 production correctly *attempts* terminalization after post-damage checked/unchecked mask failure; its own hosted CI result is independent of this new test branch.

## Why this tests a distinct boundary

G1939–42 used a synthetic finalizer callback that counted invocations. That does not prove actual `MonsterSpawnerPvmRuntime` composition, `NpcDropResolutionService`, exact owned NPC/lifecycle removal, the drop-ground settlement, or the player's pending ground-item presentation observer survive a failed packet to the attacker. A failed attacker writer is plausible precisely when a connection is closing, and the game cannot depend on a later NPC click as its only settlement trigger.

## Actual graph fixture

In the existing `LocalCanonicalNpcAttackPvmRuntimeBridgeTest`, add an opt-in package-local fixture **without changing existing tests**:

- isolated World + registered `WorldPlayer`, actual reconstructed NPC Attack definition, HOME scene and relay projection;
- real `RuntimeGraph` with 10 HP spawned Monster Spawner NPC, all combat, lifecycle, drop and settlement providers;
- exact ISAAC-decoded opcode72 click; one deliberately terminal `ServerPacketWriter` (checked `IOException`) or a real writer with an output sink throwing `IllegalStateException` (unchecked) on actual flush;
- genuine `world.finalizeMonsterSpawnerPvmIfOwned(npc)` callback, not a synthetic counter-only finalizer, with dead retry path retained.

Observe exact original presentation exception, callback count one, runtime FINALIZED, graph entry removed, world NPC and lifecycle removed, binder cleared, one drop-resolution invocation, exactly five 995 coins for the configured current recipient, one pending ground-item presentation, and no second loot after repeating terminal finalization.

## Dedicated gates

- **G1943**: checked packet writer IOException with full spawner/ground settlement.
- **G1944**: unchecked packet output IllegalStateException with the same graph outcome.

Manifest **659→661 unique** with two JavaExec regression tasks. Exact-head **661/661**, Java 11 bytecode major55 and full Gradle required before certification. Draft/unmerged; main unchanged.

## Authority limits

The fixture only checks the current CUSTOM_LOCALLAB recipient (the configured spawn recipient) and its single transaction within one still-running JVM. It **does not** prove original SpawnPK kill-credit attribution, cross-process crash durability, replay-safe observer or per-consumer progression acknowledgments, settlement under concurrent World close, or recovery after partial ground-item publication. Do not promote those unspecified policies from this test.
