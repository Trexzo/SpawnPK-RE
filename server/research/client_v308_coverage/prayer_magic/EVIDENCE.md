# Evidence Package — Prayer + Magic Client Presentation

SYSTEM
Prayer/Curse and Magic/Spellbook client-facing contracts

STATUS
STRONG-PARTIAL

## AUTHORITY

- EXACT_CURRENT_CLIENT
- EXACT_CURRENT_CACHE
- LOCAL_RUNTIME_OBSERVATION (only where explicitly marked)
- HISTORICAL_CORROBORATION (only where explicitly marked)

Exact-current client:

```
SHA-256 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
revision 308
```

## CLIENT/CACHE CONTRACT — BOOK ROOTS

Recovered current interface roots:

### Prayer

```
Normal Prayer = 5608
Curses        = 22500
```

Current normalized client/cache census:

```
29 normal prayers
22 curses
51 total activation widgets
```

### Magic

```
Modern  = 1151
Ancient = 12855
Lunar   = 29999
```

Current normalized client/cache census:

```
65 modern spell widgets
21 ancient spell widgets
40 lunar spell widgets
126 total spell widgets
```

These counts/root IDs describe the client/cache surface. They do not imply that
the original server implemented every visible spell/prayer mechanic identically.

## CLIENT CONTRACT — PRAYER/CONFIG TOGGLE REQUEST

Current prayer widgets use generic menu action `169`.

Exact `rs.Client` action-169 branch:

```
C2S185
widgetId via rs.x.e.d(int)
```

`d(int)` is ordinary BE16, so the request body is:

```
widgetId:u16_be
```

After writing C2S185, the client inspects the clicked widget's expression
program. When the first expression is the varp-toggle form (opcode 5), it
optimistically changes the local varp:

```
dP[varp] = 1 - dP[varp]
B(varp)
```

This is an exact client-side prediction/presentation mechanism.

The current cache/research corpus maps each prayer widget to its presentation
varp. Examples:

```
Thick Skin              widget 5609   varp 83
Protect from Magic      widget 5621   varp 95
Protect from Missiles   widget 5622   varp 96
Protect from Melee      widget 5623   varp 97
Augury                   widget 18047  varp 610
Rigour                   widget 18045  varp 609

Deflect Magic            widget 22517  varp 88
Deflect Missiles         widget 22519  varp 89
Deflect Melee            widget 22521  varp 90
Wrath                    widget 22537  varp 96
Soul Split               widget 22539  varp 97
Turmoil                  widget 22541  varp 105
```

The client-side 0/1 prediction does **not** prove server activation,
cancellation, drain, stat modifier, reflection, healing, or damage rules.

## CLIENT CONTRACT — PRAYER OVERHEAD ICON

The exact packet-81 appearance parser begins with five u8 fields:

```
aY
bd
bf
bg
bh
```

Exact client startup loads:

```
mW[21] <- sprite group "headicons_prayer"
eK[...] <- sprite group "headicons_pk"
```

The player overhead renderer uses `bd` as the prayer-headicon index. In the
ordinary branch:

```
if (bd < 21)
    draw mW[bd]
```

The client contains special handling for some `bd` values before the ordinary
branch; therefore do not assume all 0..20 values are freely interchangeable
prayer semantics.

The adjacent `bf` field is a separate PK/status-icon channel and must not be
confused with prayer `bd`.

### Observed production mapping

The existing R82 production-observation corpus records these mappings:

```
bd  0  Protect from Melee
bd  1  Protect from Missiles
bd  2  Protect from Magic
bd  3  Retribution
bd  4  Smite
bd  5  Redemption

bd 12  Wrath
bd 13  Deflect Melee
bd 14  Deflect Magic
bd 15  Deflect Missiles
bd 20  Soul Split
```

Authority for the semantic names above is
`LOCAL_RUNTIME_OBSERVATION / CURRENT_PRODUCTION_PROVEN`, not merely static
sprite-array indexing.

Deflect Summoning's current production index was not closed by that corpus and
remains unknown.

