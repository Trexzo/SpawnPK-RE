# SpawnPK Exact-Current Client / Cache Gap Audit R2

Date: 2026-09-20  
Lane: evidence/research only  
Base: `main` at `11df408115a87c26c713f10baecf047c72bfd073`  
Pinned client SHA-256: `6232bae206846a4ba8d09766a2dee886b69016066a3f50f83b201bf705f93662`  
Pinned cache archive SHA-256: `607425ad3fe69f4cfcaaf82220d9a0d4ebff9954e896819245f37271713d299a`

## Purpose

This continues the first-pass client/cache RSPS gap audit.

R1 identified that S2C126 is not merely widget text and recommended deeper recovery of:

1. remaining typed S2C126 targets,
2. exact C2S actions for raids,
3. marketplace,
4. progression,
5. mail/reward coffer,
6. construction,

before opening new implementation roadmap issues.

This R2 pass follows that rule. It does not invent production gameplay mechanics, prices, rewards, ownership rules, raid mechanics, mailbox contents, or construction server authority.

## Evidence used

- exact pinned current client JAR,
- exact matching SpawnPK cache archive,
- exact client bytecode via `javap -c -p`,
- existing protocol master / deep-recon documents,
- live repository state checked before creating this research lane.

The matching cache contains current SpawnPK configuration/assets including blood-system YAMLs and native UI assets.

## Major R2 finding: S2C126 target space extends through at least 71

R1's "15+" continuation bucket is much larger than it appeared.

The current client has dedicated S2C126 target branches through target 71, with 50-52 absent from this branch. These targets coexist with ordinary widget-text fallback and the global command-token vocabulary.

Do not model S2C126 as:

```
sendString(widgetId, text)
```

A safer model remains:

```
S2C126ApplicationControl
  payload: string_nl
  targetKey: u16_be_low_sub128

  dispatch:
    controller-specific typed targets
    global control tokens
    ordinary widget text fallback
```

## Recovered typed target catalog, 15-71

Names below are conservative. "Structural" means the byte/field effect is exact but the original server-side business meaning is not yet fully proven.

### 15 - Bank tab content state

Payload is three integers.

The client calls the bank-tab updater with:

```
tab = first - 1
value2 = second
value3 = third
```

The target updates the native bank tab arrays/state.

Confidence: HIGH.

### 16 - Selected bank tab

Payload: one integer.

The client applies `value - 1` as the selected bank tab and updates native bank-tab highlighting.

Confidence: HIGH.

### 17 - Friend-list clear/removal synchronization

Payload: one integer.

When the value is zero the client iterates its current friend IDs and invokes the same removal path used for normal friend removal.

Important: the removal path emits C2S opcode 215 with the friend long identifier.

This is therefore not merely cosmetic list clearing.

Confidence: HIGH structural / MEDIUM original server intent.

### 18 - Multi-group widget selection/highlight state

Payload: widget/control integer.

The client selects one entry and swaps active/inactive sprites over several hard-coded groups, including ranges around:

- 240xx groups,
- 51311 / 51313,
- 59847-59852,
- 25756-25758,
- 25337-25338,
- 54000,
- 45912 / 45923,
- 54105-54108,
- 60271-60272.

Exact state machine is proven; one universal gameplay name is not.

Confidence: HIGH structural.

### 19 - Hitpoints orb condition fill

Payload values map to exact current-client orb fill assets:

```
0 -> orbs/hp_fill
1 -> orbs/poison_fill
2 -> orbs/venom_fill
```

Confidence: HIGH.

### 20 - Widget model/media state

Payload: two integers.

Forwards to the current widget/model mutator with fixed trailing parameters.

Keep named structurally until the original server use is proven.

Confidence: HIGH structural.

### 21 - Dynamic widget sprite

Payload contains:

```
spritePath
widgetId
x
y
```

The client constructs a sprite and installs it on the target widget.

Confidence: HIGH.

### 22 - Task-progress hover text

Payload replaces `[bl]` with newline and is stored in the client task-progress hover field.

The only audited renderer consumes this field when:

```
menu/widget hover id = 55761
root interface       = 18559
```

and prefixes the exact current-client label:

