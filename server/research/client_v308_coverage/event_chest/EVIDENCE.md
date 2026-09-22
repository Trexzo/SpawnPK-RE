# Evidence Package — Exact v308 Event Chest Interface

SYSTEM

SpawnPK Event Chest client interface and client-visible presentation contract.

STATUS

PARTIAL-CLOSED-CLIENT-UI / SERVER-MECHANICS-UNKNOWN

## AUTHORITY

Primary exact-current client:

~~~
SHA-256 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
build/config 308
~~~

Exact interface builder:

~~~
rs.n.c.N
~~~

This package records only client-visible interface structure and controls.

## ROOT

Exact root:

~~~
60600
~~~

Background:

~~~
60601 -> fountain/event 1
~~~

The root installs 24 children.

## ITEM / PRIZE CONTAINERS

### Main event item grid

~~~
60602
~~~

Exact client allocation:

- 175 item ids;
- 175 quantities;
- 175 auxiliary slot arrays;
- 7 columns;
- 25 rows;
- horizontal spacing 32;
- vertical spacing 16.

This is a substantial event-item/prize grid, not a single-item widget.

### Focus/selection container

~~~
60603
~~~

Exact client structure:

- one child;
- scroll/content height 1000;
- dimensions 449 x 185;
- contains 60602 at local position 12,10.

### Small tier grids

Exact 4-slot item containers:

~~~
60611
60612
60613
~~~

The client allocates four item/quantity entries for each.

## CONFIRMED TEXT / DISPLAY WIDGETS

Exact v308 static text includes:

~~~
60608 -> Roll
60609 -> Tier I Prize
60610 -> Roll all of the items in\nthe tier for a prize!
60618 -> Event Chest Tier I
60620 -> @or1@Tier I
60622 -> Event Chest Tier II
60624 -> @or1@Tier II
60616 -> Tier I - @yel@Halloween Event 2020
60625 -> @yel@0 / 25 rolls @or1@(0 / 50,000 tokens)
60630 -> @yel@Event Guide
60634 -> Reset exchange
~~~

The Halloween 2020 wording is exact client default/static presentation. It does not prove the production server's current event identity.

## CONFIRMED CLICKABLE CONTROLS

The interface builder creates explicit button widgets with tooltip/action text:

### Exchange

~~~
60604
tooltip: Exchange
hover/paired widget: 60605
~~~

### Enter next tier

~~~
60626
tooltip: Enter next tier
hover/paired root: 60628
~~~

### Reset event items

~~~
60631
tooltip: Reset event items
hover/paired root: 60632
~~~

These are exact client controls.

The generic native widget-action transport for ordinary actionable widgets is C2S185 carrying the widget id as a 16-bit value. This package does not claim a special Event-Chest packet family.

If a control is later found to have a client-local content-type interception, that specific widget must be reclassified rather than assumed to reach the server.

## ROOT CHILD PLACEMENT

High-value exact child placements include:

~~~
60601 @ 12,11
60603 @ 27,47
60604 @ 191,240
60605 @ 191,240
60608 @ 204,252
60607 @ 231,251
60611 @ 272,246
60612 @ 394,246
63740 @ 476,14
63741 @ 476,14
60616 @ 344,23
60625 @ 331,306
60626 @ 24,282
60628 @ 24,282
60630 @ 85,291
60609 @ 67,239
60610 @ 67,256
60613 @ 27,242
60621 @ 118,27
60622 @ 118,27
60624 @ 127,33
60617 @ 18,27
60618 @ 18,27
60620 @ 27,33
~~~

This proves client layout/presentation only.

## CLIENT-COMPATIBLE S2C PRESENTATION FAMILIES

The exact v308 global S2C census proves the client can receive generic updates suitable for this interface:

- S2C97 — open main interface root;
- S2C53 — full widget item-container update;
- S2C34 — partial widget item-container update;
- S2C126 — widget text/control update bus;
- S2C171 — widget visibility;
- S2C79 — widget scroll position;
- S2C219 — close interfaces.

This is a client-capability statement.

It does not by itself prove which combination the historical SpawnPK server used for every Event Chest state transition.

Current LocalLab definitely has reusable publishers for S2C97, S2C53, S2C126 and S2C219. Other widget publisher families remain in the S2C parity audit.

## SERVER SEMANTICS PROVEN

The client proves:

- there is an Event Chest interface;
- the interface has tiered presentation;
- it can show a large event-item grid;
- it has three small four-slot item grids;
- it presents Roll / Tier I Prize / tier progression text;
- it exposes Exchange;
- it exposes Enter next tier;
- it exposes Reset event items;
- it has Event Guide and Reset exchange presentation.

## SERVER SEMANTICS UNKNOWN

UNKNOWN_SERVER_AUTHORITY:

- actual event item definitions;
- actual prize tables;
- token item/currency identity;
- whether 50,000 tokens was production-current or only a static default;
- required rolls per tier;
- tier unlock rules;
- Exchange conversion rules;
- roll RNG/probabilities;
- pity/bad-luck mechanics;
- whether a roll consumes one item or an entire set;
- Reset event items behavior;
- Reset exchange behavior;
- persistence;
- anti-abuse;
- cooldowns;
- eligibility;
- rewards.

Do not promote the static strings into authoritative server mechanics.

## FILES / METHODS

Exact client:

~~~
rs.n.c.N#a()
~~~

Raw bytecode excerpt:

~~~
server/research/client_v308_coverage/event_chest/evidence/interface_builder_v308.txt
~~~

Related exact transport packages:

~~~
server/research/client_v308_coverage/s2c_census/
server/research/client_v308_coverage/c2s_census/
~~~

## READY FOR CHAT 2

partial

No Event-Chest-specific transport is required by the evidence so far. Generic widget/container publishers are the correct internal transport substrate.

Chat 2 should consume this only if publisher-parity work proves one of the required generic widget update families is genuinely missing.

## READY FOR CHAT 3

yes for UI composition, no for gameplay mechanics

Chat 3 can define a protocol-independent Event Chest domain/application model and project it onto these exact client widgets.

It must not infer reward tables, prices, roll odds or tier rules from the client defaults.