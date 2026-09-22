# Evidence Package — PK Ratings + Daily/Tournament PK Leaderboard Navigation (Exact v308)

SYSTEM

SpawnPK PK Ratings tab, dynamic ratings rows, and the Daily PK / Tournament PK
leaderboard selection menu.

STATUS

STRONG-PARTIAL / NAVIGATION + DYNAMIC ROW TRANSPORT CLOSED

## AUTHORITY

Exact-current client:

```
SHA-256 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
build/config 308
```

Exact classes:

```
rs.n.c.Y
rs.n.c.aM
rs.n.c.aN
rs.n.c.Z
rs.q.a.a.b
rs.q.a.a.a
rs.Client
rs.x.e
```

Authority:

`EXACT_CURRENT_CLIENT`

## GAMEFRAME PK RATINGS TAB

The custom gameframe/tab root is:

```
32000
```

Exact PK Ratings tab button:

```
32017
tooltip "Open pk ratings"
```

Visible title after selection:

```
<img=253> PK Ratings
```

Exact client-local target root:

```
40403
```

### Important local-navigation behavior

On menu action for widget 32017, exact `rs.Client` calls:

```
rs.n.c.Y.m(32017)
```

before sending the generic widget action.

That local handler immediately:

- marks 32017 selected;
- changes title widget 32014 to `<img=253> PK Ratings`;
- assigns sidebar slot 2 to root 40403;
- marks the tab/UI dirty for redraw.

The same action path then still emits:

```
C2S185(32017)
```

Exact body:

```
32017 -> 7D 11
```

Therefore the visible PK Ratings tab transition is **client-local and
immediate**, while the server still receives the selection action.

The server does not need to send an interface-open packet merely to make the
gameframe switch to 40403 after this native tab click.

## PK RATINGS ROOT 40403

Exact builder:

```
rs.n.c.aM
```

Root:

```
40403
```

It installs six direct children, including:

```
40402  background/gameframe sprite
16022  decorative/shared widget (placed twice)
16023  decorative/shared widget
32000  the custom gameframe/tab strip
40404  ratings scroll root
```

## RATINGS SCROLL

Exact scroll root:

```
40404
```

Static capacity:

```
50 rows
```

Exact row widget range:

```
40405..40454
```

The builder initially creates all 50 rows as blank/nonselectable rows.

Row text can also be updated directly through the exact static helper:

```
aM.k(rowIndex, text)
 -> H[40405 + rowIndex].text = text
```

## S2C250 SUBTYPE 16 — EXACT PK RATINGS DATA BUS

The SpawnPK application registry `rs.q.a.a.b` maps:

```
subtype 16
 -> rs.n.c.aM.c
 -> concrete parser rs.n.c.aN
```

Exact top-level S2C250 behavior is:

```
S2C250 VAR_BYTE
u16_be application subtype
application payload
```

For PK Ratings:

```
subtype = 16
```

### Operation 0 — clear/reset

Payload body after subtype:

```
u8 0
```

Exact client effect:

- resets next-row/widget counter to 40405;
- resets scroll child insertion index;
- clears text for all 50 rows;
- resets row font/reference to the default first font.

### Operation 1 — append/build next row

Exact body grammar after subtype:

```
u8 1
u8 fontIndex
u8 selectableFlag
string_nl text
```

The client:

- allocates the next sequential row widget starting at 40405;
- uses the supplied font index;
- stores the supplied text;
- if `selectableFlag == 1`, creates the row as an action-enabled text widget
  with tooltip `Select`;
- otherwise creates a non-action text row;
- appends the row to scroll 40404.

This gives exact server control over whether each PK Ratings row is clickable.

### Operation 2 — update existing row text

Exact body grammar after subtype:

```
u8 2
u16_be rowIndex
string_nl text
```

Exact client effect:

```
H[40405 + rowIndex].text = text
```

The client does not prove acceptable server row-index bounds beyond the static
50-row construction; server publication should remain within the known
0..49 range unless separate evidence proves otherwise.

## SELECTABLE PK RATING ROW ACTIONS

When operation 1 creates a row with `selectableFlag == 1`, the exact helper
creates an action-enabled text widget:

```
tooltip "Select"
aI = 4
M  = 1
```