```
@yel@Task progress left:
```

before rendering the supplied multiline payload near the mouse cursor.

This proves target 22 as task-progress hover/presentation state. The specific server task family that owns any individual payload remains server authority.

Confidence: HIGH.

### 23 - Broadcast banner text

Payload `"null"` clears the field; any other payload becomes the active broadcast text.

The current client renders this field with the exact prefix:

```
<img=2> <col=FE610C>[Broadcast]:</col> <payload>
```

in both relevant overlay/menu rendering paths.

Therefore target 23 is the exact current-client broadcast banner/message state channel.

Confidence: HIGH.

### 24 - Equipment-hover detail cache

Target 24 populates the current client's equipment-hover detail cache.

The payload begins with an item ID, which is used as the map key; the full payload is retained as the cached value.

The consuming hover renderer first resolves the current item definition. Only equip/wear/wield-capable items (plus exact special item 21739) become equipment-hover candidates.

When the cache has no entry for that item ID, the client queues exactly:

```
::equipstr <itemId>
```

The normal pending-command drain serializes it as C2S103 command text:

```
equipstr <itemId>
```

Until target 24 supplies the matching keyed payload, the hover UI displays `Loading, please wait..`.

The exact S2C126 control token:

```
RESET_HOVER_EQUIPMENT
```

clears the same cache.

The renderer consumes the returned structured fields to build native attack/defence/strength/prayer/ranged-strength/magic-damage equipment detail presentation.

This gives a complete exact-current round trip:

```
equipment hover miss
 -> C2S103 equipstr <itemId>
 -> server equipment-detail lookup
 -> S2C126 target 24 keyed payload
 -> native equipment hover presentation
```

Confidence: HIGH.

### 25 - CombatOverlay state update

Exact consumer class identifies itself as `CombatOverlay`.

Payload contains:

```
name/text
integer
integer
```

and resets related transient state.

Confidence: HIGH.

### 26 - Client text state

Payload copied into a dedicated client string field.

Domain owner unresolved.

Confidence: HIGH structural.

### 27 - Widget enabled/disabled sprite state

Payload:

```
widgetId
flag
```

The target widget's active/inactive visual state is updated.

Confidence: HIGH structural.

### 28 - Client deadline/timer

Payload: seconds/milliseconds scalar interpreted as a future deadline relative to current time.

Confidence: HIGH structural.

### 29 - Two-scalar state

Payload: two integers.

Stored as a pair in client state.

Confidence: HIGH structural.

### 30 - Single-scalar state

Payload: one integer.

Confidence: HIGH structural.

### 31 - Floating-point state

Payload: double.

Confidence: HIGH structural.

### 32 - Player/entity runtime scalar

Writes one scalar into a current `rs/a/k` field.

Confidence: HIGH structural.

### 33 - Indexed player/entity runtime scalar

Payload identifies an index and scalar and updates an `rs/a/k` array entry.

Confidence: HIGH structural.

### 34 - Boolean pair-key state map

Payload: two integers.

The pair is converted into a key and recorded in a Boolean map.

Confidence: HIGH structural.

### 35 - Combat hit/block popup update

This target is now bytecode-joined to exact top-level S2C255.

S2C255 creates the popup object through `rs/l/e/h.a(int,long,int)`:

```
HIT_DROP_POPUP_EVENT(amount, opaque64, protectionStyle)
```

Target 35 parses the same three-field shape:

```
amount,opaque64,protectionStyle
```

It scans both active/pending popup lists for the matching `opaque64` identifier and mutates the existing popup's visible amount and protection style.

The renderer uses exact current assets:

- `popups/hit drop` when amount is non-zero,
- `popups/block drop` when amount is zero,
- `popups/protmelee` when protectionStyle = 1,
- `popups/protmagic` when protectionStyle = 2,
- `popups/protrange` otherwise.

Therefore target 35 is not a loot/drop-table state channel. It is the update side of the current combat hit/block popup presentation contract.

The middle `opaque64` remains intentionally opaque because its server-side identity meaning is not consumed by the audited renderer.

Confidence: HIGH.

### 36 - Daily Money Making Activities selection

Consumer: native `Daily Money Making Activities` controller.

