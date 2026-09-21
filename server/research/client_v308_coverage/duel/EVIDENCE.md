# Evidence Package — Normal Duel Client Contract (Exact v308)

SYSTEM

Normal player dueling: duel-type selector, invite action, load-last-rules
augmentation, and incoming challenge presentation.

STATUS

STRONG-PARTIAL / CLIENT ENTRY AND CHALLENGE-PRESENTATION CONTRACT CLOSED

## AUTHORITY

Primary exact-current client:

```
SHA-256 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
build/config 308
```

Exact classes:

```
rs.n.c.E   duel-type selector
rs.n.c.D   load-last-rules augmentation
rs.n.e     widget construction
rs.Client  menu action + incoming control-message handling
rs.x.e     packet buffer
```

Authority:

`EXACT_CURRENT_CLIENT`

This is normal dueling. It is separate from the existing gambling-mode
presentation that includes a Dice Duel mode.

## DUEL-TYPE SELECTOR ROOT

Exact root:

```
25754
```

Exact selector children:

```
25755  background "popups/duel"
25756  selector/decorative sprite
25757  selector/decorative sprite
25758  selector/decorative sprite

25759  "Standard duel"
       tooltip "Select"

25760  "Whip only"
       tooltip "Select"

25761  "Whip + dds only"
       tooltip "Select"

25762  "Select a duel type.."

25763  "Invite"
       tooltip "Select"

65418  Close Window button
65419  close hover sprite
```

Root child count:

```
11
```

The selector is therefore an independent native interface, not merely a
gambling-mode alias.

## DUEL TYPE ACTION TRANSPORT

Widgets 25759/25760/25761/25763 use the same exact action-enabled text helper as
other v308 application controls.

The helper sets:

```
widget.aI = 4
widget.M  = 1
widget.Q  = "Select"
```

Exact `rs.Client` turns this form into menu action 315.

Menu action 315 emits:

```
C2S185
body = rs.x.e.d(widgetId)
```

`rs.x.e.d(int)` writes two-byte big-endian.

Exact vectors:

```
25759 Standard duel    -> 64 9F
25760 Whip only        -> 64 A0
25761 Whip + DDS only  -> 64 A1
25763 Invite           -> 64 A3
```

The client proves selection/invite intent. It does not prove what the server
must store or what restrictions apply.

## EXPLICIT CLOSE CONTROL

Widget 65418 is an action-enabled Close Window button.

It also uses the generic action-315 -> C2S185 path.

Exact body:

```
65418 -> FF 8A
```

Exact keyboard behavior is notable: the client's generic Escape close path
explicitly avoids auto-closing while the active main-interface root is:

```
25754
6575
```

among several other special roots.

Therefore the selector and classic duel-rules interface are intentionally
special-cased against the ordinary Escape close action.

That proves client UI behavior only.

## EXISTING DUEL-RULES ROOT AUGMENTATION

`rs.n.c.D.a()` does not create a new duel-rules root.

It fetches the existing client interface:

```
6575
```

then extends its child arrays by two entries.

Added widgets:

```
68439  sprite "misc/duel load"
68440  visible text "Load last rules"
       tooltip "Load last duel"
```

Positions appended to root 6575:

```
68439 -> x=394, y=304
68440 -> x=415, y=306
```

Widget 68440 uses the exact action-enabled text helper:

```
widget.aI = 4
widget.M  = 1
widget.Q  = "Load last duel"
```

and therefore reaches menu action 315 -> C2S185.

## CRITICAL HIGH-WIDGET-ID WIRE FACT

Widget id:

```
68440
```

is greater than 65535.

However C2S185 writes the id through:

```
rs.x.e.d(int)
```

which serializes only two bytes.

Exact result:

```
68440 decimal = 0x10B58
low 16 bits   = 0x0B58
wire u16      = 2904
body bytes    = 0B 58
```

Therefore:

> **Clicking native widget 68440 does NOT put integer 68440 on the C2S185 wire.**
>
> It puts unsigned-short value **2904**.

No special 68440 remap exists in the exact client action-315 path before this
write.

This is an exact transport fact and is important beyond Duel: high custom widget
ids using the ordinary C2S185 u16 writer can lose their high bits.

