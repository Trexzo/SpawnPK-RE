# Interface-button outbound routing

The current client's generic menu action `315` is the ordinary interface-button route. After local panel/controller hooks and feature guards, it emits:

`C2S opcode 185` + `widgetId`

This is the exact client-facing contract for data-driven buttons carrying the normal Select-style interface action.

## Clan Wars

The current Clan Wars builder supplies exact option widgets. All 27 recovered actionable choices are in `tables/clan_wars_widget_routes.csv`.

The Accept control ambiguity was resolved explicitly:

- `24086` = actionable Accept sprite/control
- `24087` = hover/alternate sprite child
- `24089` = text overlay

Thus the actionable route is `C2S185(24086)`.

This does not recover server-side match creation, validation or win resolution.

## Gambling selector

The mode selector widgets are in `tables/gambling_widget_routes.csv`:

- 59853 / 59854 — 55x2 host side
- 59855 / 59856 — BJ host side
- 59857 — Dice duel
- 59858 — Flower poker

These reveal selection routing only. RNG, stakes validation and outcomes remain server-owned.