Payload: integer selecting its current filter/tab/difficulty state.

The client contains Easy / Medium / Hard task views, tracking and teleport presentation.

Confidence: HIGH.

### 37 - Integer list append

Payload integer appended to a client list.

Domain owner unresolved.

Confidence: HIGH structural.

### 38 - Item Enchantment Chest state/stage

Consumer: native `Item Enchantment Chest`.

Payload integer changes its current internal state/stage.

Confidence: HIGH.

### 39 - Two-value timed state

Payload: two integers, one of which contributes to a future deadline.

Confidence: HIGH structural.

### 40 / 41 - Widget Boolean state

Payload: widget ID.

```
40 -> widget boolean false
41 -> widget boolean true
```

Confidence: HIGH structural.

### 42 - BountyOverlay scalar state

Consumer: `BountyOverlay`.

Payload integer writes a dedicated overlay field.

Confidence: HIGH presentation / MEDIUM exact field meaning.

### 43 - Two Bounty/PvP-adjacent client scalars

Payload: two integers into paired client fields.

Keep structural pending consumer proof.

### 44 - Completionist/cosmetic selector text pair

Payload can be either:

```
value
```

or:

```
value1,value2
```

It updates `rs/n/c/w` text state and immediately rebuilds that controller's visible label.

The controller is part of the native completionist/cosmetic color-selection surface.

Confidence: HIGH consumer / MEDIUM exact semantic label.

### 45 - Completionist/cosmetic selector auxiliary text

Payload stored in `rs/n/c/w.d`.

Confidence: HIGH consumer / MEDIUM exact semantic label.

### 46 - Two-scalar client state

Payload: two integers.

Confidence: HIGH structural.

### 47 - Debug/log text

Payload is printed to stdout.

Confidence: HIGH.

### 48 - Absolute future deadline

Payload long is added to current time and stored as a deadline.

Confidence: HIGH structural.

### 49 - Multiline text state

Payload replaces `{n}` with newline and stores the result.

Confidence: HIGH structural.

### 50-52

No dedicated 50/51/52 target branch was found in this S2C126 handler.

Do not invent them.

### 53 - Item Library / Item Guide selection

Payload is split into text plus integer and forwarded to `rs/n/c/ab.a(String,int)`.

That controller is the native SpawnPK Item Library / Item Guide.

Confidence: HIGH.

### 54 - Launcher/window message control

Payload is passed to the launcher with the title `SpawnPK RSPS`.

Confidence: HIGH.

### 55 - Daily Challenge row/state update

Payload uses six semicolon-separated fields and calls the native Daily Challenges row updater.

Confidence: HIGH.

### 56 - Daily Challenge summary/progress update

Payload:

```
string
int
int
```

and calls the native Daily Challenges summary/state updater.

Confidence: HIGH.

### 57 / 58 - Double state plus mode flag

Both parse a double into the same field.

```
57 -> mode flag false
58 -> mode flag true
```

Domain owner unresolved.

Confidence: HIGH structural.

### 59 / 60 / 61 - Three related text channels

Each stores one string into adjacent client fields.

Domain owner unresolved.

Confidence: HIGH structural.

### 62 - Fog state

Exact token:

```
FOG_ACTIVE
```

sets the client fog Boolean; any other payload clears it.

Confidence: HIGH.

### 63 - Widget integer runtime property

Payload:

```
widgetId
value
```

writes the value into a runtime widget integer field.

Confidence: HIGH structural.

### 64 - BountyOverlay toggle

No payload parsing is required; receiving target 64 toggles one BountyOverlay Boolean.

Confidence: HIGH.

### 65 - BountyOverlay deadline

Payload long is converted into an absolute deadline relative to current time.

Confidence: HIGH.

### 66 - BountyOverlay paired strings

Payload may contain:

```
value1^value2
```

or no pair.

The two strings are stored in BountyOverlay; `None` is normalized to null for the first field.

Confidence: HIGH.

### 67 - BountyOverlay cursed state

Exact payload check:

```
CURSED
```

sets the BountyOverlay cursed Boolean.

Confidence: HIGH.

### 68 - Launcher/window message control variant

