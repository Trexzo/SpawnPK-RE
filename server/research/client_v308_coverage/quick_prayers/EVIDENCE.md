# Evidence Package — Quick Prayers / Quick Curses (Exact v308)

SYSTEM

Native quick-prayer / quick-curse selection roots, quick-toggle actions, exact
selection widget/config mapping, confirmation action, and client-side active
state controls.

STATUS

STRONG-PARTIAL / STATIC SELECTION + INPUT CONTRACT CLOSED

## AUTHORITY

Primary exact-current client:

```
SHA-256 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
build/config 308
```

Exact classes:

```
rs.n.c.aJ   quick prayer / curse roots
rs.n.c.aH   normal-prayer interface authority
rs.n.c.aG   curse interface authority
rs.n.e      widget construction / conditional scripts
rs.Client   quick-orb menu + C2S action dispatch + S2C126 controls
rs.x.e      packet buffer
```

Current LocalLab exact prayer authority cross-reference:

```
PrayerDefinitionRepository
```

Authority:

`EXACT_CURRENT_CLIENT`

## ROOTS

Exact selection roots:

```
20000  Select your quick prayers
22000  Select your quick curses
```

Both are created through `rs.n.e.j(int)`, which creates/registers a normal
interface root in the global widget table.

They are compatible with ordinary S2C97 main-interface opening.

Exact open bodies:

```
20000 -> 4E 20
22000 -> 55 F0
```

This proves client presentation capability. The exact production server event
that chooses/open either root remains server-side authority.

## SHARED QUICK-SELECTION WIDGET FAMILY

Both roots use the same selection-overlay widget id family beginning at:

```
17202
```

The selection widgets are built through the exact helper:

```
a(
  widgetId,
  17200,
  spriteIndexPrimary=2,
  spriteIndexAlternate=1,
  "/prayer/quick/SPRITE",
  width=14,
  height=15,
  tooltip="Select",
  ...,
  M=1,
  configIndex
)
```

The helper creates an action widget with:

```
aI = 5
M  = 1
Q  = "Select"
```

and installs one conditional interface script:

```
comparison type / aq[0] = 1
required value / I[0]   = 0
script X[0]             = [5, configIndex, 0]
```

Therefore each selection overlay has:

1. an ordinary clickable C2S185 action path; and
2. visual state tied to a specific client config/varp-style index.

The exact client does not locally toggle that config in the generic action-315
path. It sends the widget action. Config visual state can be driven through the
normal S2C36 / S2C87 config-update families.

This is a client contract, not proof of original server persistence format.

## QUICK PRAYERS — ATTACHED SELECTIONS

Root:

```
20000
```

Static child count:

```
64
```

The root visibly attaches **29** selection overlays:

```
17202..17230
```

mapped to config indices:

```
630..658
```

The static builder also creates definition `17231 / config 659`, but widget
17231 is **not attached as a child of root 20000** by this layout.

Do not count it as a proven visible quick-prayer selection.

### Exact visible normal-prayer crosswalk

| Selection widget | Config | Prayer | Activation widget | Display child |
|---:|---:|---|---:|---:|
| 17202 | 630 | Thick Skin | 5609 | 5632 |
| 17203 | 631 | Burst of Strength | 5610 | 5633 |
| 17204 | 632 | Charity of Thought | 5611 | 5634 |
| 17205 | 633 | Sharp Eye | 18000 | 18001 |
| 17206 | 634 | Mystic Will | 18002 | 18003 |
| 17207 | 635 | Rock Skin | 5612 | 5635 |
| 17208 | 636 | Superhuman Strength | 5613 | 5636 |
| 17209 | 637 | Improved Reflexes | 5614 | 5637 |
| 17210 | 638 | Rapid Restore | 5615 | 5638 |
| 17211 | 639 | Rapid Heal | 5616 | 5639 |
| 17212 | 640 | Protect Item | 5617 | 5640 |
| 17213 | 641 | Hawk Eye | 18004 | 18005 |
| 17214 | 642 | Mystic Lore | 18006 | 18007 |
| 17215 | 643 | Steel Skin | 5618 | 5641 |
| 17216 | 644 | Ultimate Strength | 5619 | 5642 |
| 17217 | 645 | Incredible Reflexes | 5620 | 5643 |
| 17218 | 646 | Protect from Magic | 5621 | 5644 |
| 17219 | 647 | Protect from Missiles | 5622 | 686 |
| 17220 | 648 | Protect from Melee | 5623 | 5645 |
| 17221 | 649 | Eagle Eye | 18008 | 18009 |
| 17222 | 650 | Mystic Might | 18010 | 18011 |
| 17223 | 651 | Retribution | 683 | 5649 |
| 17224 | 652 | Redemption | 684 | 5647 |
| 17225 | 653 | Smite | 685 | 5648 |
| 17226 | 654 | Preserve | 18553 | 18554 |
| 17227 | 655 | Chivalry | 18012 | 18013 |
| 17228 | 656 | Piety | 18014 | 18015 |
| 17229 | 657 | Rigour | 18045 | 18046 |
| 17230 | 658 | Augury | 18047 | 18048 |

