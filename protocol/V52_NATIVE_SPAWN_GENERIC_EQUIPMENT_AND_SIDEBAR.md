# v5.2 Native Spawn, Generic Equipment, Appearance and Sidebar Contract

## 1. Development login

After login success code `2`, the pinned client consumes the next byte into `Client.cT`. LocalLab sends:

`[2, 205, 0]`

This is a localhost-development client rank only. It is not a statement about production-server authorization.

## 2. Spawn Tab

The native Spawn Tab remains client-owned. LocalLab does not clone its search UI.

Current pinned-client roots:

- root: 67027
- search: 64029
- status/text: 64033
- results: 64071
- dynamic results: 70000+

The client sends `::tabitem <itemId> <amount>` through opcode 103. LocalLab accepts valid embedded catalogue IDs for development.

## 3. Semantic equipment

The server owns a 14-position equipment container and sends it to widget 1688 with packet 53. Semantic slot-to-container indices are:

HEAD=0, CAPE=1, AMULET=2, WEAPON=3, CHEST=4, SHIELD=5, LEGS=7, HANDS=9, FEET=10, RING=12, AMMO=13.

Equipment metadata comes from `server/data/equipment_slots.tsv` plus clone/equipClone inheritance. Unknown slots fail closed.

## 4. Inventory -> equipment

The current client maps inventory actions containing Wear/Wield/Equip to opcode 41. v5.2 accepts opcode 41 for inventory widget 3214, resolves the semantic equipment slot, updates inventory/equipment transactionally, sends both packet-53 containers, and sends a packet-81 appearance refresh.

The current equipment widget's first Remove action is handled through widget 1688 and returns the item to a free inventory slot before refreshing appearance.

## 5. Appearance projection

Equipment and appearance are intentionally separate representations.

Visible equipment is projected into packet 81. Non-visible state such as ring/ammo remains in equipment container state. Full-helm metadata can suppress default hair/beard identity-kit positions.

The reusable scythe profile is:

`[15692,823,1146,820,821,822,1210]`

No attack animation is inserted into packet 81.

## 6. Native sidebar roots

Packet 71 maps the current roots without force-selecting a tab:

- 3917 @ 1 Skills
- 44100 @ 2 Achievements/current quest-position
- 3213 @ 3 Inventory
- 1644 @ 4 Equipment
- 5608 @ 5 Prayer
- 1151 @ 6 Magic
- shortcut 0 @ 10 Native Spawn Tab -> client resolves 67027

Combat/spec is deliberately not assigned an invented root in v5.2 because it is weapon/config dependent and still requires authoritative reconciliation.

## 7. Certification boundary

v5.2 certifies interface wiring, item spawning, semantic equipment state, multi-slot appearance, and the tested equipment actions. It does not certify every prayer, spell, skill, combat style, special attack, or custom-item mechanic.