Payload passed to the launcher under `SpawnPK RSPS` with an additional Boolean mode.

Confidence: HIGH.

### 69 - Client text state

Payload copied into a dedicated client string field.

Domain owner unresolved.

Confidence: HIGH structural.

### 70 - Four-field timed/status state

Payload has four components:

```
int
int
longDelta
string
```

The long becomes an absolute deadline relative to current time.

Domain owner unresolved.

Confidence: HIGH structural.

### 71 - Item Enchantment Chest selected/result sprite state

Payload integer selects one of 60 native enchantment widgets beginning at widget 50254.

The chosen index receives the active/result sprite while previous `sprite 30` state is reverted to the inactive sprite.

Confidence: HIGH.

## Exact C2S command transport recovered

The current client command helper does not merely retain command strings.

Its queued-command drain writes:

```
opcode 103
var-byte length = commandString.length - 1
ISO-8859-1 command text = commandString.substring(2)
```

Therefore a UI-side string:

```
::mail
```

becomes exact C2S opcode 103 command text:

```
mail
```

The same applies to all command helpers below.

## Mail / reward coffer

Exact client commands:

```
::mail
::claimcoffer
```

Both enter the opcode-103 command queue.

The richer inbox presentation is separately carried by the S2C250 application bus. Existing exact client evidence exposes native mail/coffer state operations for row population, status, selection, claim state and detail presentation.

Architecture consequence:

```
MailDomainIntent (C2S opcode103 command)
        +
MailPresentationState (S2C250 typed subtype)
```

Do not model mailbox state as chat text or fabricate production messages/rewards.

## Daily Challenges / progression

Native Daily Challenges exact command prefixes:

```
::claimchallenge <challengeId>
::infochallenge <challengeId>
```

Both are constructed by the client and flow through opcode 103.

S2C126 targets 55 and 56 are exact Daily Challenge presentation/state updates.

Architecture consequence:

```
ChallengeIntent
ChallengeProgressState
ChallengePresentationPublisher
```

The client proves the transport and UI surface, not the original server's challenge assignment/reward policy.

## Blood Fountain perk tree

Exact outbound command:

```
::selectperk <perkId>
```

Reset/unselect path:

```
::selectperk 0
```

Both flow through opcode 103.

Existing S2C126 controls also include Blood Pool / Blood Tree reset controls.

Architecture consequence:

```
BloodPerkSelectionIntent
BloodPerkState
BloodPerkPresentation
```

Do not infer perk mechanics merely from selection transport.

## Adventure/progression entry point

The exact client contains:

```
::adventurebook
```

and existing S2C126 control tokens include:

```
BEGIN_ADVENTURE
BEGIN_ADVENTURE_BOOK
BEGIN_ADVENTURE_ORB
END_ADVENTURE
```

This gives an exact native presentation/control entry point, but not enough authority by itself to recreate adventure progression rules.

## Raid / party UI

Native raid setup interface root:

```
19600
```

Current client exposes:

- Chambers of Xeric
- Theatre of Blood
- five party slots
- invite/remove controls
- Refresh
- Re-invite last players
- Start raid
- Leave/disband party
- Normal / Adept / Expert / Master / Grandmaster difficulty

Exact current UI widget construction includes button IDs around:

```
19611..19634   party invite/remove rows
19665          Start raid control
19669          Leave/disband control
19800          Refresh control
19803          Re-invite control
```

The normal generic widget-action path uses client menu action 315 and serializes:

```
C2S opcode 185
u16_be widgetId
```

Therefore the raid setup surface is primarily ordinary exact widget action transport, while raid presentation/state is richer and separately represented by:

- S2C126 control tokens including `RAID_INSTANCE_ON/OFF`,
- S2C250 raid application state.

Existing exact S2C250 research identifies the raid subtype as a native state machine containing member rows, panel toggles, selection/visibility, overlay state, coordinates, timer and metrics.

Architecture consequence:

```
RaidUiIntent (C2S185 widget)
Party/Raid domain service
RaidPresentationState (S2C126 + S2C250)
```

Do not infer raid damage/reward/drop mechanics from the UI.

## Marketplace / Trading Post

The native marketplace UI uses the ordinary generic widget menu action path for its clickable controls.

