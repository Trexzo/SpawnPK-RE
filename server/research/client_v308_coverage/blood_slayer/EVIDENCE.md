# Evidence Package — Blood Slayer Selector (Exact v308)

SYSTEM

SpawnPK Blood Slayer task-mode selector / point presentation.

STATUS

STRONG-PARTIAL / SELECTOR CLIENT CONTRACT CLOSED

## AUTHORITY

Primary exact-current client:

```
SHA-256 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
build/config 308
```

Exact classes:

```
rs.n.c.l
rs.n.e
rs.Client
rs.x.e
rs.s.h.a
rs.gui.b.e
```

Authority:

`EXACT_CURRENT_CLIENT`

## ROOT

Exact Blood Slayer selector root:

```
54100
```

This is a true interface root, unlike the Tournament hub where 56000+ are child
widgets of root 27400.

Exact S2C97-compatible open body:

```
54100 -> D3 54
```

## EXACT CLIENT WIDGETS

`rs.n.c.l.a()` constructs:

```
54101  background sprite "skills/image 7"
54102  "Blood Slayer"
54103  "Choose a Task"
54104  "Easy task monsters"

54105  selector/decorative sprite
54106  selector/decorative sprite
54107  selector/decorative sprite
54108  selector/decorative sprite

54109  "<tab=20>Monster hunter @yel@(PvM)"
       tooltip: "Select timed task"

54110  "<tab=20>Boss hunter @yel@(PvM)"
       tooltip: "Select boss hunter task"

54111  "<tab=20>Bounty hunter @yel@(PK)"
       tooltip: "Select man hunter task"

54112  "<tab=20>Slaughter @yel@(PK)"
       tooltip: "Select man slaughter task"

54113  Get-task button
       tooltip: "Get a blood slayer task"

54114  hover sprite for Get Task
54116  "<img=24> Get a task"

54117  "<u=16776960>Reward points"
54118  "@yel@5 <col=FF9B00>Blood slayer points"
54119  "@yel@5 <col=FF9B00>Slayer points"
54120  blank text row

63740 / 63741  shared close-control widgets
```

Root child count is exactly 20.

## EXACT MODE ACTION TRANSPORT

Widgets 54109..54112 are created through the same action-enabled text helper
used by other exact-v308 application UIs.

The helper sets:

```
widget.aI = 4
widget.M  = 1
widget.Q  = tooltip
```

Exact `rs.Client` turns this actionable widget form into menu action:

```
315
```

Exact menu action 315 emits:

```
C2S185
body = widgetId via rs.x.e.d(int)
```

`rs.x.e.d(int)` writes u16 big-endian.

Therefore:

### Monster hunter (PvM)

```
widget 54109
C2S185 body D3 5D
```

### Boss hunter (PvM)

```
widget 54110
C2S185 body D3 5E
```

### Bounty hunter (PK)

```
widget 54111
C2S185 body D3 5F
```

### Slaughter (PK)

```
widget 54112
C2S185 body D3 60
```

These four actions prove task-mode selection intent at the client boundary.

They do not prove how the production server defines, assigns or completes the
selected task.

## GET TASK ACTION — EXACT

Widget 54113 is created through the exact action-enabled sprite/button helper.

The helper sets:

```
widget.aI = 5
widget.M  = 1
widget.Q  = "Get a blood slayer task"
```

It enters the same menu action 315 -> C2S185 path.

Exact vector:

```
widget 54113
C2S185 body D3 61
```

## POINT PRESENTATION

The client ships static/default text:

```
54118  "@yel@5 <col=FF9B00>Blood slayer points"
54119  "@yel@5 <col=FF9B00>Slayer points"
```

The literal value `5` is **client presentation/default content only**.

It must not be promoted to original server reward authority.

Both are ordinary text widgets and are compatible with exact S2C126 text
updates:

```
target 54118 -> Blood Slayer point text
target 54119 -> Slayer point text
```

Widget 54120 is an additional blank text row available for server-authored
presentation.

## TASK / TIMED-EFFECT CROSS-EVIDENCE

Exact v308 contains separate generic timed-effect/status presentation labels:

