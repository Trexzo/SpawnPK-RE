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