The exact generic path for action code 315 serializes:

```
C2S opcode 185
u16_be widgetId
```

The current marketplace UI contains:

- search results,
- name/quantity,
- price each,
- seller,
- ascending/descending toggle,
- pagination,
- modify,
- refresh,
- `Select this market listing`.

The current S2C126 global control vocabulary includes:

```
clear_exchange
add_exchange
update_exchange
clearsellmarket
clearbuymarket
setsellitem,<itemId>
```

The richer S2C250 marketplace/listing state already has exact fields:

```
itemId
totalQuantity
soldQuantity
priceEach
currency
```

with exact currency mapping:

```
0 = Gold
1 = Bags
```

and remove-by-itemId support.

Architecture consequence:

```
TradingPostUiIntent (C2S185 and text/search input paths)
TradingPostQuery/Transaction domain
TradingPostPresentationState (S2C126 + S2C250)
```

Do not invent production listing contents, prices, seller ownership or settlement rules.

## Construction / POH

The current client contains a native construction room-selection surface with 23 room definitions, including:

- Parlour
- Garden
- Kitchen
- Dining room
- Workshop
- Bedroom
- Hall variants
- Games Room
- Combat room
- Menagerie
- Study
- Costume room
- Chapel
- Boss portal room
- Formal garden
- Throne room
- Superior garden
- four dungeon room types
- Treasure room

The current S2C126 command vocabulary contains exact:

```
CONSTRUCTION_BUILD_ON
CONSTRUCTION_BUILD_OFF
```

The room-selection surface is built from ordinary current-client widgets. The generic widget action contract remains opcode 185 + widgetId.

What is not proven yet:

- exact original room-placement validation,
- room graph constraints,
- object hotspots,
- material costs,
- persistence semantics,
- destruction/relocation rules.

Architecture consequence:

```
ConstructionUiIntent
HouseInstance / HouseLayout domain
ConstructionPresentationState
```

but implementation must remain fixture/dev-authority until the missing server rules are independently evidenced.

## R2.2 exact C2S closure: raid/party setup

The native raid setup actions are now mapped to exact current-client widget IDs.

Raid type selectors:

```
19637  Chambers of Xeric
19638  Theatre of Blood
```

Difficulty selectors:

```
19643  Normal
19644  Adept
19645  Expert
19646  Master
19647  Grandmaster
```

Party membership controls:

```
19611  Remove member slot 2
19617  Remove member slot 3
19623  Remove member slot 4
19629  Remove member slot 5

19614  Invite member slot 2
19620  Invite member slot 3
19626  Invite member slot 4
19632  Invite member slot 5
```

Party/raid controls:

```
19665  Start raid
19669  Leave/disband party
19800  Refresh
19803  Re-invite last players
```

The raid type/difficulty rows are type-1 widgets with explicit `Q` actions such as `Selected raid` and `Normal difficulty`.
The party/start/leave/refresh buttons are also current-client action widgets.

For type-1 action widgets, the client menu builder emits menu action 315. Menu action 315 serializes exactly:

```
C2S opcode 185
u16_be widgetId
```

Therefore all named raid/party setup intents above have exact C2S185 contracts.

This does not authorize inference of party ownership rules, instance lifecycle, raid encounter mechanics, completion validation or rewards.

## R2.2 exact C2S closure: Daily Money Making Activities

The native Daily Money Making controller exposes exact current-client controls:

```
55002  Track this activity
55012  Teleport to this activity
55015  Hard money making tasks
55018  Medium money making tasks
55021  Easy money making tasks
```

`55002` and `55012` are ordinary type-1 action widgets and use menu action 315.

The Easy/Medium/Hard selector widgets are type-5 action widgets. The client menu builder maps type-5 to menu action 646. The action-646 handler then serializes the same exact widget packet:

```
C2S opcode 185
u16_be widgetId
```

Therefore target 36 can be paired with an exact outbound presentation-intent surface:

```
DailyMoneyMakingUiIntent
  TRACK_CURRENT        -> C2S185(55002)
  TELEPORT_CURRENT     -> C2S185(55012)
  SELECT_HARD          -> C2S185(55015)
  SELECT_MEDIUM        -> C2S185(55018)
  SELECT_EASY          -> C2S185(55021)

DailyMoneyMakingState
  <- S2C126 target 36
```

