# Chapter rewards, shop tabs and generic dialogs

## Shop Tabs — subtype 17
Three operations: reset defaults, define a tab-string set, select tab. The default set begins with `Main stock`; the interface has five tab positions and client action `Select shop tab`. Stock/prices/currency remain server state.

## Chapter Rewards — subtype 22
Operation 3 is a real variable record builder containing a reward/card type, definition id, one/two text strings, an arbitrary list of id+amount pairs, two u16 scalars and a boolean. Operation 7 gives exact claim-state presentation: incomplete / claimable / claimed.

## Confirmation — subtype 28
Controls layout/reset and two model/visual slots (14171, 14172). The base client prompt is `Please confirm your choice.` with Confirm/Cancel controls.

## Selection Dialog — subtype 40
Can replace a widget's list with arbitrary labels and optional custom action text per row, then select a row by index. This is a reusable generic server-driven selection protocol, not a feature-specific one.
