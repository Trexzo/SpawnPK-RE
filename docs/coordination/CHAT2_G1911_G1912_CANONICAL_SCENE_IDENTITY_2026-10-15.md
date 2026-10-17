# G19.1.1–G19.1.2 — exact viewer scene object ownership for canonical NPC hit

**Parent:** cumulative G21.254 / G19.1.0 [#2764](https://github.com/Trexzo/SpawnPK-RE/pull/2764) exact `d0fb9624353c6a6080b2648abc0d31938318d9c6` (608 focused, pending own CI at branching). **Issue:** [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558), original kill-credit authority remains unknown.

## Source evidence and narrow change
The exact-client NPC option2 `opcode72` packet transports only a viewer-local `sceneIndex`. `NpcRegistry.scene(sceneIndex)` resolves the currently retained `NpcEntity` **object**; `retireMirroredNpcLocal` removes a mirrored view, and `spawnMirroredNpc` can later reuse the same integer scene slot. Existing `LocalCanonicalNpcAttackHandler.handle(action,clicked,writer)` independently validated `clicked.canonicalId()` against `WorldNpcRegistry`, but did **not** first establish that `clicked` was still exactly the viewer's current registered projection or that `action.sceneIndex==clicked.sceneIndex`.

Introduce one `STALE_SCENE` non-mutating outcome and check both `action.sceneIndex==clicked.sceneIndex` and `npcs.scene(action.sceneIndex)==clicked` before resolving canonical world NPC/damage/dead retry. Keep legacy dummy routing, all original packet codecs, range/cadence, combat damage, Monster Spawner recipient, G1/drop settlement, all account core and terminal observer semantics unchanged. This is LocalLab server-source hardening, not recovered original SpawnPK server policy.

### New deterministic gates
- **G1911**: actual ISAAC-framed client opcode72 decoded for real projected attack NPC; a forged NpcEntity with same definition, scene index and canonical ID is rejected before damage; an action index mismatch is rejected; the real current registry object still performs the normal 10-damage accepted hit with exact current attacker witness and packet-65 presentation.
- **G1912**: one mirrored projection is really retired from its viewer registry, then another NPC with a distinct canonical EntityId reuses the same numeric scene slot. A stale Java reference to old projection is rejected without damaging either NPC; the fresh projection's independent decoded opcode72 succeeds against only the new NPC. This **does not** claim that a replayed old *wire* packet can be distinguished once the index was reused; the protocol carries no generation token.

Gradle adds G1911/12, two focused JavaExec tasks, both manifest hardguards **608→610**; mechanically 610 distinct entries. Requires **exact-head hosted 610/610 focused, Java 11 bytecode major55, full Gradle**, dedicated G1911/G1912 markers, and parent 608 branch verified. Keep #2558 kill-credit and #2587 durable per-consumer ACK open; no main merge.