The transport is exact. Task assignment, progress credit, completion requirements, teleport eligibility and rewards remain server-authority gaps.

## R2.1 exact C2S closure: Trading Post text search

The three marketplace text-entry modes are now bytecode-closed.

Current-client input modes:

```
28  Enter name of item to search
29  Enter name of player to search
30  Enter name of item history to search
```

On submit the client constructs exactly:

```
::tpsitem <text>
::tpsuser <text>
::tpshist <text>
```

The generic pending-command drain is also exact:

```
C2S opcode 103
u8 payloadLength = commandString.length - 1
ISO-8859-1 text = commandString.substring(2)
```

Therefore the actual wire command texts are:

```
tpsitem <text>
tpsuser <text>
tpshist <text>
```

This closes the Trading Post text-search C2S path. Ordinary clickable market controls remain exact C2S185 widget actions.

## R2.1 exact C2S closure: mailbox row/detail actions

The native mailbox creates type-1 action widgets. The current client's type-1 menu builder maps their `Q` action string to menu action 315; menu action 315 serializes:

```
C2S opcode 185
u16_be widgetId
```

Exact mailbox controls recovered:

```
row i View inbox message = 32026 + (4 * i), i = 0..34
Delete this message      = 32184
Deposit items to inventory = 32181
Deposit items to bank      = 32178
Refresh icon/control        = 32185
```

The refresh control intentionally has an empty menu-label string while retaining the same type-1 clickable widget construction.

This means mailbox intent transport can be modeled without inventing a mailbox-specific C2S packet:

```
MailboxUiIntent
  VIEW_ROW(index/widget)
  DELETE_SELECTED
  DEPOSIT_ATTACHMENTS_INVENTORY
  DEPOSIT_ATTACHMENTS_BANK
  REFRESH
      -> C2S185(widgetId)
```

Mailbox/coffer top-level entry commands remain:

```
::mail
::claimcoffer
    -> C2S103
```

The server still owns message contents, attachment ownership, expiry, claim authorization and rewards.

## R2.1 exact C2S closure: construction room selector

The construction room selector creates one actionable `Build <room>` type-5 widget per room.

The type-5 widget builder installs `Q = "Build " + roomName`; the current client maps this widget action to menu action 315, therefore every room selection serializes as:

```
C2S opcode 185
u16_be widgetId
```

Exact actionable room widget IDs:

```
39879  Parlour
39883  Garden
39887  Kitchen
39891  Dining room
39895  Workshop
39899  Bedroom
39903  Hall - Skill Trophies
39907  Games Room
39911  Combat room
39915  Hall - Quest trophies
39919  Menagerie
39923  Study
39927  Costume room
39931  Chapel
39935  Boss portal room
39939  Formal garden
39943  Throne room
39947  Superior garden
39951  Dungeon - corridor
39955  Dungeon - junction
39959  Dungeon - stairs
39963  Dungeon - pit
40300  Treasure room
```

The final jump is deliberate current-client behavior: when the dynamic ID counter reaches `39967`, the builder changes the next room base to `40300`.

This closes the room-selection transport contract. It does **not** prove original-server placement validation, adjacency/graph rules, hotspot semantics, material consumption, persistence or destruction rules.

## R2.4 exact C2S closure: Item Enchantment item selection

The native Item Enchantment Chest uses more than one client intent transport.

Its visible category/action controls remain exact C2S185 widget actions:

```
49970..49985  Select enchantment category rows
49990         Enchant
50314         Search by name
```

The actual 60-slot selectable item grid is widget:

```
50253
```

and its first inventory action is exactly:

```
Select item
```

The generic inventory-action builder maps item action index 0 / `W[0]` to menu action 632.

The exact current writer for menu action 632 emits:

```
C2S opcode 145
widgetId  u16_be_low_sub128
slot      u16_be_low_sub128
itemId    u16_be_low_sub128
```

LocalLab's existing exact-current decoder independently carries the same authority:

```
case 145:
  widget = BE-A
  slot   = BE-A
  item   = BE-A
```