## CLIENT CONTRACT — TARGETED SPELL SELECTION

Exact menu action `626` is client-local selection.

It stores, among other local state:

```
selected spell widget = nl
target mask           = nm = widget.ai
selection state       = active
```

and **returns without sending a packet**.

Therefore:

> selecting a targeted spell is not itself a server gameplay request in exact
> current v308.

The current interface/cache target-mask semantics are:

```
1   GROUND_ITEM
2   NPC
4   OBJECT
8   PLAYER
16  INVENTORY_ITEM
```

Masks may combine target categories.

## CLIENT CONTRACT — DIRECT/NON-TARGETED SPELLS

Generic widget menu action `315` sends:

```
C2S185
widgetId:u16_be
```

unless intercepted by a client-local special case.

This is the request path used by direct widget spells/teleports whose interface
mode is direct rather than select-then-target.

The client can contain local lock/presentation checks for individual widgets.
Those checks do not establish the complete original server permission policy.

## CLIENT CONTRACT — SPELL-ON-TARGET TRANSPORT

Once a targeted spell has been selected, the selected spell widget is carried
in `Client.nl` and the later target action produces the actual C2S request.

Exact current writer families:

### Spell on player

```
menu action 365 -> C2S249

targetPlayerIndex -> o(int) = u16_be_low_add128
spellWidget       -> n(int) = u16_le
```

Body:

```
targetPlayer:u16_be_low_add128
spellWidget:u16_le
```

### Spell on NPC

```
menu action 413 -> C2S131

targetNpcIndex -> p(int) = u16_le_low_add128
spellWidget    -> o(int) = u16_be_low_add128
```

### Spell on object

```
menu action 956 -> C2S35
```

Exact write order:

```
worldX      -> n(int)  // LE16
spellWidget -> o(int)  // BE16 low+128
worldY      -> o(int)  // BE16 low+128
objectId    -> n(int)  // LE16
```

### Spell on ground item

```
menu action 94 -> C2S181
```

Exact write order:

```
worldY      -> n(int)  // LE16
itemId      -> d(int)  // BE16
worldX      -> n(int)  // LE16
spellWidget -> o(int)  // BE16 low+128
```

### Spell on inventory item

```
menu action 543 -> C2S237
```

Exact write order:

```
slot        -> d(int)  // BE16
itemId      -> o(int)  // BE16 low+128
widgetId    -> d(int)  // BE16
spellWidget -> o(int)  // BE16 low+128
```

Raw menu-action/opcode identity belongs inside transport/runtime infrastructure;
Chat 3 should consume semantic target requests.

## CLIENT/CACHE CONTRACT — SPELL REQUIREMENT PRESENTATION

The current interface condition programs visibly encode:

- required Magic level;
- rune/item quantities;
- alternative elemental rune combinations;
- equipped staff/provider alternatives;
- spell target masks.

Example modern Wind Strike visibly requires:

```
Magic level 1
Air rune x1, with recognized alternatives/providers
Mind rune x1
target mask 10 = NPC | PLAYER
```

The current full normalized corpus records 126 spell widgets across the three
books.

These condition programs are exact client-visible requirement/presentation
authority.

They are **not sufficient proof** that production server validation,
consumption, or effect logic was identical.

## CLIENT PRESENTATION — CUSTOM MAGIC

Existing exact-client discovery also proves custom interface surfaces for:

- Miasmic Rush/Burst/Blitz/Barrage;
- Surge-family widgets;
- server-controlled custom-magic presentation state through the application
  packet-250 family.

The client corpus contains visible-level/filter-threshold discrepancies for some
custom spells. Those discrepancies must be preserved as evidence rather than
silently normalized into an original-server rule.

## SPELL ANIMATION/GFX/PROJECTILE TUPLES

The R82 presentation table contains per-spell fields such as:

```
castAnimation
castGfx
projectile
impactGfx
```

but authority is **row-specific**:

- some tuples are `CURRENT_PRODUCTION_PROVEN`;
- some are production-validated historical-family candidates;
- some remain historical RSPS-lineage candidates.

