# G19.0.9–G19.1.0: reject stale dead-NPC retry before settlement callback

**Scope:** source-side LocalLab authorization ordering; no original SpawnPK server attribution inference. Parent is cumulative [#2760](https://github.com/Trexzo/SpawnPK-RE/pull/2760), exact `4c110593ab4872cb1bb2e0cd72b28785a3aa5ce4` (605 focused). [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558) remains open.

## Observed source defect
`LocalCanonicalNpcAttackHandler.handle` checked `NpcLifecycleService.Snapshot.dead()` and invoked `retryDeadFinalization(target)` **before** reading/validating `generationSupplier` with `world.players().owns(player,generation)`. A stale handler from an unregistered session could enter a dead-NPC retry that potentially triggers settlement/observer mutations, without first proving it was the current World session. Previous G1904 checks only the *alive* NPC route.

## Narrow hardening
Move the existing exact player-generation check directly ahead of the dead-NPC branch. Preserve the original `TARGET_DEAD` and dead-finalization callback for valid current sessions. No changes to damage calculation, client codec, NPC registration, spawner recipient, G1/drop settlement, or terminal dispatcher.

- **G1909**: a genuine ISAAC decoded opcode72 request associated with the unregistered WorldPlayer A, after same-name replacement B and canonical NPC death, is `STALE_PLAYER` without invoking its dead-finalization-retry callback or emitting packet/reward.
- **G1910**: B's separate legitimate opcode72 request for the same already-dead NPC is `TARGET_DEAD` and invokes exactly one permitted retry callback, but creates no new hit or reward.

This tests **entry-time authorization order**, not concurrent logout synchronization, packet-to-account cryptographic identity, durable per-consumer reward ACK, or historical kill attribution. Fresh exact-head Java 11 and Gradle verification required: **607 distinct focused classes**, `g1909StaleDeadNpcRetrySessionFenceRegression` and `g1910CurrentDeadNpcRetryPreservedRegression`. Draft PR; no main merge.