Therefore the current Item Enchantment intent split is:

```
SELECT_ENCHANTMENT_CATEGORY -> C2S185(widget 49970..49985)
SELECT_ITEM                  -> C2S145(widget=50253, slot, itemId)
ATTEMPT_ENCHANT              -> C2S185(widget=49990)
SEARCH_BY_NAME               -> C2S185(widget=50314)
```

S2C126 target 38 remains the stage/state projection and target 71 the selected/result sprite projection.

This closes the native item-selection transport. It still does not prove authoritative ingredient requirements, consumption, success/failure probability, outputs or currency/material costs.

## Transport separation confirmed by R2

Do not collapse these systems into one "custom packet" abstraction.

The current client demonstrably uses at least:

### S2C126

Application control / typed target / text fallback.

### S2C250

Typed ScriptPacket/application-state records for richer systems such as mail, marketplace and raid UI.

### S2C253

Server-message transport with embedded request directives in other systems.

### C2S103

Text command intents.

### C2S185

Generic widget-button intents.

A correct LocalLab architecture should preserve these boundaries.

## Recommended implementation sequence after evidence closure

Do not open all domain modules at once.

Recommended order:

1. typed S2C126 application-control/state publisher,
2. typed S2C250 application-bus registry integration where already exact,
3. Daily Challenges / progression presentation substrate,
4. mailbox/reward-coffer presentation substrate,
5. marketplace query/listing presentation substrate,
6. party + raid presentation/intent substrate,
7. construction/house instance substrate,
8. domain mechanics only where independent server authority exists.

## Suggested first implementation issue, after audit sign-off

Title:

`[Protocol / Presentation] Model exact-current S2C126 as a typed application-control/state bus`

Acceptance should require:

- preserve exact S2C126 wire grammar,
- ordinary widget text remains supported,
- explicit typed target handlers for proven targets,
- explicit control-token registry,
- provenance on every typed control,
- no original-server gameplay invention,
- exact packet-level golden vectors,
- no changes to unrelated gameplay.

## Remaining R2 evidence gaps before domain issues

Continue research before opening raid/market/construction gameplay issues:

1. resolve structural targets 26, 29-34, 37, 39, 43, 46, 48-49, 57-61, 69-70 to domain owners where possible,
2. verify whether construction exposes any additional non-widget placement command after room selection,
3. recover any raid invitation text-entry path if the server requests a player name after the invite widget,
4. identify the Item Enchantment Search-by-name text submission path if it is client-local rather than server-requested,
5. add packet-level fixtures before any production implementation.

Closed in R2.4:
- Item Enchantment widget 50253 `Select item` uses exact C2S145 with BE-A widget/slot/item fields; category, Enchant and Search controls remain C2S185.

Closed in R2.3:
- S2C126 target 35 is the exact update side of S2C255 `HIT_DROP_POPUP_EVENT(amount, opaque64, protectionStyle)`; it is combat hit/block popup presentation, not loot/drop-table state.

Closed in R2.1/R2.2:
- Trading Post text submission: `tpsitem` / `tpsuser` / `tpshist` over C2S103.
- Mailbox row/delete/deposit/refresh widget actions over C2S185.
- Construction room-selection widget IDs and C2S185 transport.
- Named raid type/difficulty/member/start/leave/refresh/re-invite controls over C2S185.
- Daily Money Making track/teleport/difficulty controls over C2S185 paired with S2C126 target 36.

Closed in R2.5:
- target 22 = task-progress hover text.
- target 23 = broadcast banner text.
- target 24 = equipment-hover detail cache, paired with exact C2S103 `equipstr <itemId>` and `RESET_HOVER_EQUIPMENT`.

## R2 conclusion

The first-pass gap assessment remains directionally correct, but the exact current client exposes a substantially larger recoverable presentation/control surface than R1 captured.

The strongest architectural conclusion is now:

```
wire-exact transport
  -> typed client/server presentation intent
  -> domain request/state
  -> server-authoritative mechanics
```

not:

```
custom packet/string
  -> gameplay
```

The next safe code change is the typed S2C126 presentation bus, not a guessed raid, marketplace, mail or construction gameplay implementation.
