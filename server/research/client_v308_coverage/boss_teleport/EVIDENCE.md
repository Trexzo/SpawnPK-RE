# Evidence Package — Exact v308 Boss Teleportation Network

SYSTEM

SpawnPK Boss Teleportation Network client interface, selection surface, boss-information projection and drop-preview presentation.

STATUS

CLOSED-CLIENT-UI-CONTRACT / SERVER-DESTINATIONS-AND-MECHANICS-UNKNOWN

## AUTHORITY

Exact-current client:

~~~
SHA-256 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
build/config 308
class rs.n.c.o
~~~

## ROOT

Exact root:

~~~
18616
~~~

The root installs 31 children.

Primary title:

~~~
60410 -> @or1@Boss Teleportation Network
~~~

## BOSS SELECTION LIST

Exact selectable rows:

~~~
60412 -> Teleport #1
60413 -> Teleport #2
60414 -> Teleport #3
60415 -> Teleport #4
60416 -> Teleport #5
60417 -> Teleport #6
60418 -> Teleport #7
60419 -> Teleport #8
60420 -> Teleport #9
60421 -> Teleport #10
60422 -> Teleport #11
60423 -> Teleport #12
60424 -> Teleport #13
~~~

Every row is built with the exact tooltip:

~~~
Select this teleport
~~~

These are client placeholders/selection slots. The client does not prove which boss name/destination is assigned to each row by the production server.

## TELEPORT ACTION

Exact control:

~~~
60448
tooltip: Teleport to boss
hover/paired widget: 60449
visible label: 60451 -> Teleport <img=149>
~~~

The control is a normal actionable widget. The global exact-v308 input census proves ordinary widget actions are transported through C2S185 with the widget id.

This package does not claim a Boss-Teleport-specific C2S packet.

## BOSS INFORMATION / STATS

Main display placeholders:

~~~
60405 -> @or1@Name of Boss
60406 -> @or1@Bosses
60407 -> @or1@Description
60408 -> empty
60409 -> @or1@Possible drops and rewards
~~~

Scrollable information/stat panel:

~~~
60426
content height: 250
width: 165
height: 123
17 child lines
~~~

Exact shipped default lines include:

~~~
60427 -> @yel@Information & Stats:
60428 -> Combat level: @whi@100
60429 -> Wilderness level: @whi@Safe
60430 -> Combat zone: @whi@Single
60431 -> empty
60432 -> @yel@Available achievements:
60433 -> @yel@-@whi@ Placeholder I
60434 -> @yel@-@whi@ Placeholder II
60435 -> @yel@-@whi@ Placeholder III
60436 -> empty
60437..60443 -> Line 11 .. Line 17
~~~

Additional exact text widgets:

~~~
60444 -> Safe
60445 -> No
~~~

The shipped values 100 / Safe / Single / placeholder achievements are defaults only. They are not authoritative boss data.

## POSSIBLE-DROPS PREVIEW

Widget 60446 is a container/root holding item widget 60447.

60447 exact client allocation:

- six columns;
- two rows;
- 12 slots;
- horizontal spacing 15;
- vertical spacing 10.

Static initialization deliberately uses placeholders:

~~~
item id 995 in all 12 slots
quantities 1 through 12
~~~

This is strong negative evidence: the static coins are UI scaffolding, not the production boss drop table.

Server projection must replace/populate these slots from authoritative boss/drop data.

## FULL DROP TABLE CONTROL

Exact control family:

~~~
39873
tooltip: View full drop table
hover/paired widget: 39874
label: 39876 -> @yel@View All
~~~

This is a distinct action from teleporting to the selected boss.

## HIGH-VALUE ROOT PLACEMENT

~~~
60404 @ 7,16
60405 @ 321,54
60406 @ 73,51
60407 @ 319,80
60408 @ 281,183
60409 @ 190,234
60410 @ 279,23
60411 @ 180,136
60412..60424 @ x=-3, y=75+(18*n), n=0..12
60448 @ 172,189
60449 @ 172,189
60451 @ 222,199
60426 @ 296,101
60446 @ 135,253
63740 @ 483,22
63741 @ 483,22
39873 @ 390,234
39874 @ 390,234
39876 @ 426,235
~~~

## CLIENT-COMPATIBLE S2C PRESENTATION

The exact global S2C census provides generic primitives suitable for server-driven Boss Teleport state:

- S2C97 — open main interface;
- S2C126 — text/control updates for boss name, description, stats and labels;
- S2C53/S2C34 — full/partial item-container updates for the 12-slot drop preview;
- S2C79 — scroll position where needed;
- S2C171 — visibility;
- S2C219 — close interfaces.

Current LocalLab already has reusable publisher capability for S2C97, S2C126, S2C53 and S2C219.

## CURRENT LOCALLAB GAP

A current-main repository search found no dedicated references for:

~~~
18616
60412
60448
Boss Teleportation Network
~~~

So there is no dedicated current-main Boss Teleport adapter/application layer at the time of this package.

## SERVER SEMANTICS PROVEN

The exact client proves:

- a Boss Teleportation Network application exists;
- it has 13 selectable destination rows;
- it has a distinct Teleport-to-boss action;
- it presents boss name/description/stat information;
- it presents Wilderness/safety/combat-zone information fields;
- it presents achievement lines;
- it presents a 12-slot possible-drops/rewards preview;
- it offers a separate View full drop table action.

## SERVER SEMANTICS UNKNOWN

UNKNOWN_SERVER_AUTHORITY:

- row -> boss identity mapping;
- boss destination coordinates;
- teleport requirements;
- teleblock/Wilderness restrictions;
- fees;
- cooldowns;
- boss combat levels and zone classifications for real rows;
- achievement definitions;
- drop-table contents/rates;
- reward policy;
- whether all 13 slots are always active;
- unlocks/progression;
- persistence/favorites/history;
- anti-abuse.

Do not promote the client placeholder texts or coin preview into server authority.

## READY FOR CHAT 2

yes for generic transport only

No bespoke Boss-Teleport packet is required by the evidence. Existing semantic widget/container publication should remain the transport substrate.

## READY FOR CHAT 3

yes

Chat 3 can build a protocol-independent BossTeleportCatalog / TeleportDestination projection using these 13 client selection slots and the information/drop-preview widgets. Destination coordinates, requirements and drop mechanics must come from separate authority or explicit LOCAL_LAB_POLICY.