# Exact v308 Item/NPC Definition-Field Census

SYSTEM

Exact-current SpawnPK MessagePack definition overrides and the v308 client-loader dispatch boundary.

STATUS

KEY-DISPATCH-CENSUS-CLOSED / FIELD-SEMANIC-MAPPING-STRONG-PARTIAL / SERVER-RULE-AUTHORITY-SEPARATE

## AUTHORITIES

Exact-current client:

```
client(6).jar
SHA-256 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
build/config 308
```

Exact matched cache archive:

```
.spawnpk (2)(1).zip
SHA-256 607425ad3fe69f4cfcaaf82220d9a0d4ebff9954e896819245f37271713d299a
size 186,535,724 bytes
10,853 ZIP entries
```

Exact config payloads:

```
.spawnpk/configs/i.bin
SHA-256 3a5e23fd0bdc59bb17e0b0ea938ba376e291867a4f869bd2d7c3197243497f74
5,207 item override records
75 distinct field keys

.spawnpk/configs/e.bin
SHA-256 16b62a3871d8f2c8601a3986d44652b395c38a834f6ea107d8d2be2807e7bed1
916 NPC override records
45 distinct field keys
```

Both config files are ordinary MessagePack maps.

Exact v308 loaders:

```
items -> rs.t.a.d
NPCs  -> rs.t.a.b
```

## KEY-DISPATCH RESULT

### Items

All **75 / 75** field keys actually present in exact-current `i.bin` are accounted for by v308 item-loader control flow, but they are **not all applied as definition fields**.

Exact control-flow classes are now distinguished:

- `clone` / `fullClone` — handled in a pre-pass before the ordinary per-field switch;
- `equipClone` / `cloneEquip` — recognized names whose ordinary switch branch is a no-op and which have no equivalent clone pre-pass in `rs.t.a.d`;
- `param_1..param_8` — caught by the exact `param_` prefix check and skipped before the field switch;
- policy keys listed below — recognized but intentionally no-op for client gameplay enforcement;
- remaining keys — active definition/presentation mutation paths.

This distinction is important: "recognized by the loader" does not mean "mutates the client item definition."

Known exact client-definition no-op policy fields remain:

```
tradeable
bankable
autoloss
autolost
autobankable
autokeep
droppable
destroy
```

The exact loader recognizes those keys but does not turn them into client-enforced gameplay policy.

`broken=true` is different: it mutates client presentation/interaction state and forces the final inventory action to `Destroy`.

`beginnerGear` is recognized but is **not** an exact degradation flag.

`equipClone` and `cloneEquip` are also exact-current cache keys but are no-op in this exact item-definition loader. They must not be treated as synonyms for `clone` without another independent consumer. A whole-JAR string scan finds the lowercase `equipclone` / `cloneequip` vocabulary in `rs/t/a/d.class`, where these branches are no-op.

Likewise, `param_1..param_8` are not dynamic item-definition extensions in v308: the loader detects `param_` and immediately skips those map entries. Their intended external/tooling/server semantic remains separate evidence.

### NPCs

**44 / 45** field keys actually present in exact-current `e.bin` are recognized by the v308 NPC loader.

The sole unconsumed exact-current key is:

```
offsets
```

It appears in exactly one override:

```
NPC id: 8229
name: Riftwalker corp beast pet
clone: 6005
offsets: [-16, -17]
scaleWidth: 32
scaleHeight: 32
size: 1
pet: true
```

The exact NPC loader lowercases config keys before dispatch. `offsets` has no matching loader literal/branch; it reaches the loader's unknown-config path, which can log:

```
(ID {} '{}') Unknown config: {}
```

in diagnostic mode.

Therefore `e.bin:8229.offsets` must **not** be treated as active v308 presentation authority.

Possible provenance labels:

```
EXACT_CURRENT_CACHE_STALE_OR_UNCONSUMED
UNKNOWN_INTENDED_SEMANTIC
```

Do not silently map it onto another offset field.

## ITEM FIELD FAMILIES

The exact-current item vocabulary covers:

### Identity / cloning / template relationships

```
name
clone
fullClone
equipClone
cloneEquip
note
template
osrs
```

### Inventory interaction / policy / stacks

```
actions
groundActions
stackable
stackIds
stackAmounts
tradeable
bankable
autoloss
autolost
autobankable
autokeep
droppable
destroy
broken
beginnerGear
disableInventoryHover
disableInvHover
```

