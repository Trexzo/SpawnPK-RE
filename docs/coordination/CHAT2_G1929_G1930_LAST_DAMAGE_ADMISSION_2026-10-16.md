# G19.2.9/30 — last-window lifecycle replacement in immediate damage admission

**Parent:** [G1927/28 #2788](https://github.com/Trexzo/SpawnPK-RE/pull/2788) exact `8416418ea8a146f9d5fdff211d111f545d4ad903` (633 focused; own hosted CI required). This source already has an optional expected-registration parameter on `NpcCombatResolutionService.resolveImmediateOwnedAdmitted` and atomic identity check inside `NpcLifecycleService.applyDamageOwnedIfRegistered`.

## Critical seam verified

The initial G1927 test replaced a canonical target's lifecycle in the handler `beforeResolution` hook. G1929 instead injects replacement in the resolver's **`ImmediateDamageAdmission.allow` callback**, which runs *after* `world.withOpenPlayerOwnership`, player mutation lock and `WorldNpcRegistry.withCurrentMutationOwnershipIfCurrent` confirm World/player/NPC ownership — immediately before lifecycle `applyDamageOwnedIfRegistered` attempts its entry-locked mutation. A same-canonical, same-HP, same-source lifecycle may be removed/re-registered reentrantly under those locks. The new identity check must refuse this newer entry, showing it cannot slip through in the final admission-to-damage window.

- **G1929** directly constructs authoritative `NpcCombatResolutionService` bound to a real isolated `World` and its `NpcLifecycleService`; injects re-registration of selected canonical NPC within the final admission callback and requires typed `StaleLifecycleRegistrationException`, no new HP loss or reward, while exact attacker and canonical NPC remain current. This is a test-only callback injection, not a claim about original packet timing.
- **G1930** replaces only another NPC's lifecycle inside the same callback, verifying the selected target's accepted 10-damage lifecycle write still succeeds.

Zero production changes on top of G1927/28, no packet/loot/G1/account policy changes. Two JavaExec tasks, manifest **633→635**, mechanically 635 unique focused classes. Exact-head hosted Java11 major55/full Gradle and 635/635 + G1929/30 markers required. This verifies atomic expected-registration check relative to synchronous lifecycle entry mutation, not end-to-end atomic packet/kill settlement or original SpawnPK server kill-credit authority. Draft/unmerged; #2558 and #2587 remain open.
