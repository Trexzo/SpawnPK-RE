# v5.2.3 — All-item equipment reconciliation

## Goal

Replace incremental item-family whitelists with a reusable slot resolver while preserving evidence precedence and fail-safe behavior.

## Resolution precedence

`explicit anchor -> production appearance observation -> compatible lineage -> exact ambiguity override -> semantic family -> trusted opcode41 base-cache fallback`

The production appearance map is derived from post-decode packet-81 equipment arrays. Appearance positions are translated through the already-established SpawnPK equipment layout.

Clone/equip lineage is traversed with cycle protection. If production and lineage agree on slot, lineage may supply secondary properties such as coverage, two-handed state and weapon pose. Slot disagreement never overrides direct production appearance evidence.

## Trusted client-action fallback

A base-cache item can lack an action row in LocalLab's current `items.tsv` even though the pinned client presents Wear/Wield and sends opcode 41. The fallback is therefore entered only from the exact client equip-action handler. It classifies the slot conservatively from item identity/family instead of treating every catalogue name as equipable.

## Coverage

Current LocalLab/current-config action candidates: 1833/1833 resolved. Direct production appearance map: 188 distinct IDs.

See `evidence/V523_EQUIPMENT_RESOLUTION_REPORT.tsv` for per-item provenance and `V523_EQUIPMENT_RESOLUTION_SUMMARY.txt` for counts and regression fixtures.

## Boundary

The runtime item census is not a substitute for the private production server's equipment metadata. v5.2.3 does not state that every runtime item ID has server-authoritative slot proof. Unknown future/custom cases can still be audited by provenance rather than hidden behind an unconditional name guess.
