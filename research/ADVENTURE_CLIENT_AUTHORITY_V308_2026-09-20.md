# Exact-current Adventure client authority — v308

Date: 2026-09-20

Newest supplied exact-current client:

`854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6`

This note records the exact current-client Adventure presentation/control contract.
It does **not** infer SpawnPK server-side Adventure objectives, progression, rewards,
eligibility, reset policy or persistence.

## Exact outbound Adventure intent

The native orb renderer/controller (`rs.i.b`) loads the exact assets:

- `orbs/adventure_orb`
- `orbs/adventure_orb_hover`

The hover path exposes the native label:

- `Adventure book`
- `Adventure Book`

The main client click/action path checks the renderer's Adventure hover flag and
queues exactly:

```
::adventurebook
```

The generic current-client pending-command serializer removes the leading
`::` and emits C2S opcode 103 command text:

```
adventurebook
```

Therefore the exact client-authoritative outbound intent is:

```
ADVENTURE_OPEN_BOOK
 -> C2S103
 -> "adventurebook"
```

This proves transport and intent identity only. It does not prove authorization,
content, state ownership or reward policy.

## Exact S2C126 global-control mode mapping

The S2C126 target-1/global-control dispatcher writes one shared integer client
presentation-mode field (`rs.Client.cY`).

Exact current mappings:

| Control token | Client mode written |
|---|---:|
| `BEGIN_ADVENTURE` | 5 |
| `BEGIN_ADVENTURE_ORB` | 6 |
| `BEGIN_ADVENTURE_BOOK` | 7 |
| `END_ADVENTURE` | 0 |

The exact bytecode sequence is direct token comparison followed by the mode
assignment. There is no inferred translation layer between these names and
values.

Nearby historical/tutorial controls use other values on the same field. That
means `cY` is a shared client presentation-mode selector, not an Adventure
domain aggregate and not safe to expose as server gameplay state.

## Native Adventure orb behavior

`rs.i.b` constructs both Adventure orb sprites and tracks whether the pointer is
inside the current layout-specific Adventure orb rectangle.

When hovered it renders the hover sprite and the Adventure Book tooltip.

The same renderer checks:

```
Client.cY == 6
```

inside its periodic flash/highlight path, proving that
`BEGIN_ADVENTURE_ORB` selects the Adventure-orb presentation mode.

The client does not derive this state from Adventure progress locally; it is
server-controlled presentation state.

## Adventure book / general mode distinction

The global control namespace distinguishes:

```
BEGIN_ADVENTURE       -> 5
BEGIN_ADVENTURE_ORB   -> 6
BEGIN_ADVENTURE_BOOK  -> 7
END_ADVENTURE         -> 0
```

These must not be collapsed into one Boolean.

The exact current client proves distinct presentation states, but this audit does
not rename them into gameplay phases such as STARTED, ACTIVE, COMPLETE, etc.
Those names would imply server semantics not established by the client.


## Exact S2C250 subtype 22 Adventure objective projection

A later exact-current v308 pass closes the native Adventure book data path much
further than the original control-token audit.

The client accepts server-published Adventure objective records through S2C250
subtype 22.

### Operation 3 - append one typed objective record

Exact wire grammar:

    subjectType          u8
    subjectId            i32_be
    textPartCount        u8
    primaryText          string_nl
    secondaryText?       string_nl   // present when textPartCount >= 2
    rewardCount          u8
    repeat rewardCount:
        rewardItemId     i32_be
        rewardAmount     i32_be
    current              u16_be
    target               u16_be
    claimed              u8          // true only when value == 1

Exact subject-type mapping:

    1 -> ITEM
    2 -> NPC_HEAD
    3 -> OBJ

The final three fields are objective presentation state:

    claimed == true        -> CLAIMED
    else current >= target -> CLAIMABLE
    else                    -> IN_PROGRESS

The current client caps the combined rendered objective lists at **25 records**.

The reward item/amount pairs are presentation payload. They do not grant or
authorize rewards by themselves.

### Operation 7 - chapter reward claim-state projection

Payload:

    state u8

Exact client presentation:

    0     -> incomplete / not claimable
    1     -> claimable / highlighted CLAIM presentation
    other -> claimed presentation

The native client toggles the chapter claim widgets and associated text according
to that state. This is presentation state only; claim authorization remains
server authority.

### Operation 8 - chapter progress fraction

Payload:

    current u16_be
    target  u16_be

