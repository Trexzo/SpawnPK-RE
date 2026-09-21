# Evidence Package — World Tournament + Leaderboards (Exact v308)

SYSTEM

SpawnPK World Tournament hub and World Tournament Leaderboards.

STATUS

STRONG-PARTIAL / CLIENT CONTRACT CLOSED FOR ROOTS, ACTION WIDGETS AND NATIVE C2S BUTTON TRANSPORT

## AUTHORITY

Primary exact-current client:

```
SHA-256 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
build/config 308
```

Exact client classes:

```
rs.n.c.aX   World Tournament hub
rs.n.c.aW   World Tournament Leaderboards
rs.n.e      widget-construction helpers
rs.Client   menu/action dispatch
rs.x.e      packet buffer transforms
```

Authority:

`EXACT_CURRENT_CLIENT`

No original server tournament scheduling, eligibility, bracket, reward or
leaderboard-ranking policy is inferred here.

## IMPORTANT ROOT CORRECTION

The main World Tournament interface root is:

```
27400
```

not `56000`.

Exact `rs.n.c.aX.a()` begins with:

```
d(27400)
```

and then constructs the tournament children in the `56000+` range.

Therefore the exact presentation relationship is:

```
main tournament root = 27400
child/widget family   = 56000+
```

Code or research that treats `56000` itself as the root should be corrected.

The leaderboard root is directly:

```
61011
```

## WORLD TOURNAMENT HUB — EXACT CLIENT WIDGETS

Exact hub construction in `rs.n.c.aX.a()` includes:

```
27400  main root

56000  background sprite: "tournament/sprite 0"
56001  title: "<img=128> SpawnPK World Tournaments <img=128>"
56002  dynamic-style title text:
       "Next world tournament: @yel@Dharok PK Tournament"
56003  blank text row
56004  "This tournament's prize will be.."
56005  "@yel@Previous Tournament Winners"
56006  "@yel@Tournament point shop"

56007  item-grid style prize widget
56008  previous-winners scroll root
56009..56043  35 winner/history text rows

56044  Enter tournament button
56045  Enter hover sprite
56047  "Enter Tournament <img=51>"
56048  "Receive points for placing\ntop 5 in a tournament"

56049  Spectate tournament button
56050  Spectate hover sprite
56052  "Spectate Tournament"

56053  Tournament shop button
56054  Shop hover sprite
56056  "Shop"
56057  "fountain/coins" sprite

63740 / 63741  shared close-control widgets included by the root
```

The exact hub root installs 22 child entries.

### Prize widget 56007

`rs.n.c.aX` creates widget 56007 using the client's item/inventory-style
helper, then configures:

```
width  = 4
cell spacing / layout fields = 11 / 10
first item-array value = 16002
first quantity value   = 1
```

This proves the native client has an item-grid style prize presentation slot and
ships a static/default client entry.

It does **not** prove:

- that 16002 is always the production tournament reward;
- the production server reward-selection policy;
- reward quantity policy;
- whether production updated this widget through S2C53 or another application
  path.

S2C53 is client-compatible with item-container widgets, but exact production
use for this tournament slot remains to be proven separately.

## HUB ACTION TRANSPORT — EXACT

The three hub action widgets are constructed through:

```
rs.n.e.a(
    widgetId,
    spriteName,
    spriteIndex,
    width,
    height,
    tooltip,
    ...,
    hoverWidget,
    1
)
```

The exact helper sets:

```
widget.aI = 5
widget.M  = 1
widget.Q  = tooltip
```

Exact `rs.Client` menu construction converts this actionable widget form to
menu action:

```
315
```

Exact menu-action 315 handling then emits:

```
C2S185
body = widgetId via rs.x.e.d(int)
```

Exact `rs.x.e.d(int)` is two-byte big-endian.

Therefore:

### Enter Tournament

```
widget: 56044
C2S:    185
body:   DA EC
```

### Spectate Tournament

```
widget: 56049
C2S:    185
body:   DA F1
```

### Tournament Shop

```
widget: 56053
C2S:    185
body:   DA F5
```

These are exact client input contracts.

The client does not prove what the server must do after receiving each action.

## PREVIOUS WINNER/HISTORY PRESENTATION

Widget 56008 is a scroll container with 35 text children:

```
56009..56043
```

The client installs placeholder/example strings in these rows.

The exact production data source, history retention and winner/reward semantics
remain server authority.

Because these rows are normal text widgets, current exact S2C126 capability can
target their widget ids for text updates. That establishes a compatible
presentation path, not proof of the original production publisher.

## WORLD TOURNAMENT LEADERBOARDS — EXACT CLIENT WIDGETS

Exact root:

```
61011
```