This crosswalk follows the exact static quick layout plus the exact current
normal-prayer widget authority.

## QUICK CURSES — ATTACHED SELECTIONS

Root:

```
22000
```

Static child count:

```
46
```

The root visibly attaches **20** selection overlays:

```
17202..17221
```

mapped to config indices:

```
630..649
```

The curses builder constructs selection definitions through:

```
17228 / config 656
```

but the static root attaches only 17202..17221.

The unattached definitions 17222..17228 must not be treated as proven visible
curse selections.

### Exact visible curse crosswalk

| Selection widget | Config | Curse | Activation widget | Display child |
|---:|---:|---|---:|---:|
| 17202 | 630 | Protect Item | 22503 | 22504 |
| 17203 | 631 | Sap Warrior | 22505 | 22506 |
| 17204 | 632 | Sap Ranger | 22507 | 22508 |
| 17205 | 633 | Sap Mage | 22509 | 22510 |
| 17206 | 634 | Sap Spirit | 22511 | 22512 |
| 17207 | 635 | Berserker | 22513 | 22514 |
| 17208 | 636 | Deflect Summoning | 22515 | 22516 |
| 17209 | 637 | Deflect Magic | 22517 | 22518 |
| 17210 | 638 | Deflect Missiles | 22519 | 22520 |
| 17211 | 639 | Deflect Melee | 22521 | 22522 |
| 17212 | 640 | Leech Attack | 22523 | 22524 |
| 17213 | 641 | Leech Ranged | 22525 | 22526 |
| 17214 | 642 | Leech Magic | 22527 | 22528 |
| 17215 | 643 | Leech Defence | 22529 | 22530 |
| 17216 | 644 | Leech Strength | 22531 | 22532 |
| 17217 | 645 | Leech Energy | 22533 | 22534 |
| 17218 | 646 | Leech Special Attack | 22535 | 22536 |
| 17219 | 647 | Wrath | 22537 | 22538 |
| 17220 | 648 | Soul Split | 22539 | 22540 |
| 17221 | 649 | Turmoil | 22541 | 22542 |

The separate exact-client curse authority also contains ranged/magic Turmoil
variants, but they are not attached to this static quick-curse selector root.

## SELECTION ACTION TRANSPORT

For the shared visible selection overlays, `rs.Client` enters ordinary menu
action 315.

The widget has `J=0`, so the generic client pre-action handler does not consume
the click.

Action 315 then emits:

```
C2S185
body = rs.x.e.d(widgetId)
```

Exact `rs.x.e.d(int)` is u16 big-endian.

Examples:

```
17202 -> 43 32
17221 -> 43 45
17230 -> 43 4E
```

Therefore normal-prayer and curse selections for widgets 17202..17221 are
**wire-identical**.

The semantic meaning depends on the active root/book context:

```
root 20000 + widget 17202 -> quick NORMAL-prayer selection
root 22000 + widget 17202 -> quick CURSE selection
```

Raw C2S185 transport alone cannot distinguish those meanings.

This is exact evidence that semantic widget routing sometimes requires active
interface/domain context even when no high-id truncation is involved.

## CONFIRM SELECTION

Exact shared button:

```
17241
```

tooltip:

```
Confirm Selection
```

hover:

```
17242
```

The button is action-enabled:

```
aI = 5
M  = 1
```

and uses the same ordinary action315 -> C2S185 path.

Exact body:

```
17241 -> 43 59
```

The same confirm widget is referenced by both root 20000 and root 22000.

Therefore:

> `C2S185(17241)` is also context-dependent.

The server/domain layer must know whether the player is confirming the normal
quick-prayer or quick-curse selector if it models those as separate semantic
sets.

## QUICK-ORB ACTIONS

Exact client quick-prayer orb/context menu exposes:

```
"Turn quick prayers on"
"Turn prayers off"
"Select quick prayers"
```

The menu action ids are local client action types 1500 and 1506.

### Toggle on/off

Action 1500 toggles local client field `cp` immediately, then emits C2S185:

When toggled ON:

```
widget 5000
body   13 88
```

When toggled OFF:

```
widget 4999
body   13 87
```

The client then refreshes the orb presentation.

### Select quick prayers

Action 1506 emits:

```
C2S185
widget 5001
body   13 89
```

The exact client does not itself open root 20000/22000 in that action path.

