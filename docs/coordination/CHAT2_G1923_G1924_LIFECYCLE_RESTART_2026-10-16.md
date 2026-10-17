# G19.2.3/24 — stale death observation after same-canonical lifecycle restart

**Parent:** [G1919–22 #2780](https://github.com/Trexzo/SpawnPK-RE/pull/2780), exact `edd4f76628306379640f7a7d17baea92fef9ee77` (622 focused; own hosted CI required). The previous source patch introduced `LocalCanonicalNpcAttackHandler.Status.STALE_LIFECYCLE` when post-generation NPC lifecycle is no longer dead or deathTick/maxHP changed, but G1919–22 only exercised complete lifecycle removal, not a live replacement.

## Focused extension

Reuse real World/NpcRegistry/canonical target and genuine ISAAC opcode72 fixture from G1919, extending the injected generation-supplier mutation modes:

- **G1923** unregisters the same *dead* canonical `WorldNpc` lifecycle and registers a new 20HP **ALIVE** `NpcLifecycleService` entry on the same exact canonical `WorldNpc`. The initial dead snapshot is stale; `LocalCanonicalNpcAttackHandler` must return `STALE_LIFECYCLE` and not invoke terminal retry or grant rewards.
- **G1924** unregisters/re-registers the unrelated NPC lifecycle while selected dead target's current lifecycle remains unchanged. Exact current selected opcode72 must still produce `TARGET_DEAD` and invoke terminal retry exactly once, without new hit/reward.

No production code changes, account security code changes, network codec changes or kill-credit policy claims. Two Java focused tests + two Gradle JavaExec targets bring **622→624 unique focused**. Requires own exact-head hosted Java 11 major55/full Gradle **624/624**, two markers, and parent 622 independent verification. Not a durable lifecycle lease or original SpawnPK server proof, no persistent grant ACK. Draft/unmerged, main unchanged.
