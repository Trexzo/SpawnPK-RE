# v5.4 Player State, Pet Replacement, Weapon Pose and Comp-Cape Parity

## Authority boundary

v5.4 is MAINLINE/MECHANICS only. It consumes the generic NPC engine from v5.3 but does not change production-home placement. WORLD-R* owns scene manifests and static home population.

## Player state

The pinned current client consumes skill update packet 134 as fixed six bytes: skill index, transformed 32-bit XP, current level. LocalLab sends combat skills 0..6 at level 99 / XP 13,034,431 and advertises combat level 126 in the player appearance block.

## Weapon poses

`weapon_poses.tsv` is the runtime data source. Direct production observations outrank clone/canonical-family inheritance. The resolver keeps evidence text alongside every resolution so inferred family propagation is never mislabeled as direct observation.

Elder-maul family fixture:

`7518,823,7520,820,821,822,7519`

Current required members:

- 20485 Elder maul base/direct family authority
- 21005 Ethereal elder maul via lineage
- 28030 Elder maul (or) via canonical family

## Pet replacement

The v5.3 reject-only one-pet rule is replaced by an atomic-ish inventory/NPC transaction. A new pet Drop is accepted only when runtime and persisted active-pet state agree. The new item is consumed first, which guarantees one inventory slot is available to restore the previous pet item. The previous follower is then removed, old item restored, new follower added, and state persisted.

## Pet follow

Normal follow pulses support one or two NPC movement steps. Desired invariant after each normal pulse: Chebyshev distance <= 1 when reachable with at most two steps. Separation >12 uses packet-65 remove/re-add beside the owner.

## Completionist customization

Special set: 23063 / 21963 / 21964.

Inventory Customize uses exact C2S opcode 75, fixed six bytes. MAINLINE opens native root 63036. Native `compcolors` command values are persisted as six selectors and re-emitted in the packet-81 special completionist extension.

## Deferred evidence

No owner-side pet Drop/Pick-up animation or GFX is synthesized. No collection-icon world/player badge field is synthesized. Those require direct current-client or production evidence.