Therefore selecting such a row uses:

```
menu action 315
 -> C2S185(rowWidgetId)
```

Static row-id range:

```
40405..40454
```

Example exact bodies:

```
40405 -> 9D D5
40454 -> 9E 06
```

The exact client proves row-selection capability.

It does not prove what semantic entity a row represents or what selecting it
should do.

## DAILY / TOURNAMENT PK LEADERBOARD SELECTION MENU

Exact root:

```
61000
```

Static labels:

```
61008  "Daily PK Leaderboards"
61009  "Tournament Leaderboard"
61010  "Leaderboard Selection Menu"
```

### Daily PK action

Widget:

```
61002
```

tooltip:

```
Daily PK leaderboards
```

Exact C2S185 body:

```
EE 4A
```

### Tournament PK action

Widget:

```
61005
```

tooltip:

```
Tournament PK leaderboard
```

Exact C2S185 body:

```
EE 4D
```

Both use the ordinary action-enabled sprite helper and action315 -> C2S185.

No exact client-local root switch comparable to `Y.m(32017)` was identified
for 61002/61005 in this pass. The server/application layer therefore remains
responsible for whatever data/interface response follows those selector clicks.

## DISTINCT FROM WORLD TOURNAMENT LEADERBOARDS

Do not conflate:

```
61000  Daily PK / Tournament PK selection menu
```

with the separately recovered World Tournament Leaderboards root:

```
61011
```

The `61011` interface contains Top Players / Top Clans and weekly/all-time
World Tournament filters.

The `61000` interface is a different selector surface for Daily PK and
Tournament PK leaderboard categories.

## SERVER SEMANTICS PROVEN

Exact client proves:

- PK Ratings gameframe action widget 32017;
- local switch to sidebar root 40403;
- server still receives C2S185(32017);
- root 40403 contains a 50-row dynamic scroll;
- exact S2C250 subtype 16 owns PK Ratings row publication;
- subtype16 operation 0 clear;
- operation1 append with font/selectable/text;
- operation2 update row text;
- selectable rows emit C2S185;
- separate leaderboard-selection root 61000;
- exact Daily PK / Tournament PK selector action ids.

## SERVER SEMANTICS UNKNOWN

`UNKNOWN_SERVER_AUTHORITY`:

- PK rating formula;
- ELO/MMR/rating meaning;
- ranking sort;
- displayed row grammar from production;
- what selectable rows target;
- player-profile/details action;
- Daily PK score formula;
- Daily reset boundary/timezone;
- Tournament PK scoring relationship to World Tournament;
- leaderboard persistence;
- tie-breaking;
- minimum activity;
- anti-farm/anti-abuse;
- privacy;
- rewards;
- season/reset behavior.

## LOCAL LAB MAPPING

No current-main file named for Ratings/Leaderboard/Tournament was found in the
production source tree during this read-only parity pass.

Existing generic foundations suitable for composition include:

- semantic player identities;
- CombatOutcome / kill facts;
- Objective/progression foundations;
- Match/Tournament foundations;
- generic S2C250 writer.

A semantic leaderboard service should own ranking entries/scores.

The client adapter should own:

- S2C250 subtype16 row operations;
- row widget ids;
- C2S185 row selection;
- root 40403 / selection root 61000 presentation.

## TEST VECTORS

### C2S185

```
Open/select PK Ratings tab:
32017 -> 7D 11

Daily PK leaderboard:
61002 -> EE 4A

Tournament PK leaderboard:
61005 -> EE 4D

Selectable rating rows:
40405 -> 9D D5
...
40454 -> 9E 06
```

### S2C250 subtype 16

```
Clear:
00

Append:
01 <font:u8> <selectable:u8> <text bytes> 0A

Update row:
02 <rowIndex:u16_be> <text bytes> 0A
```

These bodies appear after the S2C250 subtype field:

```
00 10
```

for subtype 16.

## READY FOR CHAT 2

**yes**

Existing generic S2C250 transport is sufficient.

No new wire family is required.

## READY FOR CHAT 3

**yes for presentation/data-feed contract**

Chat 3 can implement semantic PK Ratings / leaderboard state without further
client reverse engineering, while keeping formula/rewards/reset policy
evidence-gated.
