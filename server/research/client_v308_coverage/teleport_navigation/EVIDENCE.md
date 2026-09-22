# Evidence Package — Exact v308 Spellbook Teleport Navigation

SYSTEM

SpawnPK exact-current spellbook teleport entry/navigation layer across the native magic books.

STATUS

CLOSED-ENTRY-LAYER / DESTINATION-SUBMENUS-PARTIAL / SERVER-COORDINATES-UNKNOWN

## AUTHORITY

Exact-current client:

~~~
client(6).jar
SHA-256 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
build/config 308
~~~

Primary exact client classes:

~~~
rs.n.c.ap
rs.n.c.ap$a
rs.n.c.ap$b
rs.n.c.ap$c
rs.Client
~~~

## SPELLBOOK ORGANIZATION

`rs.n.c.ap$a` exposes three exact categories:

~~~
COMBAT
TELEPORT
UTILITY
~~~

`rs.n.c.ap$c` exposes three exact spellbook identities:

~~~
MODERN
LUNAR
ANCIENTS
~~~

`rs.n.c.ap$b` defines the client spell entries and carries:

~~~
P = widget id
Q = client-visible magic-level/filter requirement
R = COMBAT / TELEPORT / UTILITY
~~~

The teleport entries below are all category `TELEPORT`.

## EIGHT FIRST-CLASS TELEPORT ENTRIES

| Semantic entry | Primary widget | Client Q value |
|---|---:|---:|
| Home Teleport | 1195 | 100 |
| Money Teleport | 1164 | 100 |
| Skill / Training Teleport | 1167 | 100 |
| Boss Teleport | 1170 | 100 |
| PK Teleport | 1174 | 100 |
| Minigame Teleport | 1540 | 100 |
| House Teleport | 1541 | 100 |
| Bounty / Target Teleport | 7455 | 100 |

### Authority boundary for Q=100

The value `100` is exact-current client data used by the spellbook filtering/requirement presentation path.

It is **not** sufficient evidence that the original SpawnPK server required Magic level 100 to use every teleport.

Treat it as:

~~~
EXACT_CURRENT_CLIENT spellbook filter metadata
~~~

not recovered server eligibility policy.

## CROSS-SPELLBOOK WIDGET ALIASES

The same semantic custom teleport entries are represented by separate widgets across the three spellbook surfaces.

### Money

~~~
1164
13035
30064
~~~

### Training / Skill

~~~
1167
13045
30075
~~~

### Boss

~~~
1170
13053
30083
~~~

### PK

~~~
1174
13061
30106
~~~

### Minigame

~~~
1540
13069
30114
~~~

### House

~~~
1541
13079
30138
~~~

### Bounty / Target

~~~
7455
13095
30162
~~~

These should normalize into one semantic destination-category intent per row.

Do not expose the alias widget IDs to gameplay/content APIs.

## EXACT CLIENT LABELS / DESCRIPTIONS

Recovered exact-current visible strings include:

### Money

~~~
@gre@Money Making
Teleport to money areas

@gre@Money Making Teleports
~~~

### Training

~~~
@gre@Training & Slayer
Teleport to various monsters

@gre@Training Teleports
~~~

### Boss

~~~
@gre@Boss Teleports
Teleport to powerful foes
~~~

### PK

~~~
@gre@PK Teleports
Teleport Pking spots
~~~

### Minigame

~~~
@gre@Minigame Teleport
Teleport to shop areas

@gre@Minigame Teleports
~~~

The exact shipped client therefore contains a presentation inconsistency:

- the semantic entry is Minigame;
- the icon/description path uses shop-oriented legacy assets/text in at least one spellbook construction.

Do **not** silently correct that inconsistency and do not infer a Shop teleport server category from the stale presentation string.

### House

~~~
@gre@Teleport to House
Teleport to your PoH
~~~

### Bounty

~~~
@gre@Teleport to Target
Teleports you to Bounty Target

@gre@Teleport to Bounty Target
~~~

### Home

~~~
Cast @gre@Home Teleport
~~~

## EXACT CLIENT SPRITE / ASSET REFERENCES

Recovered presentation assets include:

~~~
icons/moneytele
icons/skilltele
icons/bosstele
icons/pktele
icons/storetele
magic/home
magic/home 2
magic/bounty
~~~

`icons/storetele` is used in the exact Minigame presentation path despite its legacy asset name.

Asset names are presentation evidence, not server semantics.

## EXACT INPUT ROUTING

For ordinary teleport-entry widget activation, the exact client routes through:

~~~
C2S185(widgetId)
~~~

Therefore the semantic server adapter should normalize widget aliases into intents such as:

~~~
OpenMoneyTeleports
OpenTrainingTeleports
OpenBossTeleports
OpenPkTeleports
OpenMinigameTeleports
OpenHouseTeleport
TeleportToBountyTarget
HomeTeleport
~~~

Names are illustrative; the important rule is that raw widget IDs remain in the presentation/input adapter.

## BOUNTY CLIENT-LOCK EXCEPTION

The exact client has special handling for the three Bounty/Target aliases:

~~~
7455
13095
30162
~~~

When the relevant client lock state is active, activation is intercepted locally and the client displays:

~~~
<img=25> You currently have this spell locked!
~~~

instead of sending the ordinary C2S185 widget action.

The same Bounty teleport entries also participate in client Lock/Unlock `Teleport to target` menu behavior.

This proves a client-visible lock state.

It does **not** prove original server unlock requirements, purchase costs, cooldowns, Bounty eligibility, or target-selection policy.

## HOME TELEPORT

`1195` is an exact primary Home Teleport entry.

Additional Home aliases across every spellbook surface are not normalized by this package yet.

Do not invent alias IDs merely to force an eight-row triple table.

## SERVER SEMANTICS PROVEN

The exact client proves:

- Home, Money, Training/Skill, Boss, PK, Minigame, House, and Bounty/Target are first-class teleport navigation concepts;
- seven custom categories have multiple cross-spellbook widget aliases;
- the aliases converge on the same semantic client concept;
- ordinary entry activation uses generic C2S185 widget-action transport;
- Bounty/Target teleport has a special client-local lock interception state;
- exact labels/descriptions/sprites are recoverable presentation data.

## SERVER SEMANTICS UNKNOWN

UNKNOWN_SERVER_AUTHORITY:

- world coordinates;
- region IDs;
- destination lists behind Money/Training/PK/Minigame menus;
- server-side Magic requirements;
- costs;
- Wilderness restrictions;
- teleblock restrictions;
- combat restrictions;
- cooldowns;
- unlock criteria;
- Bounty target eligibility;
- safe-zone policy;
- instance admission;
- House ownership/admission behavior;
- persistence of any teleport unlocks.

## ARCHITECTURE CONSEQUENCE

Chat 3 should model semantic teleport destinations/categories, for example:

~~~
TeleportCategory
TeleportDestination
TeleportRequirementPolicy
TeleportService
~~~

with an adapter:

~~~
widget alias -> semantic teleport intent -> TeleportService
~~~

Coordinates and restrictions must be server-owned data/rules and must not live in widget handlers.

## READY FOR CHAT 2

yes — existing generic C2S185 typed widget-action transport is sufficient for the entry layer

## READY FOR CHAT 3

yes for category/navigation composition; destination coordinates and server restrictions remain unresolved

## NEXT EXACT-CLIENT WORK

1. resolve destination submenu roots/buttons for Money;
2. Training/Slayer;
3. PK;
4. Minigame;
5. House route/presentation where server-visible;
6. normalize Home aliases if directly proven;
7. link already-packaged Boss Teleport contract into the unified atlas.