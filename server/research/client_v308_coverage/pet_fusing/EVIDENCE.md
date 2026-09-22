# Evidence Package — Legendary Pet Fusing (Exact v308)

SYSTEM

Legacy/native Legendary Pet Fusing interface.

STATUS

STRONG-PARTIAL / STATIC CLIENT CONTRACT CLOSED

## AUTHORITY

Exact-current client:

```
SHA-256 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
build/config 308
```

Exact classes:

```
rs.n.c.aE
rs.n.e
rs.Client
rs.x.e
```

Authority:

`EXACT_CURRENT_CLIENT`

## ROOT

Exact interface root:

```
18547
```

The root installs 12 children.

Client-compatible S2C97 open body:

```
18547 -> 48 73
```

## STATIC PRESENTATION

The exact v308 client ships the following static text:

```
@or1@Legendary Pet Fusing
@gre@Limited time pet fusion!
New pet ETA: @whi@10/26/2016
Fuse
```

This is exact-current **client content**, but it is visibly a legacy/time-bound
presentation.

It must not be interpreted as proof that the current/original production server
still uses the dated recipe/event schedule.

## WIDGETS

Core root children:

```
65552  background sprite
65553  title "@or1@Legendary Pet Fusing"
65554  subtitle "@gre@Limited time pet fusion!"
18552  "New pet ETA: @whi@10/26/2016"

18548  item/container-style recipe widget
18549  item/container-style ingredient/currency widget
18550  item/container-style result widget

65559  action button, tooltip "Fuse pets"
65560  hover sprite
65562  visible "Fuse" text

65418 / 65419 close control
```

## STATIC ITEM/QUANTITY DEFAULTS

The exact builder directly initializes item-array values.

### Widget 18548

Two entries:

```
az[0] = 12112
ax[0] = 3

az[1] = 15001
ax[1] = 3
```

### Widget 18549

```
az[0] = 11338
ax[0] = 500
```

### Widget 18550

```
az[0] = 12114
ax[0] = 1
```

The exact client interface-item convention stores definition id + 1 in these
arrays, so the corresponding item-definition ids are:

```
12111 x3
15000 x3
11337 x500
12113 x1
```

These are static client defaults only.

They do **not** prove:

- current production recipe;
- live server cost;
- whether all ingredients are consumed;
- success probability;
- whether the output is guaranteed;
- whether the interface was still active in production.

## FUSE ACTION — EXACT

Widget 65559 is created through the ordinary action-enabled sprite helper:

```
aI = 5
M  = 1
Q  = "Fuse pets"
```

Exact client menu path:

```
action-enabled widget
 -> menu action 315
 -> C2S185
 -> rs.x.e.d(widgetId)
```

### Critical high-id truncation

Internal widget id:

```
65559 = 0x10017
```

C2S185 writes only a two-byte widget id.

Therefore the exact wire value is:

```
low16 = 0x0017 = 23
body  = 00 17
```

No high bits are transmitted.

A raw server-side comparison against integer 65559 can therefore never match
this exact client action.

Semantic resolution requires higher-level context such as active root 18547.

This is another exact instance of the global C2S185 high-widget-id aliasing rule.

## CLOSE ACTION

The shared close widget remains below 65536:

```
65418 -> C2S185 body FF 8A
```

## SERVER -> CLIENT PRESENTATION

The dynamic item-style widgets are low ids:

```
18548
18549
18550
```

and are structurally compatible with ordinary item-container publication such
as S2C53.

That establishes an available presentation route.

It does not prove whether the production server dynamically rewrote these slots
or relied on the static client defaults.

The high static text/button ids in the 65552+ family cannot be addressed as
their full internal ids through ordinary 16-bit widget-id packet fields.

## SERVER SEMANTICS PROVEN

Exact client proves:

- root 18547;
- static Legendary Pet Fusing presentation;
- static recipe/result item-array defaults;
- exact Fuse action;
- exact C2S185 low16 wire alias for 65559;
- exact close action.

## SERVER SEMANTICS UNKNOWN

`UNKNOWN_SERVER_AUTHORITY`:

- whether this legacy interface remained production-active;
- current/live recipe;
- pet eligibility;
- ingredient meaning;
- cost;
- consumption policy;
- output ownership;
- success/failure probability;
- duplicate handling;
- inventory-space behavior;
- persistence;
- rate limits;
- event start/end dates;
- whether production replaced the hard-coded 2016 content dynamically.

## LOCAL LAB MAPPING

Existing generic conversion infrastructure is the correct substrate.

Do not create a parallel pet-specific transaction engine merely because this
interface exists.

A semantic adapter can map:

```
active root 18547 + C2S185 wire widget 23
 -> PET_FUSION_ATTEMPT
```

while recipe/cost/result live in semantic conversion definitions.

Any LocalLab recipe chosen without stronger evidence must be marked
`LOCAL_LAB_POLICY`, not recovered SpawnPK authority.

## TEST VECTORS

```
Open root:
18547 -> S2C97 48 73

Fuse:
internal widget 65559
low16 wire id 23
C2S185 body 00 17

Close:
65418 -> C2S185 FF 8A
```

## READY FOR CHAT 2

**yes, with high-id context warning**

No new transport is needed.

## READY FOR CHAT 3

**yes for the legacy client contract; no for live recipe/mechanics**

Chat 3 can compose this interface onto ConversionService while explicitly
separating exact static client defaults from chosen/recovered server recipes.
