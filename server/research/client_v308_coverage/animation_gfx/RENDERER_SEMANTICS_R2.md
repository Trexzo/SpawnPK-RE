# Exact v308 GFX Downstream Renderer Semantics

SYSTEM

Exact-current SpawnPK spot-animation/GFX definition consumption after `g.bin` loading.

STATUS

STRONG-CLOSED-CORE-RENDERER / GAMEPLAY-TRIGGER-OWNERSHIP-UNKNOWN

## AUTHORITY

~~~
client(6).jar
SHA-256 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
build/config 308
~~~

Primary exact classes:

~~~
rs.d.x     GFX definition/model construction
rs.d.a     animation definition/application
rs.a.b     stationary world spot-graphic renderable
rs.a.l     projectile renderable
rs.a.k     player rendering / attached GFX
rs.a.j     NPC rendering / attached GFX
rs.t.a.c   g.bin override loader
rs.t.a.a   a.bin override loader
~~~

## EXACT GFX FIELD OWNERSHIP

The exact `g.bin` loader maps the current definition fields to `rs.d.x`:

| g.bin key family | exact `rs.d.x` target | downstream meaning proven here |
|---|---|---|
| `model` / `modelId` | `e` | source model id |
| `anim` / `animation` | `f` + `g` | animation definition id + resolved `rs.d.a` |
| `resizeX` | `j` | first/two-axis model scale input |
| `resizeY` | `k` | third-axis model scale input |
| `rotation` | `l` | stationary spot-graphic quarter-turn rotation |
| `ambient` | `m` | additive lighting ambient term |
| `contrast` | `n` | additive lighting contrast term |
| `srcColors` | `h` | source recolour values |
| `destColors` / legacy alias | `i` | destination recolour values |
| `osrs` / presence of `osid` | `o` | select OSRS model-cache context |
| `reshade`, `recolor(s)`, `retexture(s)` / `textures` | `r` | structured model-modifier stack applied before model cache |

## BASE MODEL CONSTRUCTION — `rs.d.x.a()`

The exact model path is:

~~~
GFX definition id
 -> definition model cache lookup by GFX id
 -> select model-store context using `o`
 -> rs.a.h.e(modelId=e)
 -> restore primary context
 -> exact hardcoded compatibility fix rs.d.x.a(model, gfxId)
 -> apply standard h[i] -> i[i] recolour pairs where the exact guard permits
 -> apply structured modifier stack `r` when present
 -> cache the resulting pre-animation model by GFX definition id
~~~

The model cache therefore contains the definition-level base model after recolour/custom modifier work, but before per-instance animation, scale, world rotation and lighting.

### Model-store context

`o` is actively consumed before `rs.a.h.e(e)` and restored immediately afterward.

This confirms the earlier exact-cache finding that OSRS-context GFX must resolve from the OSRS model store and primary-context GFX from the primary/raw model store.

## ANIMATION APPLICATION

`rs.d.x.g` is the resolved `rs.d.a` animation definition.

For a render instance the exact animation helper supports two branches:

### New/Maya-backed branch

The `a.bin` loader maps:

~~~
animMayaID    -> rs.d.a.B
animMayaStart -> rs.d.a.z
animMayaEnd   -> rs.d.a.A
~~~

`rs.d.a.b()` is exactly:

~~~
B >= 0
~~~

and therefore is the exact branch predicate for the Maya/new-animation path.

`rs.d.a.d()` loads the corresponding `rs.u.b` animation object by `B`.

`rs.d.a.c()` returns:

~~~
A - z
~~~

which is the exact completion span used by stationary GFX ticking.

`rs.d.a.a(model, frameIndex)` handles both branches:

- Maya/new path: clone model appropriately, then `model.a(rs.u.b, frameIndex)`;
- legacy path: resolve the frame id and apply the classic frame transform.

Do not infer server hit timing from either animation representation.

### Legacy frame/duration branch

For non-Maya animations, stationary GFX accumulates elapsed ticks and compares them against exact `rs.d.a.a(frameIndex)` duration values.

The frame advances only after elapsed time exceeds the current frame duration, subtracting `duration + 1` as it advances.

## STATIONARY WORLD SPOT GRAPHIC — `rs.a.b`

This is the renderable used by the exact local/world spot-graphic presentation path.

Per render:

1. obtain the definition base model from `rs.d.x.a()`;
2. apply the current animation frame while the effect is active;
3. if `(j,k) != (128,128)`, scale with:

~~~
model.b(j, j, k)
~~~

4. apply definition rotation `l` only for the exact quarter turns:

~~~
90  -> model.o() once
180 -> model.o() twice
270 -> model.o() three times
~~~

5. light with:

~~~
ambient  = 64  + m
contrast = 850 + n
light vector = (-30, -50, -30)
boolean lighting flag = true
~~~

### Stationary GFX tick/completion

For Maya/new animations:

~~~
frameIndex += delta
finished when frameIndex >= (animMayaEnd - animMayaStart)
~~~

For legacy animations:

~~~
elapsed += delta
while elapsed > duration[currentFrame]:
    elapsed -= duration[currentFrame] + 1
    currentFrame++
~~~

When the legacy frame index reaches the animation frame count, the renderable resets its frame index to zero and marks itself finished.

## PROJECTILE PATH — `rs.a.l`

The exact projectile renderer also consumes:

~~~
j / k -> model.b(j, j, k)
m / n -> lighting 64+m, 850+n
~~~

but it does **not** consume the GFX definition rotation field `l` in the recovered renderer path.

Projectile orientation instead comes from projectile trajectory/orientation state.

Therefore `rotation` in `g.bin` must not be blindly applied a second time by a server/presentation reimplementation for projectile orientation.

## ACTOR-ATTACHED GFX — `rs.a.k` / `rs.a.j`

Player/NPC attached spot-animation composition likewise consumes:

~~~
j / k -> model.b(j, j, k)
m / n -> lighting 64+m, 850+n
~~~

and the recovered actor-composition paths do not read definition rotation `l`.

Actor alignment/offset composition is handled by actor presentation state instead.

## HARD BOUNDARY: PRESENTATION VS GAMEPLAY

The exact renderer now proves:

- which model store supplies a GFX model;
- when recolour/modifier state is baked into the cached definition model;
- how the animation representation is selected;
- how stationary GFX frame progression works;
- exact scale, quarter-turn rotation and lighting consumption;
- the important context distinction between stationary, projectile and actor-attached GFX.

It still does **not** prove:

- when the server should create a GFX;
- attack/hit delay;
- projectile launch or impact tick;
- damage timing;
- spell/special correctness;
- cooldowns;
- authoritative combat formulas.

Those remain `UNKNOWN_SERVER_AUTHORITY` unless a separate exact runtime/server source closes them.

## ARCHITECTURE CONSEQUENCE

Chat 3 should treat animation/GFX definitions as presentation data:

~~~
semantic combat/content event
 -> presentation profile
 -> exact AnimationId / GfxId
 -> client presentation adapter
~~~

Do not derive damage timing from animation frame counts, and do not reimplement model transformations in gameplay services.

## READY FOR CHAT 3

yes — core downstream GFX renderer semantics are now exact

## REMAINING CHAT 4 WORK

- downstream animation frame/model/Maya internals only where they affect observable presentation compatibility;
- exact projectile/attachment scheduling from runtime/client consumers where separately recoverable;
- gameplay trigger ownership remains server/runtime evidence, not client-definition inference.