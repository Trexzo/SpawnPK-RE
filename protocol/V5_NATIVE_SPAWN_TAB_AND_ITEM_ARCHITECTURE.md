# v5 native Spawn Tab + generic item architecture

## Authority

Current pinned SpawnPK client remains the protocol/UI authority. Historical Apollo/generic 317 behavior is secondary reference only.

## Exact current-client contracts used

### Spawn Tab

Pinned client builder `rs.n.c.af` constructs native root `67027` with search button `64029`, search/status `64033`, results container `64071`, dynamic result widgets from `70000`, maximum 200 visible results and action `Spawn this item`.

The search is local: it iterates the current client item-definition table and substring-matches item names.

Selecting a result obtains an amount and emits command `tabitem <itemId> <amount>` through the already-proven opcode-103 command channel.

### Packet 71 shortcut

Pinned client packet-71 handler reads interface id and tab index. It applies:

- `65535 -> -1`;
- `0 -> 67027` (native Spawn Tab);
- tab index `2 -> interface 44100` special override;
- then stores the result in the sidebar table.

LocalLab v5 therefore sends wire interface id `0` on local tab index `10`. Tab 10 is only a LocalLab presentation choice; the protocol fact is the `0 -> 67027` client shortcut.

## Server architecture

`ItemDefinitionRepository` is the item metadata facade. It is backed by the embedded 28,673-row catalog generated from supplied readable `items.json`, enriched by current SpawnPK `i.bin` where available.

Generic item movement uses IDs and fixed containers rather than item-specific branches:

- Inventory: 28 slots;
- Bank: 352 slots;
- Equipment: server-owned appearance slots (equipment-slot metadata incomplete);
- command spawn: `item` and `tabitem`;
- packet53 refreshes normal/bank inventory;
- opcode41 handles confirmed Wield/equip flow;
- packet81 appearance mask refreshes equipped model.

## Boundary

Catalog presence/rendering is not server mechanic reconstruction. Custom attacks, specials, bonuses, requirements, consumables, pets and other gameplay effects remain separate server systems/plugins.

Unknown base-cache equipment slots are not guessed. Bloodrend 28526 is a regression fixture, not a special architecture branch.
