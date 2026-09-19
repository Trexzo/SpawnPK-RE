# v5.5 MAINLINE — combat tab, pet trail follow, cape lifecycle, pose-family coverage

- Maps exact current-client combat interface roots from `rs.n.c.aY` into sidebar tab 0 and refreshes the root whenever the weapon changes. This restores native attack-style/spec interface **presentation**. Attack-style effects, spec-energy semantics and damage formulas remain combat-engine work.
- Grand completionist root `63036` now handles exact native Confirm `63027` and Cancel `63031`; both close with S2C packet `219`. `compcolors` persists selectors and refreshes packet-81 appearance when a supported completionist cape is equipped.
- Pet follow is scheduled about 250 ms after owner movement and consumes only authoritative owner breadcrumbs. Normal following no longer invents a direct shortcut through unknown collision. Far separation uses remove/re-add and prefers a recent owner-traversed adjacent tile; hard relocations fall back to an adjacent tile when no path breadcrumb exists.
- Pet mapping promotes 28 narrowly inferable V9.02 rows with explicit `V55_INFERRED_*` provenance: 288 mapped, 21 still ambiguous/fail-closed.
- Weapon pose fallback adds only production-consensus families: whip/tentacle, staff/wand/trident/sceptre, ordinary maul, and 2H/godsword. Direct/canonical and compatible-clone evidence has higher priority than broad family inference.
- Owner-side pet Drop/Pick-up animation/GFX, collection-badge presentation, Doppel accessory particles, the remaining 21 ambiguous pet mappings, and combat formulas are not invented in this release.