### Inventory model presentation

```
modelId / modelID
zoom
rotations
offsets
resize
ambient
contrast
opacity
zan2d
reshade
fullTexture
textureInvAnim / textureInvANim
```

### Wearable model presentation

```
maleModels
femaleModels
maleModel
femaleModel
maleModel1
femaleModel1
maleOffsets / maleoffsets
femaleOffsets / femaleoffsets
maleOffsetX / maleOffsetY
femaleOffsetX / femaleOffsetY
newMaleOffsets
newFemaleOffsets
```

### Colour / texture transforms

```
srcColors
destColors / destcolors
recolors
retexture
retextures
```

### Icon / overlay presentation

```
icon
iconX
iconY
iconOffsets
iconItem
```

### Text / skipped extension metadata

```
hover                 <-- active hover text path
param_1 .. param_8    <-- exact cache metadata skipped by v308 item-definition loader
```

Exact per-key counts and value types are in `item_definition_fields.tsv`.

## NPC FIELD FAMILIES

The exact-current NPC vocabulary covers:

### Identity / clone

```
name
clone
osrs
```

### Interaction / presentation metadata

```
actions
combatLevel
size
minimap
pet
clickable
tags
```

### Models

```
models
chatHeadModels
```

### Animation

```
standAnim
walkAnim
rotateAnim / rotateanim
rotateAnim180
rotateAnim90CW
rotateAnim90CCW
rotationSpeed
renderIdle
```

### Scale / offsets

```
scaleHeight
scaleWidth
scaling
offsets   <-- exact cache key but unconsumed by v308
```

### Colour / texture / lighting / render flags

```
srcColors
destColors / destcolors
recolors
retextures
ambient
contrast
opacity
reshade
glow
priority
priorityRender
```

### Icons

```
mapIcon
icon
headIcon
iconX
iconY
iconZ
```

### Misc client state

```
healthBarColor
anInt57
```

Exact per-key counts and value types are in `npc_definition_fields.tsv`.

## AUTHORITY BOUNDARY

A field being present in exact cache and consumed by the exact client proves a client/config presentation contract.

It does **not** automatically prove original server gameplay enforcement.

Examples:

- `combatLevel` is exact client-visible NPC metadata, but server combat formulas/HP remain server authority.
- `pet=true` proves exact config classification/presentation, not pet ownership/follow rules.
- `tradeable` is exact cache metadata but is intentionally a client-loader no-op in this generation.
- `actions` proves menu presentation, not whether the server accepts the action in every context.
- `note/template/clone` prove client definition relationships; server item-transaction policy remains separately authoritative.
- `equipClone` / `cloneEquip` are present in exact cache but do not mutate the v308 item definition through `rs.t.a.d`; do not infer wearable cloning from those keys.

Recommended server boundary:

```
ExactDefinitionRepository
  -> presentation/query metadata

ItemPolicy / NpcContentDefinition
  -> provenance-aware semantic data

Inventory/Trade/Death/Combat/Pet services
  -> authoritative gameplay enforcement
```

Do not make plugins/content parse MessagePack or depend on obfuscated loader fields.

## ALIAS / HISTORICAL-SPELLING RULE

The exact loaders normalize keys to lowercase before dispatch, so several exact-current source spellings converge at dispatch level, for example:

```
modelId / modelID
destColors / destcolors
maleOffsets / maleoffsets
femaleOffsets / femaleoffsets
textureInvAnim / textureInvANim
rotateAnim / rotateanim
```

Preserve original source spelling as provenance when normalizing.

Do not rewrite source data and lose its historical form.

## READY FOR CHAT 3

**yes**

The field vocabulary is now finite enough for a provenance-aware Item/NPC definition repository.

Chat 3 should consume semantic definitions, not raw cache keys.

## REMAINING CHAT 4 DEFINITION WORK

This closes the **override-key dispatch census**, not every deeper rendering semantic.

Still useful:

1. map each visual field to the exact obfuscated target field/model-builder read site;
2. close base-cache + override precedence for every model/action field;
3. normalize note/template/clone inheritance order;
4. map model/recolour/retexture effects through render construction;
5. recover exact NPC morph/config transforms outside the custom `e.bin` override layer;
6. index animation/GFX/model dependencies used by concrete content.

Those are narrower follow-ups now; the key vocabulary itself is no longer open.