Do not bulk-promote the entire tuple table to
`EXACT_CURRENT_CLIENT`. Spell effects are generally server-driven through the
actor/projectile presentation protocols.

## SERVER SEMANTICS PROVEN

The current client/cache proves:

- prayer/curse and spellbook interface roots;
- prayer click widget + varp presentation mappings;
- client-side prayer varp prediction after C2S185;
- prayer overhead sprite channel `bd`;
- targeted-spell selection is local-only;
- selected spell widget + target-mask client state;
- semantic target-mask categories;
- exact later target C2S writer layouts;
- client-visible spell level/rune/provider conditions.

## SERVER SEMANTICS UNKNOWN

- UNKNOWN_SERVER_AUTHORITY: prayer drain rates
- UNKNOWN_SERVER_AUTHORITY: prayer conflict/cancellation matrix unless separately
  production-observed
- UNKNOWN_SERVER_AUTHORITY: prayer stat multipliers
- UNKNOWN_SERVER_AUTHORITY: protection/deflection percentages
- UNKNOWN_SERVER_AUTHORITY: Soul Split healing/drain behavior
- UNKNOWN_SERVER_AUTHORITY: Smite/Wrath/Redemption mechanics
- UNKNOWN_SERVER_AUTHORITY: Turmoil/Sap/Leech modifier formulas
- UNKNOWN_SERVER_AUTHORITY: authoritative Magic accuracy
- UNKNOWN_SERVER_AUTHORITY: spell max-hit/damage formulas
- UNKNOWN_SERVER_AUTHORITY: bind/freeze duration and immunity rules
- UNKNOWN_SERVER_AUTHORITY: teleblock duration/clearing rules
- UNKNOWN_SERVER_AUTHORITY: rune/item consumption policy
- UNKNOWN_SERVER_AUTHORITY: autocast rules
- UNKNOWN_SERVER_AUTHORITY: cast cooldown/tick timing
- UNKNOWN_SERVER_AUTHORITY: splash/resistance rules
- UNKNOWN_SERVER_AUTHORITY: production server enforcement of every visible
  client requirement
- UNKNOWN_SERVER_AUTHORITY: most spell secondary effects

Client-side requirement checks and animations are not a substitute for server
authority.

## FILES / METHODS

Exact current v308:
- `rs.Client` menu action 169 -> C2S185 prayer/widget request + local varp toggle
- `rs.Client` menu action 315 -> C2S185 direct widget request
- `rs.Client` menu action 626 -> local spell selection, no packet
- `rs.Client` actions 365/413/956/94/543 -> semantic spell-target transports
- `rs.a.k.a(rs.x.e)` -> packet-81 appearance fields `bd/bf`
- player overhead renderer -> `headicons_prayer[bd]`
- `rs.x.e.d/n/o/p` writer transforms

Current research/corpus:
- `server/src/spk/local/data/research_r82/r30_prayer_presentation_r82.tsv`
- `server/src/spk/local/data/research_r82/r30_spell_presentation_r82.tsv`
- `server/src/spk/local/data/research_r82/r30_spell_tuple_closure_r82.tsv`
- `server/research/r83/client_discovery_r2/02_STANDARD_SPELL_FILTER_CATALOG.csv`
- `server/research/r83/client_discovery_r2/07_CUSTOM_MAGIC_BUILDERS.csv`
- `server/research/r84/application_protocol_r4/08_CUSTOM_MAGIC_AND_RUNTIME_STATE.md`

Normalized query surfaces:
- `PrayerDefinitionRepository`
- `SpellDefinitionRepository`

## READY FOR CHAT 2

**yes — transport/state evidence**

Transport should model semantic widget/target requests without exposing raw
opcodes as gameplay API.

## READY FOR CHAT 3

**yes — interface/presentation/visible-requirement evidence**

Chat 3 can use the exact visible spell/prayer identities and target categories
while keeping authoritative mechanics protocol-independent.

All items under `UNKNOWN_SERVER_AUTHORITY` require separate production
evidence or an explicit LocalLab policy.
