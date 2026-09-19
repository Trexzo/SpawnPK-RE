# Official SpawnPK Item Library — current-client contract

Exact class: `rs.n.c.ab`.

This interface is more than a static screen. The client accepts named update records and maintains its own preview/equipment model state.

## Exact recoverable keys

`ANIMATION` updates the preview animation integer.

`SLOT_<n>` directly fills one of 15 item slots.

Named equipment-slot keys map as follows:

| key | preview slot index |
|---|---:|
| HEAD | 1 |
| WEAPON | 6 |
| CHEST | 7 |
| FEET | 13 |
| LEGS | 10 |
| HANDS | 12 |
| AMULET | 4 |
| CAPE | 3 |
| RING | 14 |
| ARROWS | 5 |
| ARM | 8 |

The visible stat shell contains Attack bonus (Stab/Slash/Crush/Magic/Range), Defence bonus (same five), Range strength and Prayer plus an `ANIMATION` presentation control. The class does **not** embed a complete all-item stat database; values/content are supplied into the interface.

This is useful for LocalLab because the generic Item Library protocol/UI can be implemented independently from the unresolved full production equipment-stat authority.
