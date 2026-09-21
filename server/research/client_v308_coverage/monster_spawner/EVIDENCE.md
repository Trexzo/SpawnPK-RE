# Evidence Package — Monster Spawner (Exact v308)

SYSTEM

SpawnPK Monster Spawner interface and NPC-selection controls.

STATUS

STRONG-PARTIAL / STATIC CLIENT CONTRACT CLOSED

## AUTHORITY

Primary exact-current client:

```
SHA-256 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
build/config 308
```

Exact classes:

```
rs.n.c.T
rs.n.e
rs.Client
rs.x.e
```

Authority:

`EXACT_CURRENT_CLIENT`

## ROOT

Exact Monster Spawner root:

```
41000
```

Client-compatible S2C97 open body:

```
41000 -> A0 28
```

## STATIC ROOT STRUCTURE

Exact root 41000 installs 14 child entries.

Core widgets:

```
41001  background "factory/SPRITE"

41002  Close Window button
41004  close hover sprite

41006  rectangular/background element

41007  action button
       tooltip "Toggle spawner"
41008  hover sprite

41010  title "Monster Spawner"
41011  heading "Monster Selections"

41012  "This spawner will provide you with 5 spawns"
41013  "before requiring you to re-activate it again."
41014  blank text row

41016  "<img=57> Activate @gre@(x5)"

41019  "You have selected: @yel@NPC Name"

41020  NPC-selection scroll root
```

### Important static-builder distinction

The exact class also constructs:

```
41017  "Spawn distanced"
41018  "Spawn x3"
```

but **neither 41017 nor 41018 is attached as a child of root 41000 by the static
builder**.

Therefore this evidence does not treat them as active clickable controls.

They are exact widget definitions, but their production visibility/use requires
additional evidence.

Likewise `41016` is a normal text widget layered over the spawner activation
area. The exact actionable activation control is `41007`, not 41016.

## TOGGLE / ACTIVATE ACTION — EXACT

Widget 41007 is created with the action-enabled sprite helper:

```
widget.aI = 5
widget.M  = 1
widget.Q  = "Toggle spawner"
```

Exact client menu routing:

```
action-enabled widget
 -> menu action 315
 -> C2S185
 -> rs.x.e.d(widgetId)
```

Exact body:

```
41007 -> A0 2F
```

Visible adjacent client presentation:

```
<img=57> Activate @gre@(x5)
```

and:

```
This spawner will provide you with 5 spawns
before requiring you to re-activate it again.
```

This proves the client **presents** an x5/re-activation model.

It does not by itself prove the original authoritative server counter,
decrement timing, eligibility, cost or reset policy.

## NPC SELECTION SCROLL — EXACT

Scroll root:

```
41020
```

Exact static dimensions/state include:

```
scroll max / height fields: 350 / 143
22 children
```

Exact action row ids:

```
41021..41042
```

Every row is created through the action-enabled text helper with tooltip:

```
"Spawn this NPC"
```

The helper sets:

```
widget.aI = 4
widget.M  = 1
```

so every row uses the same menu action 315 -> C2S185 transport.

Wire mapping:

```
row 0   widget 41021 -> A0 3D
...
row 21  widget 41042 -> A0 52
```

General formula:

```
rowIndex 0..21
widgetId = 41021 + rowIndex
C2S185 body = widgetId as u16_be
```

## DEFAULT ROW LABELS — EXACT

The exact invokedynamic concat recipe is:

```
NPC IDX @yel@<rowIndex> (<widgetId>)
```

Examples:

```
NPC IDX @yel@0 (41021)
...
NPC IDX @yel@21 (41042)
```

These are clearly client-side static/default labels.

They do not prove the production NPC catalog.

The ordinary text widgets are compatible with S2C126 updates, allowing a server
to publish semantic NPC names/state into those rows.

That is a compatible presentation path, not proof of the production update
mechanism.

## SELECTED NPC PRESENTATION

Exact widget:

```
41019
```

default text:

```
You have selected: @yel@NPC Name
```

This proves a selected-NPC presentation channel exists.

S2C126 can target ordinary text widgets such as 41019.

The actual selected NPC id/name mapping remains server-owned.

## SERVER SEMANTICS PROVEN

Exact client proves:

- Monster Spawner root 41000;
- exact Toggle Spawner action widget 41007;
- x5/re-activation presentation wording;
- exact 22 row widget ids;
- exact C2S185 row-selection transport;
- selected-NPC text channel 41019;
- the static client row labels are placeholders based on row/widget index;
- 41017/41018 exist as definitions but are not static children of root 41000.

## SERVER SEMANTICS UNKNOWN

`UNKNOWN_SERVER_AUTHORITY`:

- available NPC catalog;
- row -> NPC mapping;
- whether the list changes by account/progression;
- exact five-spawn counter implementation;
- whether activation has a cost;
- activation cooldown;
- spawn ownership;
- maximum simultaneously active spawns;
- spawn coordinates;
- "distanced" behavior;
- "x3" behavior;
- whether 41017/41018 are activated dynamically elsewhere;
- NPC lifetime;
- logout/disconnect cleanup;
- death behavior;
- instance/world scoping;
- anti-abuse/rate limits;
- persistence.

## LOCAL LAB MAPPING

A semantic Monster Spawner implementation should not expose row widget ids as
NPC identity.

Suitable shape:

```
MonsterSpawnerSelection
MonsterSpawnerDefinition / catalog
MonsterSpawnerActivationState
spawn ownership through authoritative World / WorldInstance
```

The exact row index/widget mapping belongs in a presentation adapter.

Existing NPC/world ownership infrastructure should be reused rather than
introducing an independent entity registry.

## TEST VECTORS

```
Toggle spawner:
41007 -> A0 2F

NPC rows:
41021 -> A0 3D
41022 -> A0 3E
...
41042 -> A0 52
```

All are exact C2S185 u16-be widget-id bodies before ISAAC opcode framing.

## READY FOR CHAT 2

**yes**

No new wire family is required. Exact input uses existing C2S185.

## READY FOR CHAT 3

**yes for interface/action contract; no for NPC catalog/spawn policy**

Chat 3 can implement a semantic spawner service on top of canonical World/NPC
ownership without depending on raw client archaeology.
