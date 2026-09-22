# Evidence Package — Exact v308 Lottery Interfaces

SYSTEM

SpawnPK ordinary Lottery and Bloodcore Token Lottery client applications.

STATUS

CLOSED-CLIENT-UI-CONTRACTS / SERVER-LOTTERY-POLICY-UNKNOWN

## AUTHORITY

~~~
SHA-256 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
build/config 308
ordinary lottery class: rs.n.c.ao
Bloodcore lottery class: rs.n.c.n
~~~

These are two distinct native interfaces with a closely related presentation model.

## ORDINARY LOTTERY

Exact root:

~~~
52000
~~~

Exact static presentation:

~~~
52001 -> Lottery title
52002 -> @yel@The winning pot is currently..
52003 -> Latest Raffle Winners
52004 -> Time until the winner is announced..
52005 -> @yel@5 hours 18 mins 48 secs
52006 -> @or1@(There are @gre@23@or1@ participants in this lottery)
~~~

The time and participant count are shipped static defaults/placeholders. They do not prove live server values or scheduling rules.

Two item-display widgets exist:

~~~
52007
52008
~~~

Both are configured with four columns and spacing 11. The exact builder does not statically seed an item id/quantity into them, so the client does not prove the ordinary lottery currency, entry cost or pot item.

Winner/history panel:

~~~
52009
content height: 50
width: 350
visible height: 83
6 child text rows: 52014..52019
~~~

The history rows initialize empty.

Entry control:

~~~
52010 / 52011
tooltip: Enter lottery
52012 -> fountain/icon 1
52013 -> Buy-\nentry
~~~

The root installs 17 children.

## BLOODCORE TOKEN LOTTERY

Exact root:

~~~
61150
~~~

Exact static presentation:

~~~
61152 -> Bloodcore Token Lottery
61153 -> The lottery's pot is currently..
61154 -> @yel@Latest Bloodcore Lottery Winners
61155 -> Time until the winner is announced..
61156 -> @yel@5 hours 18 mins 48 secs
61157 -> @or1@(There are @gre@23@or1@ participants in this lottery)
~~~

Again, the time/count strings are client defaults rather than production-state authority.

Two exact item-display widgets are statically seeded:

~~~
61158 -> item 22844, quantity 250
61159 -> item 22844, quantity 10000
~~~

This proves item 22844 participates in the shipped Bloodcore Lottery presentation and that the client has two distinct displayed token quantities.

It does NOT, from interface construction alone, safely prove which quantity is entry cost, current pot, target, minimum, or another semantic value. Server-state evidence is required before naming those roles.

Winner/history panel:

~~~
61160
content height: 475
width: 350
visible height: 83
35 child text rows: 61161..61195
~~~

The rows initialize empty.

Entry control:

~~~
61196 / 61197
tooltip: Enter bloodcore lottery
61199 -> fountain/icon 1
61200 -> Enter
~~~

The root installs 14 children.

## INPUT TRANSPORT

Both entry controls are ordinary actionable widgets. Exact v308's generic widget-action route is C2S185 with the widget id.

No dedicated lottery C2S family is proven by these builders.

## CLIENT-COMPATIBLE S2C PRESENTATION

Generic exact-current presentation primitives are sufficient:

- S2C97 — open interface;
- S2C126 — title/time/count/history text updates;
- S2C53/S2C34 — item-display/container updates where the server changes token/currency presentation;
- S2C219 — close interfaces.

Current LocalLab already has reusable publisher capability for S2C97, S2C126, S2C53 and S2C219.

## SERVER SEMANTICS PROVEN

The client proves:

- ordinary Lottery and Bloodcore Token Lottery are separate applications;
- both present a pot, countdown, participant count and winner history;
- both expose an entry action;
- the Bloodcore variant displays item 22844 in two token-value widgets;
- the Bloodcore history surface has substantially more rows than the ordinary Lottery.

## SERVER SEMANTICS UNKNOWN

UNKNOWN_SERVER_AUTHORITY:

- ordinary lottery currency/item;
- entry prices;
- meaning of Bloodcore quantities 250 and 10,000;
- pot accumulation rules;
- ticket/entry limits;
- winner selection/RNG;
- draw interval and scheduler;
- payout formula;
- refund/cancellation rules;
- participant eligibility;
- persistence across restart;
- winner-history retention;
- anti-abuse/multi-account policy.

Do not promote the static countdown, participant count or displayed token quantities into lottery mechanics without separate server authority.

## READY FOR CHAT 2

yes for existing generic widget/text/container transport; no new packet family required

## READY FOR CHAT 3

yes

Chat 3 can use one semantic Lottery/Draw primitive with different currency/presentation adapters for ordinary and Bloodcore variants, while keeping draw policy and economics explicit LOCAL_LAB_POLICY unless separately recovered.