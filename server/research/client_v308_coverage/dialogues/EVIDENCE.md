# Evidence Package — Dialogue / Chatbox State Machines

SYSTEM
Standard SpawnPK dialogue/chatbox input routing and exact-current client-side
hotkey behavior.

STATUS
STRONG-PARTIAL

## AUTHORITY

- EXACT_CURRENT_CLIENT
- EXISTING_EXACT_CURRENT_MAKEOVER_INSTANCE

Exact-current client:

```
SHA-256 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
revision 308
```

## STANDARD CLIENT ROOT FAMILIES

Exact `rs.Client` contains two explicit chatbox/root sets.

Option/selection hotkey roots:

```
jy = [2459, 2469, 2480, 2492, 14170]
```

Continue hotkey roots:

```
jz = [4882, 4887, 4893, 4900, 30700]
```

These are client-local root-family facts. They do not by themselves define
original server dialogue-node semantics.

## MOUSE CONTINUE — EXACT C2S40

The exact menu builder assigns menu action `679` to widgets whose action type
`M == 6`.

The exact action-679 dispatcher writes:

```
C2S40
widgetId -> rs.x.e.d(int) -> u16_be
```

So the exact generic mouse Continue route is:

```
action-6 widget
 -> menu action 679
 -> C2S40(widgetId)
```

The already recovered Make-over flow gives one concrete exact instance:

```
root 4882
Continue widget 4886
 -> C2S40(4886)
```

## MOUSE OPTION / ORDINARY BUTTON — EXACT C2S185

The exact menu builder assigns menu action `315` to ordinary action-1 widgets
(`M == 1`) after local controller gating.

The exact action-315 dispatcher writes:

```
C2S185
widgetId -> rs.x.e.d(int) -> u16_be
```

So:

```
action-1 widget
 -> menu action 315
 -> C2S185(widgetId)
```

C2S185 is a generic widget-action transport. It must not be globally relabeled
as “dialogue option”; dialogue semantics come from the concrete widget/root
context.

The exact Make-over flow already identifies option widgets `2461` and
`2462` under root `2459`.

## KEYBOARD OPTION — EXACT C2S103 COMMAND

When the currently open chatbox/root field `gp` equals one of:

```
2459
2469
2480
2492
14170
```

exact `rs.Client.keyPressed(...)` accepts:

- top-row `1..5` — key codes `49..53`;
- numpad `1..5` — key codes `97..101`.

The client constructs:

```
::dialogueoption N
```

and its normal command drain emits:

```
C2S103
"dialogueoption N\n"
```

Therefore keyboard dialogue-option selection is **not** a synthesized C2S185
widget click.

This distinction matters when Chat 2/3 normalize input: mouse and keyboard can
carry the same user intent through different exact wire families.

## KEYBOARD CONTINUE — EXACT C2S40(4907)

When `gp` equals one of:

```
4882
4887
4893
4900
30700
```

and Space is pressed, exact v308 directly sends:

```
C2S40
widgetId = 4907
```

So there are two exact Continue entry forms:

```
mouse:
clicked action-6 widget -> C2S40(clickedWidgetId)

keyboard:
Space on standard Continue root -> C2S40(4907)
```

The client proves the wire behavior. It does not prove that the original server
required distinct gameplay semantics for the literal `4907` value.

## EXACT OPTION-CONTROLLER SUPPORT

Exact `rs.n.c.A` tracks the roots:

```
6179
2459
2469
2480
2492
```

and appends a client-side `Close window` / `Cancel` control to them.

The same controller explicitly manages `14170` and the visible text:

```
Please confirm your choice.
```

This confirms that the standard option roots and `14170` belong to the
client's choice/confirmation presentation family. The precise production
business meaning of any one root remains contextual.

## DIALOGUE MODEL / HEAD PRESENTATION

Exact v308 also exposes separate native model-on-widget presentation channels.

### S2C75 — NPC model/head on widget

The exact inbound handler reads:

```
npcDefinitionId = U()
widgetId        = U()
```

and switches the target widget to NPC-model presentation.

