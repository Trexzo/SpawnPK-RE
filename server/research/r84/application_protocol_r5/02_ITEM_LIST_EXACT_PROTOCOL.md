# Subtype 14 — Item List / Search / Transfer exact protocol

The interface root is 36000. Main item grid is 36025; secondary action target is 30074. Search controls are 36012/36013/36015, back control 36016, description widgets 36002/36003/36019, and up to five tabs begin at 36804.

All 26 operations are structurally closed. Important semantics:

- op4 mode 0 clears all 700 rows; nonzero appends `itemId, amount`, and mode 2 additionally consumes row text.
- op5 performs client-side filtering against loaded item-definition names.
- op12 defines up to five main-grid actions. Defaults are Remove 1/5/10/All.
- op23 defines up to five secondary-grid actions. Defaults are Deposit 1/5/10/All.
- op19 sets the main item-grid spacing/dimension pair passed to `h(int,int)` and recomputes layout; op21 separately controls the column count.
- op20 controls whether zero-quantity slots are visually disabled/dimmed.
- op21 sets item-grid column count and recomputes rows/layout; >=10 columns widens the panel.
- op22 updates one grid slot directly as `(slot, itemId, amount)`.
- op24/op25 are menu-action-code overrides. Both default to 431. During menu action 632 under root 36000, the client substitutes op24/main-grid code (`bK`), or op25/secondary-widget-30074 code (`bL`).

This means LocalLab can reproduce the generic item-list/search/transfer presentation and its outbound action-code routing without inventing item ownership or transfer rules.
