# Evidence Package — Exact v308 Blood Shard Salvaging Kit

SYSTEM

SpawnPK Blood Shard Salvaging Kit client application.

STATUS

CLOSED-CLIENT-UI-CONTRACT / SALVAGE-RECIPES-AND-YIELDS-UNKNOWN

## AUTHORITY

~~~
SHA-256 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
build/config 308
class rs.n.c.k
~~~

## ROOT

~~~
18546
~~~

Background:

~~~
60008 -> fountain/SPRITE 11
~~~

Title:

~~~
60009 -> @or1@Blood Shard Salvaging Kit
~~~

Close uses the shared 65418/65419 close family.

## INPUT PANEL

Intro text:

~~~
60010 -> Items you'll salvage into shards..
~~~

Parent:

~~~
60011
content height: 250
width: 175
visible height: 132
contains 60012 @ 11,5
~~~

Input item widget 60012:

- four columns;
- horizontal spacing 11;
- vertical spacing 10;
- exact item actions:

~~~
Remove 1
Remove 5
Remove 10
Remove All
Remove X
~~~

Exact generic widget-item option transport positions correspond to C2S145/117/43/129/135. The domain should consume semantic remove-amount intents, not packet numbers.

## SECOND ITEM DISPLAY

Widget 60013 is a separate four-column item widget with spacing 11/10.

The builder proves this second display exists, but does not by itself prove whether it is shard output, preview, result queue, requirement display, or another semantic role. Keep that role unpromoted without runtime/server evidence.

## SALVAGE CONTROL

~~~
60014
sprite: fountain/SPRITE 4
size: 100x32
tooltip: Salvage
hover/paired: 60015
60017 -> Salvage
~~~

Widget 60018 is an empty text/status field.

## GUIDE CONTROL

~~~
60019
tooltip: Read guide
hover/paired: 60020
60022 -> Item guide
~~~

## INPUT TRANSPORT

Salvage and Guide are ordinary actionable widgets and therefore fit the exact generic C2S185 widget-action route.

No salvage-specific packet family is proven.

## CLIENT-COMPATIBLE S2C PRESENTATION

Generic exact-current presentation is sufficient:

- S2C97 — open interface;
- S2C53/S2C34 — input/result item containers;
- S2C126 — status/result text;
- S2C79 — scroll state;
- S2C219 — close interfaces.

Current LocalLab already has reusable publisher capability for S2C97, S2C53, S2C126 and S2C219.

## SERVER SEMANTICS PROVEN

The client proves:

- a distinct Blood Shard Salvaging Kit exists;
- it accepts items through a multi-slot input surface;
- selected inputs can be removed by 1/5/10/All/X;
- it exposes Salvage and Item Guide actions;
- it has a second item-display surface and status text.

## SERVER SEMANTICS UNKNOWN

UNKNOWN_SERVER_AUTHORITY:

- eligible salvage items;
- shard item identity if not separately proven;
- per-item shard yields;
- bulk rounding;
- costs/fees;
- random/guaranteed yields;
- second display role;
- inventory capacity/error behavior;
- persistence;
- quotas/cooldowns;
- anti-abuse.

Existing conversion/recipe infrastructure should be reused instead of creating a separate one-off salvage engine.

## READY FOR CHAT 2

yes for existing generic transport

## READY FOR CHAT 3

yes

Chat 3 can compose this as a semantic salvage/conversion recipe family with exact UI projection. Yield tables and policy remain evidence-gated.