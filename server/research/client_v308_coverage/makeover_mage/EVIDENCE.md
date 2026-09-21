# Evidence Package — Make-over Mage / Character Design

SYSTEM
Make-over Mage / native character designer

STATUS
PARTIAL-CLOSED-CONTRACT

## AUTHORITY

- EXACT_CURRENT_CLIENT
- EXACT_CURRENT_CACHE
- HISTORICAL_CORROBORATION

Exact-current client SHA-256:

```
854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
```

Historical wording/screenshots corroborate the normal dialogue branch only.

## CLIENT CONTRACT

Exact current cache/client proves:

- NPC definition 599 = Make-over Mage.
- dialogue root `4882`
  - head `4883`
  - name `4884`
  - line `4885`
  - Continue `4886`
- options root `2459`
  - title `2460`
  - option 1 `2461`
  - option 2 `2462`
- native designer root `3559`
- native designer preview `3650`
- Accept `3651`
- Male `3698`
- Female `3699`

Exact C2S40 dialogue Continue:

```
u16_be widgetId
```

Known exact Continue widgets:
- mouse/visible intro: `4886`
- keyboard Continue path: `4907`

Exact C2S101 character-design submit:

```
fixed 13 bytes
0      gender u8
1..7   identity kits[7] u8
8..12  colours[5] u8
```

Wire kit byte `255` normalizes to semantic `-1`.

Exact kit sets:

Male:
- Head 0..8
- Jaw 10..17
- Torso 18..25
- Arms 26..31
- Hands 33,34
- Legs 36..40
- Feet 42,43

Female:
- Head 45..54
- Jaw absent / `-1`
- Torso 56..60
- Arms 61..65
- Hands 67,68
- Legs 70..77
- Feet 79,80

Colour counts:
- hair 12
- torso 16
- legs 16
- feet 6
- skin 24

Exact presentation contracts used by the native branch:
- S2C75 NPC model/head assignment
- S2C164 chatbox root
- S2C97 main interface root
- S2C219 interface close
- packet 81 player appearance projection

## SERVER SEMANTICS PROVEN

The client/cache proves the request/presentation shape, selectable domain, and
native interface behavior.

The exact client performs arrow edits, gender changes, colour changes, and
preview rebuilding locally. The server does not receive one packet per edit;
it receives the final C2S101 submit.

A valid female appearance uses no jaw kit. In packet-81 appearance projection,
the corresponding appearance slot is zero.

## SERVER SEMANTICS UNKNOWN

- UNKNOWN_SERVER_AUTHORITY: fee/cost
- UNKNOWN_SERVER_AUTHORITY: requirements/restrictions
- UNKNOWN_SERVER_AUTHORITY: alternate dialogue branches
- UNKNOWN_SERVER_AUTHORITY: original production persistence policy
- UNKNOWN_SERVER_AUTHORITY: anti-abuse/rate-limit policy

Do not invent any of these.

## EXACT DESIGNER PRELOAD BOUNDARY

Exact `rs.Client` reset/login behavior initializes designer backing state to:

- male;
- first valid male kit in all seven categories;
- all five colour indices zero.

The client does not copy packet-81 player appearance into the private designer
state when root 3559 opens.

Untouched fresh-reset Accept therefore emits:

```
00 00 0A 12 1A 21 24 2A 00 00 00 00 00
```

Semantic value:

```
gender  = male
kits    = [0,10,18,26,33,36,42]
colours = [0,0,0,0,0]
```

The server cannot distinguish that exact payload from a user deliberately
choosing those exact defaults.

Therefore:
- persisted appearance can be rendered through packet 81;
- native 3559 controls are **not proven to preload persisted appearance**;
- no heuristic suppression or fake preload protocol should be invented.

## HISTORICAL CORROBORATION

Screenshot-observed normal wording:

- `How may I help you?`
- `I'd like to change my look.`
- `Nevermind.`

This supports presentation/wording only. It does not prove fee, restrictions, or
persistence rules.

## FILES / METHODS

Exact-client archaeology:
- `rs.Client` content/action types 300..326
- `rs.Client.I()` reset/login designer initialization
- private designer initializer `bs()`
- exact packet writer for C2S40
- exact content-type 326 writer for C2S101
- player appearance parser `rs.a.k`

Recovered implementation/evidence source:
- draft PR #372, branch `feature/makeover-mage-character-design-chat4`
- this research campaign intentionally does not depend on merging that branch

## TEST VECTORS

Female high-bound valid submit:

```
01 2D FF 38 3D 43 46 4F 0B 0F 0F 05 17
```

Semantic:
- gender female
- kits `[45,-1,56,61,67,70,79]`
- colours `[11,15,15,5,23]`

Fresh-client untouched reset/default submit:

```
00 00 0A 12 1A 21 24 2A 00 00 00 00 00
```

S2C75 body for NPC 599 -> widget 4883:

```
D7 02 93 13
```

## CONFLICT / DEPENDENCY

Issue #17 typed-request transport migration owns runtime request plumbing.
Chat 4 does not prescribe pending-slot vs typed-FIFO implementation.

The final runtime implementation should preserve the exact C2S40/C2S101 contract
without exposing raw opcode/widget identity through the public content API.

## READY FOR CHAT 2

**yes, as transport evidence only**

Chat 2 may use the exact packet schemas/provenance when wiring internal typed
requests. Chat 4 does not define the runtime architecture.

## READY FOR CHAT 3

**yes, for the screenshot-observed normal branch with explicit unknowns**

Chat 3 may implement gameplay/domain handling for the proven normal branch, but
must preserve all `UNKNOWN_SERVER_AUTHORITY` fields instead of inventing
production mechanics.
