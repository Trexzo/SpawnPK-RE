# Evidence Package — Exact v308 S2C126 Application / Text Bus Boundary

SYSTEM

Exact-current SpawnPK S2C126 multiplexed text/application-state target dispatch.

STATUS

CLOSED-SPECIAL-TARGET-DISPATCH / PARTIAL-TARGET1-TOKEN-BUSINESS-SEMANTICS

## AUTHORITY

Exact-current client:

~~~text
client(6).jar
SHA-256 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
build/config 308
~~~

Normalized exact research sources:

~~~text
server/research/r84/client_static_r3_1/01_S2C126_UPDATE_BUS.md
research/S2C126_TYPED_TARGETS_15_71_R2.csv
server/research/r84/client_static_r3_1/tables/s2c126_argument_control_routes.csv
server/src/spk/local/data/research_r82/service_s2c126_keys_r82.tsv
~~~

## WIRE CONTRACT

Current LocalLab already has the exact packet framing:

~~~text
S2C126
VAR_SHORT
newline-terminated ISO-8859-1 string
then T() / BE-short-low-Add128 target key
~~~

The exact client does **not** treat every target as ordinary widget text.

Before ordinary text assignment it executes a multiplexed application/control dispatch.

## FINITE SPECIAL TARGET RANGE

The exact special-target surface reaches:

~~~text
0..71
~~~

After those special branches, positive ordinary widget ids fall through to normal widget-text behavior where applicable.

The target map is no longer open-ended.

### Targets 0–14

| Target | Exact/current family |
|---:|---|
| 0 | URL / quick-state special control family |
| 1 | global string control-token namespace |
| 2–9 | Bounty Hunter overlay/state fields |
| 10 | Blood Fountain perk-tree activate/highlight |
| 11 | Blood Fountain secondary perk state |
| 12 | Blood Fountain deactivate/unhighlight |
| 13 | Blood Diamond Fuser structured slot state |
| 14 | timed effect/status channel |

Target 14 consumes exact timed-effect identity/duration state; it is distinct from S2C250 subtype 19's named/dynamic infobox/effect presentation.

### Targets 15–71

The exact-current normalized map contains **57 numeric positions** from 15 through 71 inclusive.

Of these:

~~~text
54 have explicit special branches/effects
3 are explicit no-dedicated-branch holes: 50, 51, 52
~~~

There is no basis to densify or renumber those holes.

## NORMALIZED TARGET 15–71 INDEX

| Target | Exact normalized semantic/effect |
|---:|---|
| 15 | Bank tab content state |
| 16 | selected Bank tab |
| 17 | friend-list clear/removal reconciliation |
| 18 | gambling game-type selection |
| 19 | HP orb normal/poison/venom fill |
| 20 | widget model/media state |
| 21 | dynamic widget sprite |
| 22 | Task progress hover text |
| 23 | Broadcast banner text |
| 24 | equipment-hover detail cache |
| 25 | CombatOverlay target/HP state |
| 26 | Daily Money Making tracking text |
| 27 | gambling/control selected state |
| 28 | Clan Wars begin countdown |
| 29 | Clan Wars team counts |
| 30 | Clan Wars Fighters/Kills label mode |
| 31 | special-attack orb percentage/value |
| 32 | local-player prayer headicon |
| 33 | remote-player prayer headicon |
| 34 | scene-tile entity suppression |
| 35 | hit/block popup record update |
| 36 | Daily Money Making selection |
| 37 | Blood Pool shop slot append |
| 38 | Item Enchantment stage/state |
| 39 | World Tournament phase/deadline |
| 40 | generic widget boolean false |
| 41 | generic widget boolean true |
| 42 | Bounty overlay scalar |
| 43 | current pet-loadout pair |
| 44 | text colour + optional shadow colour |
| 45 | text-colour submit command prefix |
| 46 | raid overlay points/total |
| 47 | debug/log text only |
| 48 | PvP Hotspot deadline/duration |
| 49 | PvP Hotspot detail body text |
| 50 | **no dedicated branch** |
| 51 | **no dedicated branch** |
| 52 | **no dedicated branch** |
| 53 | Official Item Library selection/property state |
| 54 | Launcher message |
| 55 | Daily Challenge definition/row state |
| 56 | Daily Challenge progress summary/delta |
| 57 | raid progress percentage — normal style |
| 58 | raid progress percentage — alternate style |
| 59 | LMS lobby text line 1 |
| 60 | LMS lobby text line 2 |
| 61 | LMS lobby text line 3 |
| 62 | fog state |
| 63 | generic widget integer property |
| 64 | Bounty overlay toggle |
| 65 | Bounty overlay deadline |
| 66 | Bounty overlay paired strings |
| 67 | Bounty CURSED state |
| 68 | Launcher message alternate mode |
| 69 | generic fixed HUD-panel text |
| 70 | dormant structured timed state |
| 71 | Item Enchantment selection sprite |

