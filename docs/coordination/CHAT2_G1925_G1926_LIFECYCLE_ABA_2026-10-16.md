# G19.2.5/26 — same-tick dead-NPC lifecycle registration ABA

Parent: [G21.261/G19.2.4 #2786](https://github.com/Trexzo/SpawnPK-RE/pull/2786), exact commit `6a220dd15a85c5a5e512e9ad1b769d9f9b76a7de` (629 focused, own hosted CI required). Its core [G21.261 #2785](https://github.com/Trexzo/SpawnPK-RE/pull/2785) is independently green 627/627.

## Concrete source gap
The dead-target retry branch in `LocalCanonicalNpcAttackHandler.handle` compares the previously observed `NpcLifecycleService.Snapshot` with a second snapshot taken after generation sampling, using `dead()`, `deathTick` and `maxHitpoints`. A lifecycle can be unregistered and re-registered on **the same exact canonical WorldNpc** and killed again at **the same tick** with identical HP, source authority and definition. The old snapshot can then pass those value-only comparisons and authorize a terminal retry for an entirely different lifecycle registration. A same-tick resurrection cannot be identified from those values.

## Narrow production change
- Add immutable, non-serialized `registrationIdentity` to each `NpcLifecycleService.Entry` (`new Object()`) and expose that same opaque object on every `Snapshot` of the entry. No global ID, serial format, enum, external packet, or persisted owner epoch.
- Dead-NPC `LocalCanonicalNpcAttackHandler` recheck refuses `STALE_LIFECYCLE` if the snapshot's exact `registrationIdentity` differs from the prior snapshot. Retains existing deathTick/maxHP checks and all other guards.
- No changes to damage semantics, canonical EntityId, SPAWN/65 codec, G1/drop recipient, account security, World close, or terminal callback contract.

## Deterministic tests
- G1925: authenticated current-client ISAAC opcode72 targets a canonical dead NPC. Generation callback removes its lifecycle then registers/kills that very same NPC at the same death tick (1919), same 20 HP, same source authority, same canonical object/viewer scene. Verify new snapshot matches all prior visible fields but differs by registration identity. The stale retry is denied with `STALE_LIFECYCLE`, no callback/hit/G1.
- G1926: repeat identical-valued same-tick restart on an **unrelated** NPC; selected current dead target's retry runs exactly once with no new grant.

Gradle manifest **629→631 mechanically distinct classes**, two targeted JavaExec regression tasks, expanded pre-existing G1919 fixture for same-value ABI/identity capture. Requires **exact-head hosted Java11 major55/full Gradle 631/631**, G1925/G1926 and G21260/G21261 markers. Not an atomic lock against lifecycle replacement after final read, not durable lifecycle ID, not original SpawnPK kill/loot policy, not a per-consumer ACK/outbox. [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558) and [#2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587) remain open. All draft/unmerged; main untouched.
