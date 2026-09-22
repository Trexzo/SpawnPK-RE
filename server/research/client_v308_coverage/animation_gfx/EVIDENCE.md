# Evidence Package — Exact-current Animation + GFX Definition Census

SYSTEM

Exact-current SpawnPK animation (`a.bin`) and spot-animation/GFX (`g.bin`) override metadata, loader vocabulary, and cross-definition dependency coverage.

STATUS

KEY-CENSUS-CLOSED / CROSS-REFERENCE-STRONG / DOWNSTREAM-RENDER-TIMING-STILL-PARTIAL

## AUTHORITY

Exact-current client:

~~~
client(6).jar
SHA-256 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
build/config 308
~~~

Exact matched cache/config:

~~~
.spawnpk/configs/a.bin
SHA-256 81dabed5d25e168d5da2f6fb193d3d9e11df59cf3f2174078d4d007d447d55ff
657 override records
max override id 16204

.spawnpk/configs/g.bin
SHA-256 dbd20f329b95ba133494225ef4c97105b28c3ed44af159b01123d3e218ca13df
262 override records
max override id 5109
~~~

Exact loaders:

~~~
rs.t.a.a  -> animation overrides
rs.t.a.c  -> GFX overrides
~~~

## ANIMATION OVERRIDE CENSUS

Exact `a.bin` fields actually present:

| Field | Present | Exact value type |
|---|---:|---|
| ref | 657 | string/null |
| durations | 638 | list |
| osid | 637 | int |
| frames | 637 | int |
| frameIds | 637 | list |
| priority | 242 | int |
| frameStep | 203 | int |
| animMayaID | 107 | int |
| animMayaEnd | 107 | int |
| stretches | 42 | bool |
| walkPrecedence | 29 | int |
| precedence | 28 | int |
| playerMainhand | 14 | int |
| replayMode | 11 | int |
| clone | 8 | int |
| playerOffhand | 7 | int |
| walkable | 6 | bool |
| maxLoops | 4 | int |
| framestep | 1 | int |
| walkprecedence | 1 | int |
| replaymode | 1 | int |

The exact loader also recognizes aliases/fields including:

~~~
animMayaStart
interleave
secondary
~~~

even when they do not occur in the current 657-record corpus.

Case/legacy spelling variants such as `frameStep`/`framestep`, `walkPrecedence`/`walkprecedence`, and `replayMode`/`replaymode` are exact-current cache realities and should retain source provenance.

## GFX OVERRIDE CENSUS

Exact `g.bin` fields actually present:

| Field | Present | Exact value type |
|---|---:|---|
| ref | 262 | string |
| model | 193 | int |
| anim | 193 | int |
| osid | 186 | int |
| srcColors | 135 | list |
| destColors | 131 | list |
| ambient | 94 | int |
| contrast | 85 | int |
| clone | 69 | int |
| resizeX | 40 | int |
| resizeY | 39 | int |
| osrs | 8 | bool |
| destcolors | 4 | list |
| reshade | 2 | int |
| rotation | 1 | int |

The exact loader additionally recognizes aliases/families including:

~~~
animation
modelId
recolor / recolors
retextures
textures
~~~

without implying those aliases all occur in the current corpus.

## NPC -> ANIMATION DEPENDENCY INDEX

Across exact-current `e.bin` animation-bearing NPC fields:

~~~
fields inspected:
standAnim
walkAnim
rotateAnim / rotateanim
rotateAnim180
rotateAnim90CW
rotateAnim90CCW

1,318 total animation field uses
342 distinct animation ids
199 distinct references in classic base range (<15260)
143 distinct high/custom references (>=15260)
143 / 143 high references resolve to exact-current a.bin overrides
~~~

This is strong exact-current provenance that the high custom NPC animations in `e.bin` are backed by the current animation override corpus rather than dangling arbitrary IDs.

Representative matched references include:

~~~
15312  standAnim -> Venenatis' Spiderling
15313  walkAnim -> Venenatis' Spiderling
15338  standAnim -> Sarachnis
15339  walkAnim -> Sarachnis
15380  standAnim -> Jack Frost
15381  walkAnim -> Jack Frost
15382  rotateAnim180 -> Jack Frost
15383  rotateAnim90CW -> Jack Frost
15384  rotateAnim90CCW -> Jack Frost
16124  custom turn animation family
~~~

