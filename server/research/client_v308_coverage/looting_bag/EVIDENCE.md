# Evidence Package — Exact v308 Looting Bag Interface

SYSTEM

SpawnPK native Looting Bag client interface and item-action presentation.

STATUS

CLOSED-CLIENT-UI-CONTRACT / SERVER-RULES-UNKNOWN

## AUTHORITY

~~~
SHA-256 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
build/config 308
class rs.n.c.an
~~~

## ROOT

~~~
26700
~~~

Root contains 7 children.

## CLIENT WIDGETS

~~~
26701 -> misc/BAG background
26702 -> Close button
26703 -> Close hover
26705 -> Looting bag title
26706 -> bag item container
26707 -> This bag is empty.
26708 -> Deposit all to bank
~~~

## BAG CONTAINER

Widget 26706 is built as:

- 4 columns;
- 7 rows;
- 28 slots total;
- horizontal spacing 13;
- vertical spacing 0.

Exact item context actions:

~~~
option 1 -> Deposit 1
option 2 -> Deposit 5
option 3 -> Deposit 10
option 4 -> Deposit All
~~~

The global exact-v308 C2S census already proves the native generic widget-item option transports:

~~~
option 1 -> C2S145
option 2 -> C2S117
option 3 -> C2S43
option 4 -> C2S129
~~~

Those packet identities are transport details; Chat 3 should consume semantic deposit intents instead.

## DEPOSIT-ALL CONTROL

~~~
26708
label/tooltip: Deposit all to bank
~~~

This is a separate interface control from per-slot item actions.

## EMPTY STATE

~~~
26707 -> This bag is empty.
~~~

The client can therefore switch between container-backed contents and an explicit empty-state message.

## ROOT CHILD PLACEMENT

~~~
26701 @ 9,21
26702 @ 168,4
26703 @ 168,4
26705 @ 95,4
26706 @ 12,23
26707 @ 95,113
26708 @ 10,1
~~~

## CLIENT-COMPATIBLE S2C PRESENTATION

Exact v308 supports:

- S2C97 / S2C164 / S2C218 style interface roots depending presentation context;
- S2C53 full widget item-container update;
- S2C34 partial widget item-container update;
- S2C126 text/control updates;
- S2C171 visibility;
- S2C219 close interfaces.

Current LocalLab already has strong S2C53/S2C97/S2C126/S2C219 publisher foundations.

## SERVER SEMANTICS PROVEN

The client proves:

- a native Looting Bag interface exists;
- it holds 28 item slots;
- it offers Deposit 1/5/10/All per item;
- it offers Deposit all to bank;
- it has an explicit empty-state presentation.

## SERVER SEMANTICS UNKNOWN

UNKNOWN_SERVER_AUTHORITY:

- where/when the bag may be opened;
- whether deposits are Wilderness-only;
- which item types are allowed;
- capacity/stacking server validation beyond the 28 client slots;
- whether items are lost on death;
- whether bag contents persist across logout;
- withdrawal restrictions;
- bank-deposit eligibility;
- anti-abuse;
- any fees or cooldowns.

Do not import generic OSRS Looting Bag rules as SpawnPK authority unless separately evidenced.

## READY FOR CHAT 2

yes for existing generic transport; no new packet family required

## READY FOR CHAT 3

yes

Chat 3 can model a semantic LootingBag aggregate with 28-slot projection and per-slot deposit intents, using existing container/bank abstractions. Gameplay restrictions remain evidence-gated.