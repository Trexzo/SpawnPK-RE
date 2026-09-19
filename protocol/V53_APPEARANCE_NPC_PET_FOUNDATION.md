# SpawnPK LocalLab v5.3 — Appearance Safety + NPC/Pet Foundation

v5.3 is the first post-equipment milestone. It retains the complete v5.2.3 item/equipment reconciliation and concentrates on two runtime gaps exposed by the latest live run: the special Grand completionist cape appearance path and the missing pet/NPC semantics.

## Special Completionist-cape appearance extension

The v5.2.3 live run equipped item 23063 successfully as CAPE, after which the LocalLab server continued packet-81 world ticks but the client stopped producing packets. The exact pinned current client gives a concrete reason to harden this path.

`rs.a.k.b(int)` returns true only for item IDs:

- 23063
- 21963
- 21964

When one of these items is in appearance position 1, the player's model builder attaches `aS` and `aI` to the worn model. Those fields are initialized only by the final optional section of the player appearance block. Earlier LocalLab appearances always sent that extension flag as zero, leaving the special-cape state uninitialized.

v5.3 sends the native default Completionist-cape colour extension whenever one of the exact three special cape IDs is equipped. The six selector bytes are:

`13, 9, 7, 9, 7, 5`

They resolve through the exact client's native colour palette to:

`6015, 924, 62575, 924, 62575, 0`

This is the same native default vector represented by the client's `bJ` colour state, in the order used by its Completionist-cape model path.

Offline exact-client parser certification proves item 23063 now initializes the 1206-entry `aS` array and consumes the complete appearance block.

## Generic NPC synchronization foundation

v5.3 replaces the old one-off `npcIndex=1` bootstrap with a per-session NPC registry using the exact current packet-65 topology.

Local diagnostic entities:

- scene 1 -> NPC 1799 Blood Fountain
- scene 2 -> NPC 1488 Max hit dummy (Player)
- scene 3 -> NPC 1489 Max hit dummy (PvM)
- scene 4 -> active player pet, when present

The coordinates are still explicitly localhost diagnostic placements and are not claimed as production home coordinates.

The packet-65 encoder supports:

- multiple initial NPCs
- retain
- one-step movement
- two-step movement
- removal
- new NPC addition
- NPC update mask `0x20` for interaction/follow target

For player-owned followers, the target is encoded as:

`32768 + ownerPlayerIndex`

The exact pinned client stores this as `rs.a.j.m`. Production evidence specifically disproved the older `aH` ownership interpretation.

## Pet repository and lifecycle

The V9.02 pet census is imported as data rather than Java item-ID conditionals.

- 260 unique exact-name item -> NPC candidates are enabled for LocalLab
- 49 ambiguous/weak mappings remain fail-closed
- Ancient Guardian 20776 -> 3098 carries stronger passive production Drop->follower correlation provenance

Examples:

- 20776 Ancient guardian pet -> NPC 3098, stand 3033, walk 3034
- 22519 Ultimate olmlet pet -> NPC 3843, stand 7396, walk 7395
- 23484 Scooby's Ultimate olmlet pet -> NPC 3845, stand 7396, walk 7395
- 28888 Scopesight vasa pet -> NPC 8330, stand 7416, walk 7411
- 28891 Molten geyser titan pet -> NPC 8334, stand 7876, walk 7877

Ambiguous mappings such as Unholy behemoth 25425 and Ultimate easter kalphite 24145 are not silently guessed.

Lifecycle:

1. inventory `Drop` -> exact C2S opcode 87
2. decode item ID + widget + inventory slot
3. resolve item through `PetDefinitionRepository`
4. remove exactly one inventory item
5. spawn mapped NPC through S2C packet 65
6. initialize follower target to `32768 + ownerPlayerIndex`
7. move follower through normal NPC walk/run updates
8. if the follower is left more than 12 tiles behind, rebuild it beside the owner using packet-65 remove/add
9. `Pick-up` -> exact C2S opcode 155 with scene NPC index
10. validate active ownership, despawn, restore the original item

Only one active pet is allowed. Active pet item/NPC identity is stored in the existing `opensrc.properties` account state and restored on login.

Pet combat bonuses, procs, restrictions, and hidden production mechanics are intentionally not invented in v5.3.

## Retained foundation

v5.3 retains:

- 86/86 exact current-client C2S framing
- native Spawn/Search tab 13 / root 67027
- 1,833/1,833 current equipment-action candidate resolution
- packet-53 inventory/equipment state
- packet-81 multi-slot appearance and pose profiles
- `opensrc` account persistence
- bank close inventory synchronization
- M4/M5 login/world/movement baseline

## Evidence boundary

The special-cape packet format and special ID set are exact current-client facts. Pet packet topology and Ancient Guardian correlation have production/runtime support. Most of the 260 bulk pet mappings are strong static exact-name candidates from the current V9.02 item/NPC corpus, not individually server-authoritative production proofs.
