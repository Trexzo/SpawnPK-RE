# Evidence Package — Exact v308 NPC Definition Override Census

SYSTEM

Exact-current SpawnPK NPC override metadata and v308 loader dispatch contract.

STATUS

KEY-CENSUS-CLOSED / PRESENTATION-FIELD-MAPPING-STRONG / SERVER-MECHANICS-SEPARATE

## AUTHORITY

Exact-current client:

~~~
client(6).jar
SHA-256 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
build/config 308
~~~

Exact matched NPC override config:

~~~
.spawnpk/configs/e.bin
SHA-256 16b62a3871d8f2c8601a3986d44652b395c38a834f6ea107d8d2be2807e7bed1
916 override records
45 distinct field keys
~~~

Exact loader:

~~~
rs.t.a.b
~~~

## DISPATCH RESULT

Of the 45 exact-current e.bin field keys:

~~~
44 / 45 are recognized by the exact v308 loader
1 / 45 is wholly unconsumed
~~~

Among the 44 recognized keys:

~~~
tags
~~~

is intentionally a no-op branch in this definition loader.

The sole unconsumed key is:

~~~
offsets
~~~

## EXACT UNCONSUMED offsets RECORD

`offsets` appears in exactly one current override:

~~~
NPC id: 8229
name: Riftwalker corp beast pet
clone: 6005
offsets: [-16, -17]
scaleWidth: 32
scaleHeight: 32
size: 1
pet: true
~~~

The exact loader lowercases keys and has no `offsets` dispatch branch.

It falls through the unknown-config path.

Therefore:

~~~
e.bin:8229.offsets
= EXACT_CURRENT_CACHE_STALE_OR_UNCONSUMED
= not active v308 NPC presentation authority
~~~

Do not silently map it onto another positional/scale field.

## EXACT FIELD FAMILIES

### Identity / clone

~~~
name
clone
osrs
~~~

### Interaction / visible metadata

~~~
actions
combatLevel
size
minimap
pet
clickable
tags
~~~

### Models

~~~
models
chatHeadModels
~~~

### Animation

~~~
standAnim
walkAnim
rotateAnim / rotateanim
rotateAnim180
rotateAnim90CW
rotateAnim90CCW
rotationSpeed
renderIdle
~~~

### Scale / offset-related

~~~
scaleHeight
scaleWidth
scaling
offsets   <-- unconsumed by exact v308
~~~

### Colour / texture / lighting / render

~~~
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
~~~

### Icons

~~~
mapIcon
icon
headIcon
iconX
iconY
iconZ
~~~

### Misc exact client state

~~~
healthBarColor
anInt57
~~~

## HIGH-VOLUME EXACT FIELDS

Current override counts include:

~~~
name          909
actions       705
models        583
standAnim     512
walkAnim      494
combatLevel   476
scaleHeight   453
scaleWidth    449
destColors    368
srcColors     368
size          308
minimap       279
pet           256
rotateAnim    241
chatHeadModels 187
osrs          174
clone         149
~~~

Full counts/types remain in `npc_definition_fields.tsv`.

## EXACT LOADER TARGET EXAMPLES

Examples from the exact loader target map:

~~~
name        -> o:Ljava/lang/String;
actions     -> p:[Ljava/lang/String;
models      -> L:[I
standAnim   -> w:I
walkAnim    -> q:I
combatLevel -> n:I
scaleHeight -> E:I
scaleWidth  -> I:I
srcColors   -> v:[I
destColors  -> s:[I
size        -> r:B
minimap     -> F:Z
chatHeadModels -> t:[I
rotationSpeed -> y:I
clickable   -> C:Z
mapIcon     -> S:I
icon        -> T:I
headIcon    -> u:I
~~~

`pet=true` mutates multiple exact client flags (`h`, `F`, `K`), proving a concrete client classification/presentation effect.

That still does not prove server ownership/follow mechanics.

## IMPORTANT NO-OP: tags

`tags` appears on 33 exact overrides.

The loader recognizes it but performs no NPC-definition mutation.

Therefore:

~~~
tags = exact cache metadata
     != active v308 NPC definition state
~~~

Do not expose tags as runtime client authority unless another independent consumer is proven.

## SERVER-AUTHORITY BOUNDARY

Exact consumed fields prove client/config presentation and query metadata.

They do not automatically prove server gameplay mechanics.

Examples:

- `combatLevel` proves displayed/client metadata, not NPC HP or combat formula;
- `pet=true` proves classification/presentation, not ownership or follow rules;
- `actions` proves client menu labels, not that the server must accept every action;
- animation/model fields prove rendering identity, not attack timing or AI;
- `size` proves client footprint metadata but collision/pathing policy remains authoritative server state.

## RECOMMENDED DOMAIN BOUNDARY

~~~
ExactNpcDefinitionRepository
  -> models / animations / actions / icons / visible metadata

NpcContentDefinition
  -> provenance-aware semantic content definition

NpcLifecycle / Combat / Spawn / Pet services
  -> authoritative gameplay enforcement
~~~

Chat 3 should not parse e.bin directly inside gameplay handlers.

## READY FOR CHAT 3

yes

This is strong enough to back NPC catalogs, Monster Spawner presentation, spawn-content definitions and query surfaces with exact-current client/cache provenance.

## REMAINING CHAT 4 NPC WORK

- base-cache + override precedence;
- downstream model-builder/recolour/retexture semantics;
- NPC morph/config transforms outside custom e.bin overrides;
- global animation/GFX dependency indexing;
- unresolved legacy/special renderer families.