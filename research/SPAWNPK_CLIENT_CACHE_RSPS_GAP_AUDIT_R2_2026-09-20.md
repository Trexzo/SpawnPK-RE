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

### 18 - Gambling game-type selection

Canonical exact-current audit resolves the hard-coded sprite groups to the native gambling game-type/host-selection surface, including Flower Poker, 55x2, blackjack-like/BJ, Dice Duel and host-selection presentation.

The payload selects one current mode/control and the client swaps the relevant active/inactive sprites across the native gambling controller groups.

Confidence: HIGH.

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

### 26 - Daily Money Making tracking text

The stock Daily Money Making Activities renderer consumes this exact string and renders:

```
Tracking: @yel@<server value>
```

beside its native tracking widgets around 55008/55011.

Together with target 36, this forms the exact-current presentation pair:

```
target 26 -> tracking text
target 36 -> Easy / Medium / Hard selected category/state
```

Confidence: HIGH.

### 27 - Gambling/control widget selected-state

Payload:

```
widgetId
flag
```

The current client applies the active/inactive sprite state used by the native gambling/control selection UI.

Confidence: HIGH.

### 28 - Clan Wars begin countdown

Target 28 is part of the same renderer gated by exact target-1 controls:

```
ENABLE_CLAN_WARS_OVERLAY
DISABLE_CLAN_WARS_OVERLAY
```

Payload is parsed as seconds and stored exactly as:

```
clanWarsBeginDeadline = now + (payloadSeconds * 1000)
```

While active, the overlay renders:

```
Begin in..
<minutes>:<seconds>
```

Confidence: HIGH.

### 29 - Clan Wars team/opponent counts

Payload:

```
yourClanCount,opponentCount
```

The Clan Wars overlay renders the two stored integers directly beneath exact labels:

```
Your clan
Opponents
```

Confidence: HIGH.

### 30 - Clan Wars score-label mode

Payload is one integer consumed by the same Clan Wars overlay.

Exact current rendering:

```
0       -> Fighters:
nonzero -> Kills:
```

This is a presentation-mode flag, not a recovered server scoring rule.

Confidence: HIGH.

### 31 - Special-attack orb value

The stock gameframe consumes this double for:

```
orbs/spec_fill
orbs/spec_icon
```

Exact current-client display math is effectively:

```
fillPercent = int(value / 10 * 100)
displayText = int(value * 10)
```

so current values 0.0 / 5.0 / 10.0 render as 0% / 50% / 100%.

This is exact client presentation authority for special energy. It does not prove special-attack costs, restoration rate or gameplay mechanics.

Confidence: HIGH.

### 32 - Local-player prayer/protection head icon

Target 32 writes directly to the local player's `rs/a/k.bd` field.

That field is consumed by the player overhead renderer as an index into the 21-entry:

```
headicons_prayer
```

sprite array. Current-client special entries also replace indices 7/8/9 with combined protection assets:

```
prayer/protmagemelee
prayer/protmeleerange
prayer/protall
```

Therefore target 32 is exact local-player prayer/protection overhead-icon state.

Confidence: HIGH.

### 33 - Indexed remote-player prayer/protection head icon

Payload contains:

```
playerIndex
headIconIndex
```

The client bounds-checks the player array, resolves that indexed `rs/a/k`, and writes the same `bd` field used by target 32.

The overhead renderer consumes it through the same `headicons_prayer` sprite array.

Therefore target 33 is the indexed remote-player form of the prayer/protection overhead-icon update.

Confidence: HIGH.

### 34 - Scene-tile entity suppression state

Payload contains two integers which are normalized into the exact string key:

```
"<worldX>,<worldY>"
```

and inserted into a Boolean map.

The same map is consulted by both current-client NPC and player scene-render passes. In region `10806`, if the entity's resolved world-tile key exists in the map, that entity is skipped from the relevant render/add path.

A scene-update path removes the exact same coordinate key when the corresponding tile state is cleared.