Core widgets:

```
61012  background sprite "misc/hs 5"
61013  title "World Tournament Leaderboards"

61014  "<img=14> Top Players"
61015  "<img=16> Top Clans"

61016  Players: "This week"
61017  Players: "All time"
61018  player-side selector sprite
61019  player-side selector sprite

61020  Clans: "This week"
61021  Clans: "All time"
61022  clan-side selector sprite
61023  clan-side selector sprite

61024  player leaderboard scroll root
61025..61050  26 player-side text widgets

61051  clan leaderboard scroll root
61052..61077  26 clan-side text widgets

63740 / 63741  shared close controls
```

The root installs 16 direct children.

The layout contains player and clan leaderboard regions simultaneously; the
`Top Players` / `Top Clans` headings are ordinary text widgets rather than
proven action widgets.

## LEADERBOARD FILTER ACTION TRANSPORT — EXACT

Widgets 61016/61017/61020/61021 are created with the action-enabled text helper:

```
rs.n.e.a(
    id,
    text,
    tooltip,
    fonts,
    ...,
    width,
    ...
)
```

The exact helper sets:

```
widget.aI = 4
widget.M  = 1
widget.Q  = tooltip
```

That form enters the same exact menu action 315 path and emits C2S185(widgetId).

Exact filters:

### Player leaderboard — This week

```
widget: 61016
tooltip: "View weekly leaderboard"
C2S185 body: EE 58
```

### Player leaderboard — All time

```
widget: 61017
tooltip: "View all time leaderboard"
C2S185 body: EE 59
```

### Clan leaderboard — This week

```
widget: 61020
tooltip: "View weekly leaderboard"
C2S185 body: EE 5C
```

### Clan leaderboard — All time

```
widget: 61021
tooltip: "View all time leaderboard"
C2S185 body: EE 5D
```

## SERVER-TO-CLIENT PRESENTATION CAPABILITY

Exact client-compatible presentation paths now established:

- S2C97 can open root 27400 or 61011;
- S2C126 can update ordinary text widgets such as:
  - 56002/56003;
  - 56009..56043;
  - 61025..61050;
  - 61052..61077;
- S2C53 is structurally compatible with item-container style widgets such as
  prize widget 56007, but original production use for this slot is not yet
  proven.

No dedicated tournament-only S2C opcode is proven by this interface-builder
slice.

Application packet 250 / S2C126 may still participate elsewhere in the runtime;
that requires separate callsite evidence and must not be assumed.

## SERVER SEMANTICS PROVEN

Exact client proves only:

- the two interface roots;
- the widget hierarchy;
- the visible labels/tooltips/default presentation;
- Enter/Spectate/Shop action widget ids;
- weekly/all-time filter widget ids;
- exact C2S185 action transport;
- text-row and item-grid presentation structure.

## SERVER SEMANTICS UNKNOWN

`UNKNOWN_SERVER_AUTHORITY`:

- tournament schedule;
- tournament type rotation;
- whether "Dharok PK Tournament" is merely a client placeholder or a production
  default;
- eligibility;
- entry requirements;
- player limits;
- matchmaking;
- bracket/round structure;
- map/instance selection;
- death/elimination rules;
- spectator permissions;
- placement calculation;
- tournament points formula;
- point-shop catalog/prices;
- reward selection;
- previous-winner retention;
- leaderboard ranking formula;
- weekly reset boundary;
- all-time persistence policy;
- clan-score aggregation.

Chat 3 must not present any of those as recovered SpawnPK behavior until separate
authority exists.

## LOCAL LAB MAPPING

Existing semantic substrates that appear suitable:

```
MatchSessionService
GlobalEventService
Party / matchmaking / WorldInstance foundations
Objective / reward infrastructure
```

This is only an architectural handoff. It does not prove original Tournament
server rules.

## TEST VECTORS

Exact C2S185 bodies:

```
Enter       56044 -> DA EC
Spectate    56049 -> DA F1
Shop        56053 -> DA F5

Players week     61016 -> EE 58
Players all-time 61017 -> EE 59
Clans week       61020 -> EE 5C
Clans all-time   61021 -> EE 5D
```

Opcode encryption/ISAAC framing occurs outside these two-byte packet bodies.

## READY FOR CHAT 2

**yes**

No new raw transport family is needed. All proven action inputs use the already
known C2S185 widget-action transport.

Chat 2 only needs to preserve typed widget-action delivery and normal S2C
publisher capability.

## READY FOR CHAT 3

**yes for client contract; no for original gameplay rules**

Chat 3 can now build a semantic Tournament adapter without depending on raw
client archaeology, while keeping the unknown production rules explicitly
policy/evidence-gated.