```
"Slayer task"
"Blood slayer task"
"Slayer task (normal)"
"Soul hunter task"
```

in the generic timed-effect/status UI logic (`rs.s.h.a`).

This proves that Blood Slayer task state participates in broader client status
presentation.

It does **not** reveal:

- task target identity;
- required amount;
- remaining amount;
- expiry behavior;
- completion reward;
- cancellation/skip cost.

Those remain server authority.

## CLIENT GUIDE METADATA

Exact `rs.Client` also recognizes visible text containing:

```
"@yel@Blood slayer master"
```

and associates the local guide/help key:

```
"BLOOD_SLAYER"
```

The separate GUI icon catalog contains:

```
BLOOD_SLAYER
index 9
display "Blood Slayer"
asset /assets/gui/icons/slayerblood.png
```

This is client-local guide/navigation metadata, not server gameplay authority.

## SEARCH NEGATIVE / BOUNDARY

A literal-class scan of the exact v308 JAR for Blood Slayer/Slayer-task strings
found the dedicated selector builder plus generic task/timed-effect and guide
presentation classes, but no second named Blood-Slayer-specific interface
builder was identified in this pass.

That does **not** prove no additional task detail is delivered through generic
dynamic widgets, S2C126, S2C250 or another non-literal route.

It does establish that the selector contract above is the dedicated static
Blood Slayer interface we can close without guessing.

## SERVER-TO-CLIENT PRESENTATION

Exact compatible presentation paths now proven:

- S2C97 -> open root 54100;
- S2C126 -> update text widgets including 54118/54119/54120;
- generic interface close path -> S2C219.

No Blood-Slayer-specific S2C opcode is proven by this selector builder.

## SERVER SEMANTICS PROVEN

Exact client proves:

- four task mode identities;
- their visible PvM/PK categorization;
- exact mode widget ids;
- exact Get Task widget id;
- exact C2S185 action transport;
- separate visible Blood Slayer / Slayer point text channels;
- existence of Blood Slayer task status presentation in the generic timed-effect UI.

## SERVER SEMANTICS UNKNOWN

`UNKNOWN_SERVER_AUTHORITY`:

- task catalogs;
- whether "Easy task monsters" is a mode, current category or placeholder;
- mode eligibility;
- combat-level requirements;
- assignment algorithm;
- target selection;
- task amounts;
- timed-task duration;
- Boss Hunter target pool;
- Bounty Hunter target relationship to the existing Bounty service;
- Slaughter rules;
- skip/cancel/reset behavior;
- Blood Slayer point reward;
- normal Slayer point reward;
- streaks;
- Slayer Crystal interaction;
- reward-shop catalog;
- reward-shop prices;
- persistence/reset rules;
- anti-abuse rules.

## LOCAL LAB MAPPING

Existing protocol-independent foundations suitable for composition:

```
ObjectiveProgressService
BountyHunterService
CombatOutcome / objective bindings
SemanticTimedEffectService / TimedEffectCatalog
reward / currency infrastructure
```

Do not create another generic progress ledger merely for Blood Slayer.

A likely semantic shape for Chat 3 is:

```
BloodSlayerMode
BloodSlayerTaskDefinition
BloodSlayerAssignment
BloodSlayerProgress
BloodSlayerPoints
```

with ObjectiveProgress used where appropriate.

That is an architectural recommendation, not recovered production behavior.

## TEST VECTORS

```
Monster hunter  54109 -> D3 5D
Boss hunter     54110 -> D3 5E
Bounty hunter   54111 -> D3 5F
Slaughter       54112 -> D3 60
Get task        54113 -> D3 61
```

All bodies are exact C2S185 u16-be widget ids before opcode ISAAC framing.

## READY FOR CHAT 2

**yes**

No new packet family is needed. All selector inputs use the existing exact
C2S185 widget-action transport.

## READY FOR CHAT 3

**yes for selector/domain entry contract; no for original task mechanics**

Chat 3 can now implement a semantic Blood Slayer slice without depending on raw
widget archaeology, while keeping assignment/reward rules explicitly
evidence-gated.