Therefore the server/application side must decide what selection UI/state to
present after the request.

The client evidence alone does not prove the original production selection-root
decision policy.

## SERVER -> CLIENT ACTIVE-STATE CONTROLS

Exact S2C126 global-control parsing recognizes payloads:

```
QUICK_PRAYERS_ON
QUICK_PRAYERS_OFF
DISABLE_QUICK_PRAYERS
:quickdisable:
```

These can clear/set client field `cp` and refresh the quick-prayer orb.

Exact behavior:

```
QUICK_PRAYERS_ON  -> cp = true
QUICK_PRAYERS_OFF -> cp = false
DISABLE_QUICK_PRAYERS -> cp = false
:quickdisable:        -> cp = false
```

The exact S2C126 handler also recognizes:

```
:quicks:<value>
```

and sets a separate client boolean according to whether the suffix equals
`on`.

The semantic role of that second boolean is not promoted here beyond the exact
control-token behavior.

## CONFIG PRESENTATION CHANNEL

Selection widgets reference client config indices:

```
normal visible set: 630..658
curse visible set:  630..649
```

The exact client supports generic S2C36 and S2C87 config updates.

Current LocalLab already has exact config publisher primitives for both packet
families.

This establishes a clean presentation route for selected/unselected overlay
state.

It does **not** prove:

- which value means selected in production beyond the widget's exact comparison
  script;
- save timing;
- whether every click was round-tripped before visual change;
- persistence format.

The widget script itself compares the referenced config against exact required
value 0 using compare type 1 and switches between the two quick-prayer sprites.

## CURRENT LOCAL LAB GAP

Read-only current-main audit found no semantic handling for:

```
4999
5000
5001
17202..17230
17241
20000
22000
```

inside current:

- `PrayerState`;
- `LocalPrayerMagicCommandHandler`;
- `LocalGameplayWidgetHandler`.

So ordinary prayer activation/book support exists, but saved quick-selection
semantics are not currently modeled there.

This is a concrete domain-composition gap, not missing generic prayer
infrastructure.

## SERVER SEMANTICS PROVEN

Exact client proves:

- Quick Prayer root 20000;
- Quick Curse root 22000;
- 29 visible normal-prayer selections;
- 20 visible curse selections;
- exact shared selection widget ids;
- exact config indices driving selection visuals;
- exact underlying prayer/curse display crosswalk;
- exact C2S185 selection transport;
- exact shared Confirm action 17241;
- exact orb toggle actions 4999/5000;
- exact selector request 5001;
- exact S2C126 active-state control tokens;
- selection and confirm wire identity must be interpreted with active root/book
  context.

## SERVER SEMANTICS UNKNOWN

`UNKNOWN_SERVER_AUTHORITY`:

- original persistence schema;
- whether normal and curse quick sets are stored simultaneously or one-at-a-time;
- whether changing prayer book preserves both sets;
- selection validation rules;
- level checks at selection time vs activation time;
- conflict resolution for mutually exclusive prayers;
- exact activation order;
- prayer drain interaction when toggling a quick set;
- whether failed activations partially apply;
- save-on-click vs save-on-confirm behavior;
- logout/death reset policy;
- production use of the unattached selection definitions;
- exact reason/meaning of the separate `:quicks:` boolean.

## LOCAL LAB MAPPING

Chat 3 can now add semantic state above existing `PrayerState`, for example:

```
QuickPrayerSelection
  book
  selected semantic prayers/curses
```

Raw widgets/configs should remain inside a presentation adapter.

Recommended boundary:

```
C2S185 + active root
 -> semantic QUICK_SELECTION_TOGGLE(prayer)
 -> semantic state
 -> S2C36/87 config projection

C2S185(17241) + active root
 -> semantic QUICK_SELECTION_CONFIRM

C2S185(4999/5000)
 -> semantic QUICK_SET_ACTIVE false/true

C2S185(5001)
 -> semantic OPEN_QUICK_SELECTION
```

This is an architecture recommendation, not a claim about production server
class structure.

## TEST VECTORS

```
Quick orb:
4999 -> 13 87  (off)
5000 -> 13 88  (on)
5001 -> 13 89  (select)

Selection:
17202 -> 43 32
17221 -> 43 45
17230 -> 43 4E

Confirm:
17241 -> 43 59
```

All are exact C2S185 u16-be widget bodies before ISAAC opcode framing.

## READY FOR CHAT 2

**yes, with semantic-context warning**

No new packet type is required.

The typed widget-action path must preserve raw widget id exactly and allow
higher semantic routing to consult active interface/book context.

## READY FOR CHAT 3

**yes**

Chat 3 has enough exact client authority to implement saved quick-prayer and
quick-curse semantic state on top of existing `PrayerState`, without guessing
widget transport.