This proves the target's client effect as tile-scoped entity suppression/visibility state. The gameplay feature that causes the server to mark those tiles remains intentionally unnamed.

Confidence: HIGH structural effect / UNKNOWN_SERVER_AUTHORITY domain.

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

### 37 - Blood Pool shop slot append

The payload appends one native Blood Pool shop slot/value entry to the same client list cleared by the exact S2C126 control token:

```
RESET_BLOOD_POOL_SHOP_SLOTS
```

This is exact-current Blood Pool shop presentation state.

Confidence: HIGH.

### 38 - Item Enchantment Chest state/stage

Consumer: native `Item Enchantment Chest`.

Payload integer changes its current internal state/stage.

Confidence: HIGH.

### 39 - World Tournament phase/deadline

Payload contains the tournament phase plus seconds-to-deadline.

The stock client renders phase-dependent countdown semantics:

```
phase 1 -> Round starts in
phase 2 -> Round ends in
other   -> Tournament starts in
```

with the second value contributing to the absolute countdown deadline.

Confidence: HIGH.

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

### 43 - Current pet-loadout pair

The two integers are stored in `Client.s` / `Client.t` and later consumed by the stock clone/loadout UI.

That UI's apply path emits exactly:

```
::pet_loadout <arg0> <arg1>
```

over generic C2S103.

The business meaning/order of the two integers remains intentionally unnamed; only the exact current loadout transport/state relationship is claimed.

Confidence: HIGH.

### 44 - Text color and optional shadow color

This is the native Text Color Selection Menu at root `63000`, not the separate completionist-cape color screen.

Payload:

```
textColorHex[,shadowColorHex]
```

updates the selector state and rebuilds preview widget `63026`:

```
Sample on chat background
```

with the selected color/shadow markup.

Confidence: HIGH.

### 45 - Text-color submit command prefix

Target 45 supplies the command token used when the generic Text Color Selection Menu is confirmed.

Default/current-client prefix:

```
setyellcolors
```

Confirm widget `63027` constructs:

```
::<commandPrefix> <textColor> [shadowColor]
```

which enters generic C2S103 command transport.

The completionist cape color interface is separate: root `63036` uses hardcoded `::compcolors` and must not be conflated with target 45.

Confidence: HIGH.

### 46 - Raid overlay points/total

Payload contains the two integer values consumed by the exact-current raid overlay as its points/total state.

This data is separate from the target-1 `RAID_INSTANCE_ON/OFF` controls and from S2C250 raid/party presentation.

Confidence: HIGH.

### 47 - Debug/log text

Payload is printed to stdout.

Confidence: HIGH.

### 48 - PvP Hotspot timer duration

Payload is a duration in milliseconds and the client stores:

```
endTime = now + durationMs
```

The native Hotspot interface uses widget `62150` for the Hotspot label and periodically formats the remaining time into widget `62151` with the current timer icon.

Confidence: HIGH.

### 49 - PvP Hotspot detail/body text

Payload replaces `{n}` with newline and becomes the server-authored body inside the native PvP Hotspot presentation.

The stock consumer surrounds it with the current Hotspot heading and casket/Blood-orb explanatory text.

Confidence: HIGH.

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

### 57 / 58 - Raid progress percentage/style

Both targets parse the raid progress percentage into the same current-client field.

```
57 -> normal raid-progress style
58 -> alternate raid-progress style
```

These are exact raid-overlay data channels, separate from target 46 points/total and target-1 raid enable/disable controls.

Confidence: HIGH.

### 59 / 60 / 61 - LMS lobby text lines

The three adjacent strings are the exact current-client Last Man Standing lobby text channels:

```
59 -> LMS lobby line 1
60 -> LMS lobby line 2
61 -> LMS lobby line 3
```

They pair with target-1 `LMS_LOBBY_OVERLAY_ON/OFF` control and target 62 fog state.

Confidence: HIGH.

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

### 69 - Standalone HUD panel text