### LocalLab implication

A server handler that expects:

```
widgetId == 68440
```

from raw C2S185 cannot receive that value.

A semantic adapter for Load Last Rules must instead account for:

```
wire widget id = 2904
active interface/context = duel rules root 6575
semantic intent = LOAD_LAST_DUEL_RULES
```

if that behavior is implemented.

Context-aware reconstruction is an implementation strategy, not proof of the
original production server architecture.

The important authority is only that the exact client loses the high bits on
this wire path.

## INCOMING DUEL CHALLENGE PRESENTATION

Exact S2C253 server-message/control-text handling contains native challenge
suffixes.

The client parses the challenger name from the text before the first colon and
recognizes:

### Standard duel request

```
<name>:duelreq:
```

Visible client message:

```
<name> wishes to duel with you.
```

### Whip + DDS duel request

```
<name>:whipddsreq:
```

Visible client message:

```
<name> wishes to whip + dds duel with you.
```

### Whip-only duel request

```
<name>:whipduelreq:
```

Visible client message:

```
<name> wishes to whip duel with you.
```

The client applies its local ignore/suppression checks before displaying these
request messages.

This proves native challenge **presentation grammar** over S2C253.

It does not prove:

- server authorization;
- challenge lifetime;
- mutual acceptance;
- target distance;
- arena rules;
- staking;
- anti-spam/rate limits.

## INVITE / NAME INPUT BOUNDARY

The exact selector has an actionable `Invite` widget at 25763.

Exact v308 also has generic:

- S2C187 -> open name input;
- C2S60 -> name-entry response as i64 name key.

This audit has **not** yet proven that production Duel specifically connected
25763 -> S2C187 -> C2S60.

Do not silently assume that sequence merely because the generic primitives
exist.

The only exact selector fact closed here is:

```
25763 -> C2S185
```

## SERVER SEMANTICS PROVEN

Exact client proves:

- native normal duel-type selector exists;
- Standard / Whip / Whip+DDS modes;
- Invite action exists;
- exact selector widget ids;
- exact C2S185 action transport;
- classic duel-rules root 6575 receives Load Last Rules controls;
- exact high-widget-id truncation for 68440;
- native incoming request tokens for Standard/Whip/Whip+DDS presentation.

## SERVER SEMANTICS UNKNOWN

`UNKNOWN_SERVER_AUTHORITY`:

- who may duel;
- exact challenge initiation sequence after Invite;
- how selected duel type is stored;
- whether selected type applies before or after target selection;
- rule catalog;
- staking;
- item restrictions;
- spell/prayer restrictions;
- arena location;
- movement restrictions;
- countdown;
- win/loss resolution;
- disconnect behavior;
- death behavior;
- item return/escrow;
- load-last persistence format/lifetime;
- challenge timeout;
- reward behavior;
- anti-abuse/rate limits.

## LOCAL LAB MAPPING

Existing semantic foundations suitable for composition:

```
MatchSessionService
AtomicTransactionService / escrow-style substrate
WorldInstance / match lifecycle
CombatOutcome
typed widget-action request path
```

Do not reuse the gambling Dice Duel mode as a substitute for normal Duel.

## TEST VECTORS

Exact C2S185 bodies:

```
Standard duel       25759 -> 64 9F
Whip only           25760 -> 64 A0
Whip + DDS only     25761 -> 64 A1
Invite              25763 -> 64 A3
Close               65418 -> FF 8A

Load last rules:
native widget id     68440
wire low16           2904
C2S185 body          0B 58
```

Exact incoming S2C253 suffix grammar:

```
:duelreq:
:whipduelreq:
:whipddsreq:
```

## READY FOR CHAT 2

**yes, with one transport warning**

The ordinary duel selector uses existing C2S185.

For high widget 68440, typed transport must preserve the exact **wire value
2904** and must not fabricate 68440 as though it were transmitted.

Semantic reconstruction, if desired, belongs above the raw decoder with active
UI/domain context.

## READY FOR CHAT 3

**yes for client-visible Duel entry/presentation contract**

Chat 3 can build a separate semantic normal-Duel domain while keeping all
rules/staking/rewards explicitly server-policy/evidence-gated.
