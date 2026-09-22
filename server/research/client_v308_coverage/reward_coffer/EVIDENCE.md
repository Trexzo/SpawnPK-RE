# Evidence Package — Exact v308 Coffer of Unclaimed Rewards & Prizes

SYSTEM

SpawnPK Coffer of Unclaimed Rewards & Prizes client application.

STATUS

CLOSED-CLIENT-UI-CONTRACT / REWARD-DELIVERY-POLICY-UNKNOWN

## AUTHORITY

~~~
SHA-256 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
build/config 308
class rs.n.c.u
~~~

## ROOT

~~~
42100
~~~

Background:

~~~
35612 -> bank/bank 30
~~~

Title:

~~~
42103 -> <img=131> Coffer of Unclaimed Rewards & Prizes <img=131>
~~~

## COFFER CONTAINER

Main item widget:

~~~
42101
~~~

Exact shape:

- 70 item slots;
- 7 columns;
- 10 rows;
- horizontal spacing 32;
- vertical spacing 16.

Exact item actions:

~~~
Remove 1
Remove 5
Remove 10
Remove All
(fifth action null)
~~~

Global exact-v308 widget-item action transport positions map these four actions to C2S145/117/43/129.

Container/scroll parent:

~~~
42102
content height: 500
width: 445
visible height: 244
contains 42101 @ 5,10
~~~

## BULK DELIVERY CONTROLS

Exact button construction includes two distinct controls:

~~~
42104 / 42105
tooltip: Deposit items to your inventory
~~~

and:

~~~
42108 / 42109
tooltip: Deposit items to your bank
~~~

The compound button helper also embeds the static string:

~~~
Empty your backpack into\nyour bank
~~~

for associated hover/auxiliary widgets 42106/42107 and 42110/42111.

That wording is internally mixed with the coffer-specific tooltips. Preserve it as exact legacy client text; do not silently reinterpret the auxiliary helper string into a different server action without tracing the helper/runtime behavior.

## EXPLANATORY CLIENT AUTHORITY

Exact shipped text:

~~~
42112 -> <img=9> This coffer usually holds contest prizes, event rewards, etc.
42113 -> (Especially if you were offline when you received them)
~~~

This is strong client evidence that the intended application includes deferred/unclaimed reward delivery, including rewards received while offline.

It still does not prove the original server's durable storage/retry/expiry semantics.

## INPUT TRANSPORT

The bulk buttons are ordinary actionable widgets and fit the exact generic C2S185 widget-action route.

Per-item Remove actions use the generic widget-item option family.

No coffer-specific packet family is proven.

## CLIENT-COMPATIBLE S2C PRESENTATION

Generic exact-current publishers are sufficient:

- S2C97 — open interface;
- S2C53/S2C34 — coffer container population/update;
- S2C126 — text/status updates if required;
- S2C79 — scroll state;
- S2C219 — close interfaces.

Current LocalLab already has reusable S2C97, S2C53, S2C126 and S2C219 capability.

## SERVER SEMANTICS PROVEN

The client proves:

- a 70-slot unclaimed reward/prize coffer exists;
- it is intended for contest prizes, event rewards and similar deliveries;
- offline receipt is explicitly part of the client explanation;
- rewards can be removed in per-item quantities 1/5/10/All;
- there are separate bulk controls targeting inventory and bank.

## SERVER SEMANTICS UNKNOWN

UNKNOWN_SERVER_AUTHORITY:

- authoritative reward enqueue source list;
- durable persistence format;
- exactly-once/idempotency model;
- expiry policy;
- capacity overflow handling;
- account-vs-character scope;
- offline transaction atomicity;
- bank/inventory fallback rules;
- partial-claim behavior on insufficient space;
- item eligibility/stacking validation;
- duplicate-reward prevention;
- audit/history retention.

These concerns belong in the semantic reward-delivery/coffer service rather than packet handlers.

## EXISTING LOCALLAB SUBSTRATE

LocalLab already has mailbox/reward-delivery and container foundations. This exact client contract should be composed onto those primitives rather than creating a second persistence/reward pipeline.

## READY FOR CHAT 2

yes for persistence/runtime requirements when Chat 3 defines the semantic durable model; no bespoke packet required

## READY FOR CHAT 3

yes

Chat 3 can treat this as a deferred RewardDelivery/Coffer projection with exact 70-slot UI and claim controls. Offline durability/idempotency policy must be explicit and tested rather than inferred from the interface.