Target 69 stores the payload in `Client.k`.

A whole-JAR reader census finds the only stock consumer in `rs.l.b.b`: when the field is non-null, the client draws a fixed standalone HUD panel and renders the payload verbatim inside it.

No feature-specific heading, command token, asset name, or second consumer identifies the originating domain. The honest classification is therefore exact presentation effect with unknown business owner:

```
GENERIC_HUD_PANEL_TEXT
feature/domain = UNKNOWN
```

Confidence: HIGH structural effect / UNKNOWN_SERVER_AUTHORITY domain.

### 70 - Dormant structured timed state

Exact grammar:

```
int0,int1,durationOffset,string
```

with exact storage:

```
aO = int0
aP = int1
aQ = now + durationOffset
aR = string
```

A whole-JAR field-reference census found no stock runtime `getfield` reader for any of these four fields in the pinned client; they are initialized/reset and written by target 70 only.

Therefore the exact grammar/storage/expiry model is known, while the feature name is deliberately **UNKNOWN**. This target must remain representable without inventing a gameplay semantic.

Confidence: HIGH structural / DORMANT_CURRENT_CLIENT.

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

The R1-requested transport/effect audit is now closed at the static exact-current layer.

Remaining unknowns are deliberately server-owned business rules or require runtime/server evidence, for example:

1. raid invite-name acquisition after the exact C2S185 invite click,
2. authoritative raid admission/progression/reward rules,
3. marketplace escrow/settlement/ownership rules,
4. mailbox contents/claim authorization/expiry rules,
5. construction placement/adjacency/material/persistence rules,
6. Enchantment Search-by-name follow-up query acquisition,
7. recipe/success/cost mechanics for Enchantment/Blood conversion systems.

These are **not** grounds to invent new C2S/S2C packet families.

Packet-level production fixtures and typed S2C126 implementation belong on Issue #148's isolated production lane.

## R2.9 negative closures for indirect input / post-selection paths

### Raid invite-name follow-up remains intentionally unjoined

The exact raid setup controller `rs.n.c.aL` owns the four native invite widgets:

```
19614
19620
19626
19632
```

Each is an ordinary current-client widget request and therefore emits:

```
C2S185(widgetId)
```

A direct audit of the raid controller finds:

- no write to `Client.fN` (the client-local text-input mode),
- no embedded `::raidinvite` / player-name command construction,
- no alternate raid-specific C2S name packet.

The current client does have a generic server-driven native name prompt:

```
S2C187 -> Enter name mode
C2S60  -> fixed8 BE name-hash long
```

but static exact-current evidence does **not** join that generic prompt to the raid invite widgets.

Therefore the safe contract is:

```
raid Invite member click
 -> exact C2S185(widget)
 -> subsequent invitee-name acquisition = SERVER-MEDIATED / UNRESOLVED
```

Do not hardwire raid invite to S2C187/C2S60 without runtime/server evidence.

### Item Enchantment Search-by-name is request-only

Widget `50314` is the exact current `Search by name` button and its click transport is C2S185.

Whole-JAR negative proof from the canonical R6 audit shows:

- `rs.n.c.G` owns widget 50314 but never writes `Client.fN`,
- all current-client `Client.fN` writers lack widget 50314,
- therefore this button does not locally enter one of the known text-command input modes.

The follow-up query entry remains server-mediated/indirect or otherwise unresolved.

Do not map it to an arbitrary name/free-text input protocol by analogy.

### Construction has no hidden client-side second packet family

The construction room selector `rs.n.c.z` creates each `Build <room>` row as widget mode `M=5`.

Exact outbound-menu authority closes:

```
M=5
 -> client menu action 646
 -> C2S185(widgetId)
```

The construction controller contains no local `Client.fN` input transition and no construction-specific `::command` emission.

Thus the exact client-side transport boundary for room choice is the already-cataloged C2S185 room widget.

Anything after that choice—placement legality, adjacency, orientation, build hotspot selection, price/material deduction, persistence, deletion/refund—is server authority unless separately observed.

