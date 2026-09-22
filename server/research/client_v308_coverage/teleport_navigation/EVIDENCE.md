# Evidence Package — Exact v308 Spellbook Teleport Navigation

SYSTEM

SpawnPK exact-current spellbook teleport entry/navigation layer across the native magic books.

STATUS

CLOSED-STATIC-CLIENT/CACHE-NAVIGATION-CONTRACT / DYNAMIC-SERVER-PRESENTATION-UNKNOWN / SERVER-COORDINATES-UNKNOWN

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

Exact v308 now proves at least two Home presentation entries:

~~~
1195  -> primary/classic Home Teleport entry
12856 -> Ancient-spellbook Home Teleport entry
~~~

Both are built with the exact label:

~~~
Cast @gre@Home Teleport
~~~

The Ancient builder additionally sets widget `12856.ab = 1196`, linking it to the classic Home informational/content path.

The classic Home help presentation also includes:

~~~
1197  -> Level 0: Home Teleport
1198  -> A teleport which requires no
18998 -> runes and no required level that
18999 -> teleports you to the main land.
~~~

These strings are exact client presentation. They do not establish production server cooldowns, coordinates, combat restrictions or eligibility rules.

A third distinct Lunar Home alias is not proven by this slice. Do not invent one merely to force a three-spellbook alias table.


## DESTINATION-SUBMENU NEGATIVE AUTHORITY

A whole exact-v308 class-string scan was performed across `rs.*` after the entry atlas was built.

Result:

- the hardcoded Money / Training / PK / Minigame / House category labels and descriptions occur in `rs.n.c.ap`;
- no second exact client class exposes a static destination-name catalogue for those five category menus;
- Boss is the explicit exception: it has its own dedicated `rs.n.c.o` Boss Teleportation Network interface, already packaged separately.

The exact widget-action dispatcher was then traced for the seven custom category aliases.

For Money, Training, Boss, PK, Minigame and House, no client-local submenu-opening branch is executed. They fall through the generic path:

~~~
C2S185(widgetId)
~~~

Bounty/Target is the only recovered category with client-local interception before that generic send: when its lock state is active the request is blocked locally and the locked message is displayed.

Therefore the exact-current evidence supports this boundary:

~~~
spellbook category widget
  -> C2S185 semantic category intent
  -> server decides / projects destination UI or destination action
~~~

For Money / Training / PK / Minigame / House specifically, there is currently **no exact-current client authority for a hardcoded destination catalogue**.

This is a useful negative finding: do not reconstruct destination names by importing generic RSPS/OSRS teleport lists and label them SpawnPK authority.

A direct exact-current cache/interface-archive scan was then completed.

## CACHE-BACKED INTERFACE ARCHIVE SCAN

Exact startup mapping in `rs.Client.L()` proves:

~~~
archive 1 -> title
archive 2 -> config
archive 3 -> interface
archive 4 -> media
~~~

The exact widget loader is:

~~~
rs.n.e.a(interfaceArchive, fonts, mediaArchive)
~~~

and reads:

~~~
interfaceArchive.a("data")
~~~

from archive 3.

The matched cache index-0 archive 3 is exactly:

~~~
184,196 bytes
~~~

Using the exact client's own `rs.x.f` decompressor, its `data` member expands to:

~~~
784,354 bytes
~~~

The first exact widget count is:

~~~
18,516
~~~

and `rs.n.e.H` is allocated as:

~~~
18,516 + 70,000 = 88,516 widgets
~~~

The base-cache widget table was traversed by exact parent/root relationship:

~~~
aw = widget id
ah = parent/root id
~~~

### Result

No base-cache widget text/action entry contains the SpawnPK destination-category strings:

~~~
Money Making
Training Teleports
Training & Slayer
PK Teleports
Minigame Teleports
Teleport to House
Boss Teleports
Bounty Target
~~~

The teleport/travel-bearing static cache roots that do exist are unrelated legacy/base interfaces:

~~~
1151  classic/modern magic spellbook teleport/help content
12855 ancient magic spellbook teleport/help content
12468 player teleport-request accept/decline dialogue
18220 canoe transport destination selector
3281  ship/travel map presentation
15712 unrelated narrative/book content containing travel text
~~~

For example, root `18220` is the exact canoe interface with destinations such as Lumbridge, Champions Guild, Barbarian Village, Edgeville and Wilderness Pond. It is not a SpawnPK custom teleport-category submenu.

At the later custom-builder failure point, the spellbook aliases still retain their raw base-cache values (for example `1164 = Cast @gre@Varrock teleport` and `13035` retains its Ancient spell value), proving this scan reflects the unmodified base/cache interface table before `rs.n.c.ap` overwrites the custom teleport entry layer.

Raw evidence:

~~~
server/research/client_v308_coverage/teleport_navigation/evidence/cache_interface_scan_v308.txt
~~~

### Static authority conclusion

The prior JAR scan plus this cache scan now close the static exact-current boundary:

~~~
custom teleport category alias
  -> C2S185 semantic category intent
  -> server decides/directly acts OR projects a dynamic/generic destination UI
~~~

There is no exact-current Java **or static cache-interface** destination catalogue for Money / Training / PK / Minigame / House.

Therefore do not import generic RSPS/OSRS destination lists and label them recovered SpawnPK authority.

Still possible:

- a generic/cache root whose rows are populated dynamically by server text/model packets;
- a direct server-side teleport/action response;
- another runtime-selected presentation path.

Those require server/runtime observation, not more static client/cache guessing.

## SERVER SEMANTICS PROVEN

The exact client proves:

- Home, Money, Training/Skill, Boss, PK, Minigame, House, and Bounty/Target are first-class teleport navigation concepts;
- seven custom categories have multiple cross-spellbook widget aliases; Home additionally has exact primary `1195` and Ancient alias `12856` presentation entries;
- the aliases converge on the same semantic client concept;
- ordinary entry activation uses generic C2S185 widget-action transport;
- Bounty/Target teleport has a special client-local lock interception state;
- exact labels/descriptions/sprites are recoverable presentation data.

## SERVER SEMANTICS UNKNOWN

UNKNOWN_SERVER_AUTHORITY:

- world coordinates;
- region IDs;
- destination lists behind Money/Training/PK/Minigame/House; neither Java-hardcoded nor static cache-interface catalogues exist in the exact-current client/cache, so any such lists require server/runtime proof;
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

Static current-client/cache recovery for the custom category layer is now closed.

Remaining teleport questions are runtime/server-authority questions:

1. capture a real server response after Money/Training/PK/Minigame/House category activation if historical/runtime authority becomes available;
2. if that response opens a generic interface, record the runtime root and exact S2C row/model updates;
3. recover any additional Home alias only if directly evidenced;
4. keep coordinates, destination catalogues and restrictions server-owned until independently evidenced.

Further blind static searches for a second destination catalogue are not justified by the exact-current evidence.