The native Adventure renderer consumes this pair as the **Chapter Progress**
progress-ring fraction.

### Finalize/rebuild ordering

The client maintains two insertion-ordered objective lists:

    unclaimed
    claimed

The finalize/rebuild path renders:

    all unclaimed first
    then all claimed

That ordering is part of the presentation adapter contract because outbound row
clicks carry only generated widget identity, not a semantic objective id.

## Exact stable outbound Adventure book actions

The native controller creates ordinary M=1 buttons:

    30380  Next chapter
    30383  Previous chapter
    30390  Claim rewards

All three therefore use the ordinary exact C2S185 widget-action transport:

    C2S185
    u16_be widgetId

Widget 30393 is presentation text, not the clickable claim action.

## Exact dynamic Adventure objective-row actions

Dynamic objective rows use a fixed **15-widget stride**.

For rendered row ordinal i (0-based):

    Tips & Information  = 30400 + (15 * i)
    Teleport to Task    = 30403 + (15 * i)
    Claim reward        = 30407 + (15 * i)

The claim button is only materialized when that row is claimable.

All three actions are ordinary M=1 controls and emit only C2S185(widgetId).
There is no Adventure-specific packet payload carrying objective identity.

At the 25-row client cap the ranges are:

    Tips:      30400 .. 30760  step 15
    Teleport:  30403 .. 30763  step 15
    Claim:     30407 .. 30767  step 15

## Presentation projection identity must not become domain identity

Because outbound dynamic row clicks carry only generated widget identity, the
presentation adapter must retain the exact current projection ordering and
resolve:

    dynamic widget
     -> action kind + rendered row ordinal
     -> server-owned semantic objective identity

The raw widget arithmetic is **presentation identity only**.

A claimable row is necessarily in the unclaimed projection, so a dynamic claim
widget resolves to the current unclaimed row ordinal. That still does not make
the client row or subject id authoritative reward identity.

## Stronger authority conclusion

The exact client embeds starter/reference objective definitions, but the S2C250
subtype-22 path proves the server can clear and republish Adventure objective
records, progress and claim state.

Therefore the safe authority split is:

    client embedded definitions      = presentation/reference evidence
    server objective definitions     = authoritative domain state
    server progress/claim validation = authoritative domain state
    client generated widget id       = presentation selection only

This strengthens, rather than weakens, the negative boundary above: a modified
client must never be trusted to define Adventure rewards, completion state or
objective identity.

## Exact-current related presentation evidence

The current timed-effect catalogue also contains:

```
ADVENTURE_SCROLL
```

with client-visible description:

```
Adventure Scroll
+10 blood money from PKs
+15% PvM damage
+25% Drop rate bonus
```

That timed-effect presentation is separate from the Adventure book/orb control
mode and remains owned by the semantic timed-effect foundation (#150 / PR #151).
Do not merge the two concepts merely because they share the word "Adventure".

## Negative authority boundary

The v308 client path audited here does **not** establish:

- Adventure objective definitions;
- chapter/task assignment rules;
- completion validation;
- progress attribution;
- reward tables or reward grant mechanics;
- eligibility/admission rules;
- reset or seasonal cadence;
- persistence;
- server-side meaning of mode 5 beyond its exact presentation effect;
- server-side meaning of mode 6 beyond the proven orb presentation effect;
- server-side meaning of mode 7 beyond its exact presentation selection;
- any new C2S packet family beyond the existing C2S103 command transport.

Any authoritative Adventure gameplay implementation should therefore build on
server-owned Objective/Progression infrastructure only after independent server
authority is available.

## Architecture consequence

Keep the layers separate:

```
C2S103 "adventurebook"
        |
        v
Adventure/open-book semantic intent
        |
        v
future server-authoritative Adventure domain (only if independently proven)
        |
        v
S2C126 Adventure presentation controls
```

The S2C126 control constants belong to presentation/protocol authority.
They must not become the authoritative Adventure gameplay state model.

## Coordination

This research intentionally does not modify:

- #148 / PR #149 — S2C126 application-control implementation
- #150 / PR #151 — semantic timed effects
- #154 / PR #155 — Objective/Progression foundation
- #176 / PR #177 — Blood Fountain selectable perk catalog
- #178 / PR #179 — Daily Challenge assignment
- #182 / PR #184 — Daily Money Making tracking state
- #17 typed request routing
- packet writers / world state / persistence / content API

Permanent scope is research/evidence only.

No pinned-client 179/179 claim is made.
