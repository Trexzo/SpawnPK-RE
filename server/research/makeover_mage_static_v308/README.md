# Make-over Mage / Character Design — Exact v308 Static Authority

Research date: 2026-09-21

This note records the exact-current client/cache evidence used by the LocalLab
Make-over Mage recovery. It deliberately separates exact client/cache facts from
historical server-side wording.

## Authority boundary

Exact-current authority:
- client SHA-256: `854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6`
- NPC definition 599: `Make-over Mage`
- current Home spawn corpus already places NPC 599 at 3082,3506
- interface/widget roots and content/action types
- identity-kit categories and selectable sets from current `idk.dat`
- C2S40 and C2S101 wire schemas
- S2C75/97/164/219 client decoders

Historical SpawnPK screenshot evidence:
- `How may I help you?`
- `I'd like to change my look.`
- `Nevermind.`
- the normal screenshot-observed branch from NPC dialogue to the designer

Unknown original-server authority remains unknown:
- fees
- conditions
- alternate dialogue branches
- any production-specific persistence policy not visible in client/cache

LocalLab therefore implements only the screenshot-observed normal branch and does
not invent unknown conditions.

## Native interface roots

NPC one-line chatbox:

```
root 4882
  4883 NPC head/model
  4884 NPC name
  4885 dialogue line
  4886 Continue
```

Two-option chatbox:

```
root 2459
  2460 title
  2461 option 1
  2462 option 2
```

Character designer root:

```
3559
```

## Character designer widget map

The values 300..326 in client bytecode are widget content/action types, not widget IDs.

| Part | Previous | Next | client state |
|---|---:|---:|---|
| Head | 3659 | 3666 | mu[0] |
| Jaw | 3660 | 3667 | mu[1] |
| Torso | 3661 | 3668 | mu[2] |
| Arms | 3662 | 3669 | mu[3] |
| Hands | 3663 | 3670 | mu[4] |
| Legs | 3664 | 3671 | mu[5] |
| Feet | 3665 | 3672 | mu[6] |
| Hair colour | 3657 | 3679 | lB[0] |
| Torso colour | 3681 | 3685 | lB[1] |
| Legs colour | 3680 | 3687 | lB[2] |
| Feet colour | 3682 | 3688 | lB[3] |
| Skin colour | 3683 | 3689 | lB[4] |

Other widgets:
- preview: 3650
- Accept: 3651
- Male: 3698
- Female: 3699

All arrows, gender toggles and preview rebuilding are client-local. The server does
not need a packet for each edit.

## Exact selectable identity-kit sets

Male:
- Head: 0..8
- Jaw: 10..17
- Torso: 18..25
- Arms: 26..31
- Hands: 33,34
- Legs: 36..40
- Feet: 42,43

Male defaults:
`[0,10,18,26,33,36,42]`

Female:
- Head: 45..54
- Jaw: no selectable kit
- Torso: 56..60
- Arms: 61..65
- Hands: 67,68
- Legs: 70..77
- Feet: 79,80

Female defaults:
`[45,-1,56,61,67,70,79]`

The client transmits semantic kit `-1` as byte `255`; LocalLab normalizes it back
to `-1` before validation/persistence.

## Exact colour bounds

- Hair: 12 choices, 0..11
- Torso: 16 choices, 0..15
- Legs: 16 choices, 0..15
- Feet: 6 choices, 0..5
- Skin: 24 choices, 0..23

## C2S dialogue / design contracts

C2S40 dialogue Continue:
```
u16_be widgetId
```

The visible NPC one-line dialogue uses widget `4886`. The exact client's
keyboard-continue path can emit `4907`; LocalLab accepts either only while the
Make-over intro stage is active.

C2S101 character-design submit:
```
fixed length 13
byte 0: gender (0 male, 1 female)
bytes 1..7: mu[0..6]
bytes 8..12: lB[0..4]
```

Widget content/action type 326 writes C2S101 and returns immediately. It does not
also emit C2S185 for Accept.

## Exact presentation contracts used

S2C75 NPC model assignment reads two `U()` shorts:
- NPC definition id
- widget id

`U()` is little-endian with the low wire byte encoded +128.

For NPC 599 and widget 4883, exact body:
```
D7 02 93 13
```

S2C164 opens a chatbox root using `u16_le`.

S2C97 opens the main interface root using `u16_be`.

S2C219 closes interfaces.

## LocalLab integration

Current LocalLab already owns exact packet-81 appearance serialization and remote
player appearance projection. This slice therefore stores:
- gender
- seven semantic identity-kit IDs
- five colour indices

and feeds them into the existing appearance block instead of creating a second
appearance protocol.

Existing equipment visual policy remains authoritative over base kits:
- full helms may suppress base head/jaw kits
- full-body chest items may suppress base arms
- equipped item models still replace their normal appearance positions

Persistence uses additive schema keys:
```
appearance.gender
appearance.kit.0 .. appearance.kit.6
appearance.colour.0 .. appearance.colour.4
```

Older snapshots have no such keys and therefore normalize to the exact male defaults.


## Designer-state initialization boundary

A static whole-class reference audit of exact `rs.Client` shows that the private
designer state is not populated from packet-81 player appearance when interface
`3559` opens:

- `mu[0..6]` is written by the designer-default initializer and the 300..313
  arrow handlers, then read by preview/content-type 326 submit.
- `lB[0..4]` is zeroed by client reset, written by the 314..323 colour arrow
  handlers, then read by preview/content-type 326 submit.
- `ml` is set by client reset and toggled by content types 324/325.
- no additional assignment path copies local-player `br[]`, `aV[]`, or
  packet-81 gender `aY` into `mu[]`, `lB[]`, or `ml`.

Therefore a fresh exact client does not have a server packet that seeds the
designer controls from a persisted current appearance merely by opening root
`3559`. Packet-81 still renders the persisted appearance correctly before the
designer opens; this is specifically a designer-control initialization boundary.

LocalLab does not patch or mutate the production client to invent such a channel.
The recovered server flow opens the native designer and accepts the exact C2S101
result the client submits.
