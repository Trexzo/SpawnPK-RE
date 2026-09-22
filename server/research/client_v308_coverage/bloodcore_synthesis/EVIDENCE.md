# Evidence Package — Exact v308 Bloodcore Token Synthesis

SYSTEM

SpawnPK Bloodcore Token Synthesis client application.

STATUS

CLOSED-CLIENT-UI-CONTRACT / SYNTHESIS-RECIPES-AND-ECONOMICS-UNKNOWN

## AUTHORITY

~~~
SHA-256 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
build/config 308
class rs.n.c.m
~~~

## ROOT

~~~
61078
~~~

Background:

~~~
61079 -> fountain/sprite 21
~~~

Title:

~~~
61080 -> <img=180> Bloodcore Token Synthesis <img=180>
~~~

The root installs 24 children.

## PRIMARY CONTROLS

### Start synthesis

~~~
61081 / 61082
tooltip: Synthesize items
hover/paired: 61083
61084 -> fountain/icon 1
61085 -> Start
~~~

### Bloodcore token shop

~~~
61086 / 61087
tooltip: Bloodcore token shop
hover/paired: 61088
61089 -> fountain/coins
61090 -> Shop
~~~

### Synthesis item guide

~~~
61091 / 61092
tooltip: Synthesis item guide
hover/paired: 61093
61094 -> icons/help
61095 -> Guide
~~~

Two additional text widgets 61096 and 61097 initialize empty and are available for server-driven status/presentation.

## INPUT / REMOVAL CONTAINER

Parent:

~~~
61098
content height: 250
width: 248
visible height: 149
contains 61099 @ 11,5
~~~

Input item widget 61099:

- five columns;
- horizontal spacing 16;
- vertical spacing 10;
- exact item actions:

~~~
Remove 1
Remove 5
Remove 10
Remove All
Remove X
~~~

Global exact-v308 widget-item option transport maps these five action positions to:

~~~
option 1 -> C2S145
option 2 -> C2S117
option 3 -> C2S43
option 4 -> C2S129
option 5 -> C2S135
~~~

Those opcodes are transport detail; the semantic domain should receive remove-amount intents.

## SECOND ITEM DISPLAY

Widget 61100 is a separate item widget configured with four columns and spacing 11.

The exact builder proves a second item-display/container surface but does not by itself prove whether it is output, preview, reward, required-token display, or another role. Keep that semantic role evidence-gated until runtime/server authority closes it.

## BLOODCORE LOTTERY SHORTCUT

Exact shortcut inside the synthesis interface:

~~~
61101 / 61102
tooltip: Bloodcore lottery
hover/paired: 61103
61104 -> fountain/dice
61105 -> Lotto
~~~

This directly links the Synthesis and Bloodcore Lottery applications in client navigation without making them the same domain system.

## INPUT TRANSPORT

The Start/Shop/Guide/Lotto controls are ordinary actionable widgets and are compatible with the exact generic C2S185 widget-action route.

No Bloodcore-Synthesis-specific packet family is proven.

## CLIENT-COMPATIBLE S2C PRESENTATION

Generic exact-current publisher families are sufficient:

- S2C97 — open main interface;
- S2C53/S2C34 — full/partial item-container projection;
- S2C126 — status/text/control projection;
- S2C79 — scroll state;
- S2C219 — close interfaces.

Current LocalLab already has reusable publisher capability for S2C97, S2C53, S2C126 and S2C219.

## SERVER SEMANTICS PROVEN

The client proves:

- a distinct Bloodcore Token Synthesis application exists;
- it accepts a multi-item input/container surface;
- input items can be removed by 1/5/10/All/X;
- it exposes Start, Shop and Guide actions;
- it has a direct Bloodcore Lottery shortcut;
- it has server-updatable status/presentation fields.

## SERVER SEMANTICS UNKNOWN

UNKNOWN_SERVER_AUTHORITY:

- synthesis recipes;
- eligible items;
- required quantities;
- token yields;
- costs/fees;
- success/failure chance;
- RNG;
- consumption atomicity rules;
- second item widget semantic role;
- reward delivery behavior;
- persistence;
- quotas/cooldowns;
- anti-abuse.

Do not create a parallel synthesis engine solely for this interface: existing ConversionService/RecipeCatalog-style foundations are the appropriate semantic substrate.

## READY FOR CHAT 2

yes for existing generic transport; no bespoke packet required

## READY FOR CHAT 3

yes

Chat 3 can compose Bloodcore synthesis as a protocol-independent conversion/recipe application projected onto this exact interface. Recipes, economics and RNG must remain separate authority/policy.