## DELIBERATELY STRUCTURAL TARGETS

Some exact effects are closed without a trustworthy gameplay/business name.

Important examples:

### Target 20

Exact widget/model media mutator.

The client effect is known; originating domain is not.

### Target 34

Exact world-tile entity suppression state.

Player/NPC render paths suppress entities on marked tiles and scene update removes the same key, but the originating gameplay reason remains unknown.

### Targets 40 / 41

Exact generic widget boolean false/true state.

Do not invent one business subsystem.

### Target 42

Exact BountyOverlay scalar field, but its business label remains unresolved.

### Target 43

Two-int current pet-loadout pair.

The pair is consumed by loadout UI and can later be emitted as `pet_loadout`, but the exact component names/order are intentionally not invented.

### Target 63

Exact widget integer-property write.

Business meaning depends on target widget/content context.

### Target 69

Exact fixed HUD-panel text state.

The renderer is known, but the feature/domain identity is not.

### Target 70

Exact grammar:

~~~text
int
int
longDelta
string
~~~

The client stores two ints, an absolute deadline derived from `now + delta`, and a string.

A whole-JAR current-client census found no stock runtime reader for this state.

Therefore the correct authority is:

~~~text
EXACT_CURRENT_CLIENT_STRUCTURAL
business semantic = UNKNOWN
~~~

Do not name it after a guessed feature.

## TARGET-1 GLOBAL CONTROL TOKENS

Target 1 is itself a compatibility/control namespace rather than one semantic field.

Exact current examples include:

- duel type controls;
- LMS/HG overlay on/off;
- restore ticks;
- Item Library / Wiki selection/reset;
- system-update warning controls;
- Clan Wars overlay on/off;
- quick-prayer state;
- construction build mode;
- Adventure/tutorial modes;
- Raid instance controls;
- Daily Challenge rebuild/clear;
- Marketplace/exchange reset/update;
- login-reward index;
- Blood Pool slot reset;
- Blood-tree scroll reset;
- inventory-overlay clear;
- quick-prayer disable.

Argument-bearing routes already normalized include:

~~~text
clear_exchange
add_exchange <int,int>
update_exchange
clearsellmarket
clearbuymarket
setsellitem,<itemId>
clearinvoverlay
cleardialog
togglebh
clearcc <widgetStart>
LOGIN_REWARD_IDX <int>
DISABLE_QUICK_PRAYERS
ITEM_GUIDE_SELECTED_<widgetId>
ITEM_GUIDE_BONUS_WIDGET <ON|other>
WIKI_SELECTED_<widgetId>
~~~

Important: some wire names are historically misleading. For example, `BUILD_ACHIEVEMENT_TAB` / `CLEAR_ACHIEVEMENT_TAB` operate on Daily Challenges in this client generation.

Raw token spelling is compatibility evidence, not necessarily the correct business-domain name.

## ARCHITECTURE CONSEQUENCE

S2C126 should not be exposed to content code as:

~~~text
publish126(target, arbitraryString)
~~~

except at the lowest compatibility layer.

Correct shape:

~~~text
exact S2C126 codec
 -> typed application-control publisher
 -> subsystem presentation adapter
 -> semantic domain state
~~~

Unknown-but-exact structural targets must remain representable without inventing semantics.

## WHAT IS CLOSED

- exact framing;
- finite special-target dispatch boundary through target 71;
- exact target 15–71 branch map;
- explicit holes 50–52;
- major target 0–14 families;
- a substantial target-1 control-token vocabulary;
- argument-bearing target-1 routes.

## WHAT REMAINS

Not broad packet archaeology.

Only targeted work such as:

- naming a structural target when a new exact consumer/source proves it;
- linking a specific target-1 compatibility token to a semantic domain operation;
- recovering original server business rules behind already-known presentation state.

## READY FOR CHAT 2

yes — transport/presentation boundary is finite.

## READY FOR CHAT 3

yes for named semantic targets; structural/unknown targets stay below the domain boundary.