No additional construction packet family should be invented.

### Research-lane closure status

At this point:

- every active numeric S2C126 target has an exact client-effect classification;
- all five high-value R1 domains (raid, marketplace, progression, mail, construction) have exact current-client outbound intent transports or explicit negative boundaries;
- known command compatibility paths are mapped onto C2S103;
- ordinary UI actions converge on C2S185;
- Item Enchantment item selection is separately exact C2S145;
- generic native amount/name prompts remain reusable transports but are not assigned to a feature without a direct join;
- unresolved items are server business authority, not unknown packet framing.

Production implementation remains owned by Issue #148 / its dedicated branch. This research lane should not duplicate that code.

## R2.10 live roadmap coordination snapshot

Live repository coordination was rechecked after the static audit closed.

The following audit-derived foundations/services are now owned by separate issues/lanes and must not be duplicated from this research branch:

```
#148  exact-current S2C126 application control/state bus
#150  semantic timed-effect foundation
#154  objective/progression foundation
#157  semantic usage-quota/window state
#158  Party + Matchmaking + WorldInstance lifecycle
#159  atomic transaction/escrow substrate
#162  mailbox/offline reward delivery
#164  durable global-event lifecycle above WorldEventQueue
#165  Marketplace listing lifecycle above #159
#166  read-only semantic item catalog query API
#167  Construction/HouseInstance room-layout state above #158
```

Related implementation/draft PRs already present at this snapshot include:

```
#149  S2C126 application-control implementation
#151  semantic timed effects
#153  v308 login compatibility guard
#155  objective/progression foundation
#161  semantic usage-quota window foundation
#163  mailbox/offline reward delivery foundation
```

Issue #160 was opened concurrently with #158 for Party + WorldInstance, detected immediately, and closed as a duplicate without creating an implementation branch.

This R2 branch remains **research/evidence only**.

### v308 compatibility note

A separate exact-client compatibility audit (#152 / PR #153) established that the newest supplied v308 JAR differs from the prior pinned v307 JAR only in `rs/f/a.class`, where the embedded client-build/config constant changes from 307 to 308.

No application/UI class used by this R2 audit changed.

Therefore the S2C126/application-action findings in this report remain applicable to the supplied v308 exact-current client. This does **not** replace the repository's pinned-client 179/179 acceptance hash and does not claim that the external suite has been run against v308.

## R2.11 later R5 authority correction: Construction prices are presentation-only and internally inconsistent

A deeper exact-current R5 pass found that the native Construction UI does **not** expose one trustworthy room-cost table.

It contains two separate parallel price representations:

### Client-side comparison thresholds

For the 23 room choices the interface script compares inventory coins against integer thresholds:

```
1000, 1000, 5000, 5000, 10000, 10000, 15000, 25000, 25000, 25000,
30000, 50000, 50000, 50000, 100000, 75000, 150000, 150000,
7500, 7500, 7500, 10000, 250000
```

These are exact-current client constants used by interface comparison logic.

### Separately rendered GP-price strings

The same 23 rooms render independent visible price strings:

```
30k, 30k, 150k, 150k, 300k, 300k, 450k, 750k, 750k, 750k,
900k, 1500k, 1500k, 1500k, 300M, 2250k, 4500k, 500M,
300k, 300k, 300k, 350k, 1000k
```

These are displayed as `<img=9> {price} gp`.

The two sets differ substantially.

Therefore the correct provenance is:

- room names = `EXACT_CURRENT_CLIENT`
- displayed level requirements = `EXACT_CURRENT_CLIENT`
- integer comparison thresholds = `EXACT_CURRENT_CLIENT`, interface/presentation semantics only
- rendered GP strings = `EXACT_CURRENT_CLIENT`, presentation only
- actual server deduction/build cost = `UNKNOWN_SERVER_AUTHORITY`

This supersedes any wording that might imply the client has one authoritative room-cost array.

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