Existing exact Make-over evidence provides a concrete instance:

```
NPC 599 -> widget 4883
```

### S2C185 — local-player model/head on widget

The exact inbound handler reads one widget id with `U()`, switches that target
to player-model presentation, and builds its model identity from the local
player's current appearance/morph state.

This **inbound S2C185** is directionally distinct from **outbound C2S185**
generic widget actions even though both use numeric opcode 185.

### S2C200 — model animation + dialogue camera special cases

Exact S2C200 reads a widget id and signed animation id, then updates model
animation state.

For these exact widget ids:

```
4883
4888
4894
4901
969
974
980
987
```

the client applies dialogue-style model camera defaults:

```
zoom/presentation = 2000 / 100 / 1900
```

The first four pair exactly with the standard Continue roots:

```
4882 -> 4883
4887 -> 4888
4893 -> 4894
4900 -> 4901
```

The second four are an exact parallel dialogue-model widget family. Their
semantic root names remain unassigned here until the cache/interface
relationship is separately proven.

This gives Chat 3 a protocol-independent presentation shape such as:

```
showNpcDialogueModel(...)
showLocalPlayerDialogueModel(...)
animateDialogueModel(...)
```

without exposing raw widget ids.

## SEMANTIC NORMALIZATION BOUNDARY

A protocol-independent dialogue/content layer should be able to receive semantic
intent such as:

```
DialogueContinue
DialogueOption(index)
DialogueClose
```

without exposing raw opcodes or widget IDs.

Transport/presentation adapters can normalize:

```
C2S40(clickedContinueWidget)
C2S40(4907 from Space)
C2S103("dialogueoption N")
C2S185(proven option widget)
```

into the same domain-level dialogue state machine where evidence says they are
equivalent.

Do not turn raw `40`, `103`, `185`, `4907`, or individual option-widget
IDs into public content API concepts.

## SERVER SEMANTICS UNKNOWN

- UNKNOWN_SERVER_AUTHORITY: original dialogue node/state ownership.
- UNKNOWN_SERVER_AUTHORITY: option result/effect for arbitrary production
  dialogues.
- UNKNOWN_SERVER_AUTHORITY: fees, permissions, rewards, cooldowns and alternate
  branches.
- UNKNOWN_SERVER_AUTHORITY: whether the original server interpreted the keyboard
  Continue's literal `4907` specially or only as a Continue signal.
- UNKNOWN_SERVER_AUTHORITY: exact semantic root names/relationships for the parallel `969/974/980/987` dialogue-model family until cache/interface proof is added.
- UNKNOWN_SERVER_AUTHORITY: remaining item/statement/close presentation families not yet normalized in this package.

## FILES / METHODS

Exact v308:
- `rs.Client` menu construction: `M==1 -> 315`, `M==6 -> 679`
- `rs.Client.a(int,int,int,int,int,String)`: action dispatch
- `rs.Client.keyPressed(KeyEvent)`: dialogue hotkeys
- `rs.Client` outbound command drain: `ap -> C2S103`
- `rs.x.e.d(int)`: BE16 writer
- `rs.x.e.a(String)`: string + newline writer
- `rs.n.c.A`: option/confirmation root controller
- inbound S2C75 handler: NPC model-on-widget
- inbound S2C185 handler: local-player model-on-widget
- inbound S2C200 handler: model animation + dialogue camera special cases

Raw evidence:
- `server/research/client_v308_coverage/dialogues/evidence/standard_dialogue_transport_v308.txt`

Related exact-current concrete instance:
- `server/research/client_v308_coverage/makeover_mage/EVIDENCE.md`

## READY FOR CHAT 2

**yes — core transport normalization**

Chat 2 can normalize C2S40/C2S103/C2S185 into internal typed request forms while
keeping opcode/widget identity internal.

## READY FOR CHAT 3

**yes — standard Continue/option state-machine input contract**

Chat 3 can define semantic dialogue state independently of transport. Remaining
dialogue root/presentation families should continue to be added by Chat 4 as
evidence, not guessed from generic 317 conventions.
