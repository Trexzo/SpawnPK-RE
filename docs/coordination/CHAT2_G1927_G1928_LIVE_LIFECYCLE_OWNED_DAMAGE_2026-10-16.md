# G19.2.7/28 — atomic immediate PvM damage admission for exact lifecycle registration

**Parent:** [G1925/26 #2787](https://github.com/Trexzo/SpawnPK-RE/pull/2787), exact `0a3d6579c3b6aabcf9d358a783b7956a7b2e3791` (631 focused; own hosted CI required), carrying the opaque `NpcLifecycleService.Snapshot.registrationIdentity` marker. Core G21.261 [#2785](https://github.com/Trexzo/SpawnPK-RE/pull/2785) independently hosted green 627/627.

## Source-level flaw

`LocalCanonicalNpcAttackHandler.handle` samples `NpcLifecycleService.Snapshot before` for an **alive** canonical NPC and, after `beforeResolution`, uses `NpcCombatResolutionService.resolveImmediateOwnedAdmitted`. The resolver validates exact player generation, World open, canonical `WorldNpc` and spatial range inside their existing ownership locks, but `NpcLifecycleService.applyDamageOwned` previously accepted the **current** entry for that EntityId regardless of whether it was the lifecycle seen by the handler. Thus unregister/register of the same `WorldNpc` during the pre-resolution seam could cause a stale client attack to mutate the HP of a **new** registration, producing packet-65 and cadence for the wrong lifecycle.

## Narrow production code correction

1. Add `NpcLifecycleService.applyDamageOwnedIfRegistered(EntityId,int,long,Object expectedRegistrationIdentity)` with a non-null expected identity. Reuse existing damage logic; verify entry's exact `registrationIdentity` **inside the same synchronized lifecycle-entry section before HP mutation**. Throw a dedicated typed `StaleLifecycleRegistrationException` when identity differs. Existing normal `applyDamageOwned` and `applyDamage` behavior unchanged.
2. Add a backward-compatible overload of `NpcCombatResolutionService.resolveImmediateOwnedAdmitted` accepting an optional expected registration identity. The existing overload delegates with null. Inside the existing World/player/canonical ownership sequence, call `applyDamageOwnedIfRegistered` only for callers supplying an expected identity; preserve legacy resolver callers.
3. The canonical NPC action handler passes `before.registrationIdentity` with the existing spatial admission callback. Map only the new typed exception to `Status.STALE_LIFECYCLE`, preserving `LIFECYCLE_MISSING` for missing entries. Post-hit snapshot validation additionally detects a lifecycle replacement before packet publication.

## Deterministic tests

- **G1927**: genuinely ISAAC-decoded current-client opcode72, exact current player, viewer scene, canonical `WorldNpc` and registered live target. Pre-resolution callback unregisters and re-registers its 20HP lifecycle with *identical public values*, before owned HP mutation. Must return `STALE_LIFECYCLE`, leave fresh HP=20, no accepted witness, packet-65, cadence or grant.
- **G1928**: same callback restarts another WorldNpc's lifecycle; the selected target registration is unchanged and hit10 succeeds with actual packet-65.

Two focused tests/JavaExec tasks; manifest **631→633** mechanically 633 unique. No change to original packet codecs, range formula, delay policy, G1/drops/spawner recipient, account RAW security, or durable ownership epochs. The identity check is atomic relative to `NpcLifecycleService` entry replacement/damage mutation for the selected immediate hit, **not** atomic with subsequent packet publication/terminal settlement, nor a general scene or World lease. Real SpawnPK original server hit/kill-credit policy remains **UNKNOWN** (#2558) and durable reward outbox ACK (#2587) remains open.

**Requires exact-head hosted 633/633 focused, Java11 major55, full Gradle, G1927/28 markers** before certification. Draft/unmerged, main untouched.
