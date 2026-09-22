# Evidence Package — Exact v308 Blood Fountain Hub + Blood Diamond Fuser

SYSTEM

SpawnPK Blood Fountain navigation hub and Blood Diamond Fuser client application.

STATUS

CLOSED-HUB-AND-FUSER-CLIENT-CONTRACT / STORE-DESTINATION-UI-NOT-YET-CLOSED / FUSION-RULES-UNKNOWN

## AUTHORITY

~~~
SHA-256 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
build/config 308
hub class rs.n.c.i
fuser class rs.n.c.j
~~~

## BLOOD FOUNTAIN HUB

Actual root:

~~~
3320
~~~

Background/title:

~~~
60000 -> fountain/SPRITE 10
60001 -> @or1@The Blood Fountain
~~~

Exact selectable navigation entries:

~~~
60002 -> <img=186> Blood perk tree (abilities)
         tooltip: Select blood perk tree

60003 -> <img=118> Blood pool store
         tooltip: Select blood pool shop

60004 -> <img=67> Blood diamond fuser
         tooltip: Select diamond fusing

60005 -> <img=117> Blood diamond store
         tooltip: Select diamond shop

60006 -> <img=68> Blood shard salvaging
         tooltip: Select shard salvaging

60007 -> <img=116> Blood shard store
         tooltip: Select shard shop
~~~

Shared close family:

~~~
65418 / 65419
~~~

This proves these are six distinct client navigation intents under one Blood Fountain hub.

The Blood Diamond store and Blood Shard store are therefore distinct destinations from the Fuser/Salvaging applications. This package does not yet claim their destination interface roots or shop inventories.

## BLOOD DIAMOND FUSER — ROOT CORRECTION

The fuser class has a static root field:

~~~
rs.n.c.j.c = 318
~~~

and calls `d(c)` to create/use the root.

Therefore the exact Blood Diamond Fuser root is:

~~~
318
~~~

**65403 is not the root.** It is the first background child.

This corrects the tempting but incorrect inference that the clustered `65403+` widgets imply root 65403.

## FUSER PRESENTATION

Exact background/title/instruction:

~~~
65403 -> fountain/SPRITE 9
65404 -> @or1@Fuse items to sacrifice them into blood diamonds!
65405 -> @or1@Blood Diamond Fuser
~~~

The root installs 19 children.

## THREE EXACT FUSE ROWS

### Fuse row 1

~~~
65406 button
tooltip: Fuse
65407 hover/paired
65409 visible label: Fuse
~~~

### Fuse row 2

~~~
65410 button
tooltip: Fuse
65411 hover/paired
65413 visible label: Fuse
~~~

### Fuse row 3

~~~
65414 button
tooltip: Fuse
65415 hover/paired
65417 visible label: Fuse
~~~

The exact builder therefore exposes three separately actionable Fuse positions.

It does **not** build a generic item-container input surface in this class. The client alone does not prove what item/recipe each of the three Fuse rows represents; those bindings must come from another state/update channel or server authority.

## CYCLE-ITEMS CONTROL

Exact control:

~~~
65752
tooltip: Cycle items
65753 hover/paired
65755 visible label: Cycle
65756 visible text: Fuser
65757 -> fountain/icon 1
~~~

This proves the client can cycle/change the Fuser presentation/item set independently of pressing one of the three Fuse actions.

## ROOT PLACEMENT

High-value exact placement:

~~~
root 318

65403 @ 5,20
65404 @ 258,67
65405 @ 263,27

65406/65407 @ 230,128
65409       @ 259,129

65410/65411 @ 230,190
65413       @ 259,191

65414/65415 @ 230,251
65417       @ 259,252

65418/65419 @ 481,27

65752/65753 @ 420,266
65755       @ 468,275
65756       @ 468,287
65757       @ 426,276
~~~

## INPUT TRANSPORT

The hub entries, Fuse controls and Cycle-items control are exact actionable widgets.

No Blood-Fountain/Fuser-specific C2S packet family is proven. The generic exact C2S185 widget-action transport is the appropriate transport candidate/adapter seam unless a client-local interception is separately demonstrated.

Domain code should receive semantic intents such as `OPEN_DIAMOND_FUSER`, `FUSE_SLOT_1`, or `CYCLE_FUSER_SET`, not raw widget ids.

## CLIENT-COMPATIBLE S2C PRESENTATION

Generic exact-current presentation mechanisms can drive this family:

- S2C97 — open root;
- S2C126 — labels/status/recipe text where server-driven;
- generic widget model/item-model families if recipe visuals are server-swapped;
- S2C219 — close interfaces.

The S2C publisher-parity campaign still tracks generic widget-model publisher gaps.

## CURRENT SERVER GAP

Current-main code search found no dedicated references for:

~~~
root 3320
widget 60004
root 318 / child 65403
widget 65752
Blood Diamond Fuser
~~~

at the time of this package.

## SERVER SEMANTICS PROVEN

The exact client proves:

- The Blood Fountain is a navigation hub rather than one monolithic mechanic;
- Blood perk tree, Blood pool store, Diamond Fuser, Diamond store, Shard Salvaging and Shard store are distinct destinations;
- the Blood Diamond Fuser sacrifices items into blood diamonds according to exact client wording;
- the Fuser has three separately actionable Fuse rows;
- the Fuser has a Cycle items control.

## SERVER SEMANTICS UNKNOWN

UNKNOWN_SERVER_AUTHORITY:

- item/recipe bindings for the three Fuse rows;
- required quantities;
- blood-diamond item identity if not separately proven;
- yields;
- success/failure chance;
- costs/fees;
- cycling order/set catalog;
- unlocks;
- inventory validation;
- store roots and inventories;
- store prices/currencies;
- persistence;
- quotas/cooldowns;
- anti-abuse.

Do not invent recipe economics from the three-button layout.

## EXISTING SERVER SUBSTRATE

The Fuser should compose onto ConversionService/RecipeCatalog-style foundations. The hub should be a semantic navigation adapter. Store destinations should reuse the general Shop domain once their exact presentation/content contract is recovered.

## READY FOR CHAT 2

yes for generic widget transport/publisher support only

## READY FOR CHAT 3

yes for hub + fuser semantic composition

Chat 3 can model three named/ordered fuser recipe slots plus a cycle/preset selector without exposing widget ids. Actual recipes/yields/store content remain separate authority/policy.