## GFX -> ANIMATION DEPENDENCY INDEX

Among exact-current `g.bin` records:

~~~
165 total non-negative animation references
117 distinct animation ids
113 distinct references resolve through a.bin
113 distinct high/custom references are >=15260
113 / 113 high/custom references resolve through a.bin
~~~

The remaining low references are base-animation references and therefore need base-cache lookup rather than an `a.bin` override row.

Representative exact custom GFX animation links include:

~~~
15306  Callisto trap
15317  Araxxor web projectile
15319  Araxxor venom projectile
15401  Ursine chainmace swing
15415  Osmumten fang spec gfx
15525  Ghommal teleport start
15696  elder maul spec gfx
15702  scorching bow projectile
16020  Olm lightning
16087  Araxxor blue projectile
16109  Vorkath area travel
~~~

## GFX -> MODEL DEPENDENCY INDEX

A deeper exact-v308 runtime trace closes the model-resolution ambiguity.

Exact GFX model construction:

~~~
rs.d.x.a()
  -> rs.cache.osrs.c.a(definition.o)
  -> rs.a.h.e(modelId)
~~~

The exact `g.bin` loader sets the GFX OSRS-cache flag:

~~~
osrs: <boolean> -> definition.o = value
osid: <integer> -> definition.o = true
~~~

So `osid` is not merely metadata; its presence selects the OSRS model-cache
context for the resulting GFX definition.

Across the exact-current `g.bin` corpus:

~~~
193 records contain a non-negative model id
125 distinct model ids

186 / 193 model-bearing rows select OSRS model-cache context
  7 / 193 model-bearing rows select primary model-cache context
~~~

The matched exact cache contains both model stores:

~~~
.spawnpk/main_file_cache.idx1
.spawnpk/main_file_osrs.idx1
.spawnpk/raw/<id> and raw/<id>.dat override paths
~~~

Resolution was checked using the exact selected context for every model-bearing
GFX definition:

~~~
193 / 193 GFX model references resolve
0 dangling model references
~~~

Specifically:

- OSRS-context rows resolve against `main_file_osrs.idx1`;
- primary-context rows resolve through the primary packed model index and/or
  exact loose `raw/` override set.

This corrects an earlier incomplete filesystem-only probe that appeared to leave
many model ids unresolved when only the primary model store was considered.

Do not classify a GFX model as missing without honoring the exact definition's
OSRS cache-context flag.

## AUTHORITY BOUNDARY

These config fields prove exact-current presentation definitions and dependency identity.

They do NOT automatically prove:

- server attack timing;
- hit delay;
- damage application tick;
- projectile travel duration;
- when a GFX should be spawned;
- whether a particular animation/GFX is authoritative for every gameplay action;
- NPC AI;
- cooldowns;
- original server formula or trigger rules.

Those remain server/domain authority unless another exact client/cache/runtime source closes them.

## ARCHITECTURE CONSEQUENCE

Chat 3 should consume provenance-aware definition repositories:

~~~
AnimationDefinitionRepository
GfxDefinitionRepository
Npcs/Items/Content -> semantic animation/GFX references
PresentationAdapter -> exact client ids
~~~

Gameplay code should not parse MessagePack or infer timing from frame counts ad hoc.

Frame/duration metadata can support presentation scheduling only where the exact consumer semantics are independently proven.

## CUSTOM-ASSET CONSEQUENCE

The current override ranges also provide exact occupancy information for custom-asset preflight:

~~~
base animation count: 15260
a.bin max override: 16204
client animation capacity: base + 20000 -> ids through 35259

base GFX count: 2964
g.bin max override: 5109
client GFX capacity: base + 5000 -> ids through 7963
~~~

Any CUSTOM_LOCALLAB allocation must remain collision-checked against the pinned base and exact override corpus.

## READY FOR CHAT 3

yes — exact definition and cross-reference authority is strong

## REMAINING CHAT 4 WORK

- downstream frame/model builder semantics;
- global model dependencies for GFX;
- exact packet/projectile/GFX attachment timing where not already recovered;
- Maya-animation branch semantics beyond field identity;
- base-cache + override precedence edge cases.