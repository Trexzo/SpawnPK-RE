# Evidence Package — Dialogue / Chatbox State Machines

SYSTEM
Standard SpawnPK dialogue/chatbox input routing and exact-current client-side
hotkey behavior.

STATUS
STRONG-CLOSED-STANDARD-FAMILIES / CONTENT-SPECIFIC-SEMANTICS-PARTIAL

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


## EXACT CLOSE / CANCEL CONTROL

Exact `rs.n.c.A` augments the option roots:

~~~
6179
2459
2469
2480
2492
~~~

with one shared actionable widget:

~~~
widget id: 54195
text:      <img=25> Close window
tooltip:   Cancel
action M:  1
~~~

The exact widget builder `rs.n.e.a(int,String,String,K[],...)` sets `M=1` for this control.

The exact menu/action pipeline maps action-1 widgets to:

~~~
menu action 315
 -> C2S185(widgetId)
~~~

Therefore the standard close/cancel control is server-visible as:

~~~
C2S185(54195)
~~~

subject to the client's normal local widget-action gating.

This closes the transport side of semantic `DialogueClose` / `DialogueCancel` for the augmented option family.

It does **not** prove what every production dialogue did after cancel; branch/state effects remain server authority.

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

The second four are exact model children of cache-backed roots `968/973/979/986`. The cache relationship is now proven below; the roots form a named 1–4-line model-dialogue family structurally parallel to the NPC family.

This gives Chat 3 a protocol-independent presentation shape such as:

```
showNpcDialogueModel(...)
showLocalPlayerDialogueModel(...)
animateDialogueModel(...)
```

without exposing raw widget ids.


## EXACT INTERFACE-CACHE DIALOGUE FAMILIES

The exact current cache-backed `interface` archive was decoded through the v308 client's own widget loader (`rs.n.e.a(...)`). This closes several families that were previously only inferred from dispatcher constants.

### Parallel named model-dialogue family

The previously listed ids `969/974/980/987` are **model child widgets, not roots**.

Exact roots and children are:

~~~
root 968  -> model 969, Name 970, Line1 971, Continue 972
root 973  -> model 974, Name 975, Line1 976, Line2 977, Continue 978
root 979  -> model 980, Name 981, Line1 982, Line2 983, Line3 984, Continue 985
root 986  -> model 987, Name 988, Line1 989, Line2 990, Line3 991, Line4 992, Continue 993
~~~

Each model child is type-6 and each Continue child is an action-6 widget with exact text/tooltip:

~~~
Click here to continue
Continue
~~~

This family is structurally parallel to the NPC dialogue family and is compatible with the exact inbound local-player model channel S2C185. However, the cache record itself does not contain a literal semantic label such as `PLAYER_DIALOGUE`; keep that higher-level name as a semantic adapter label rather than pretending it is a cache string.

### Named model-dialogue family used by exact NPC presentation

The same exact cache decode confirms:

~~~
root 4882 -> model 4883, Name 4884, Line1 4885, Continue 4886
root 4887 -> model 4888, Name 4889, Line1 4890, Line2 4891, Continue 4892
root 4893 -> model 4894, Name 4895, Line1 4896, Line2 4897, Line3 4898, Continue 4899
root 4900 -> model 4901, Name 4902, Line1 4903, Line2 4904, Line3 4905, Line4 4906, Continue 4907
~~~

These model children are the same exact widgets used by S2C75 NPC model/head presentation in the Make-over NPC-599 instance.

Importantly, the exact client does **not** intrinsically bind these roots to NPC models. S2C246 can retarget a type-6 widget to an item model, while S2C75 retargets it to an NPC model. Therefore these roots are best modeled as **named model-dialogue templates** whose model source is supplied by the presentation adapter, not as NPC-only domain roots.

That distinction prevents item dialogue from needing a separate packet-specific domain model.

### Statement dialogue family

The exact cache also closes the model-free statement roots:

~~~
root 356 -> Line1 357, Continue 358
root 359 -> Line1 360, Line2 361, Continue 362
root 363 -> Line1 364, Line2 365, Line3 366, Continue 367
root 368 -> Line1 369, Line2 370, Line3 371, Line4 372, Continue 373
root 374 -> Line1 375, Line2 376, Line3 377, Line4 378, Line5 379, Continue 380
~~~

No model or Name child is present in these roots. This is exact interface-cache structure and cleanly supports a semantic StatementDialogue presentation family.

### Runtime-composed exact item-backed dialogue root 30700

Exact v308 class `rs.n.c.a.a` statically owns:

~~~
root = 30700
~~~

and composes that root from existing exact cache widgets:

~~~
14171 -> 1x1 item widget/container
6181  -> Line1
6182  -> Line2
6183  -> Line3
6184  -> Line4
4892  -> action-6 Continue
~~~

The exact runtime setter:

~~~
rs.n.c.a.a.a(itemId, amountOrCount, boolean)
~~~

calls the shared item-widget helper on widget `14171`.

That helper resolves:

~~~
rs.d.k.f(itemId)
~~~

and copies exact item-definition presentation state into the widget, including
item identity plus model rotation/zoom fields.

Therefore `30700` is not merely a generic model-bearing dialogue inferred from
legacy conventions. It is an **exact runtime-composed item-backed dialogue
presentation root** in v308.

This also explains why Space treats `30700` as a Continue root while sending
the canonical literal `C2S40(4907)`: the item root reuses the standard
Continue widget family rather than introducing a bespoke request packet.

### Root 14170 — cache default versus runtime repurposing

The exact matched interface cache gives root `14170` a concrete default
identity:

~~~
14171 -> 1x1 item widget
14172 -> static model widget
14173 -> static model widget
14174 -> "Are you sure you want to destroy this object?"
14175 -> "Yes."  / tooltip "Destroy Object"
14176 -> "No."   / tooltip "Cancel"
14184 -> "Name"
~~~

So the cache-default form is specifically a **destroy-object confirmation**.

Exact `rs.n.c.A` subsequently mutates the same root at runtime and rewrites
`14174` to:

~~~
Please confirm your choice.
~~~

The exact client also includes `14170` in the keyboard option-root array.

The correct authority statement is therefore:

> `14170` is an option-capable confirmation shell with a destroy-object
> cache default that is repurposed by exact runtime UI code.

Do not describe it as merely a generic fifth option root, and do not treat
widget `14176`'s `Cancel` as the same control as the shared augmented-option
close widget `54195`.

### Additional model-bearing Continue families

The cache contains several other model-bearing/no-name Continue roots (for example `306/310/315/321` and later quest/content-specific roots). Their exact structure is recoverable, but this package deliberately does **not** relabel them as item dialogue until their model-update/use path is directly proven.

This avoids importing generic 317 naming conventions as SpawnPK authority.


## SEMANTIC NORMALIZATION BOUNDARY

A protocol-independent dialogue/content layer should be able to receive semantic
intent such as:

```
DialogueContinue
DialogueOption(index)
DialogueClose
DialogueCancel
```

without exposing raw opcodes or widget IDs.

Transport/presentation adapters can normalize:

```
C2S40(clickedContinueWidget)
C2S40(4907 from Space)
C2S103("dialogueoption N")
C2S185(proven option widget)
C2S185(54195 close/cancel)
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
- UNKNOWN_SERVER_AUTHORITY: historical/business naming policy for the cache-backed `968/973/979/986` named model-dialogue family beyond its exact structure and local-player-model compatibility.
- UNKNOWN_SERVER_AUTHORITY: exact semantic identity of remaining model-bearing/no-name dialogue families until their update/use paths are directly proven; root `30700` is no longer in this unknown bucket because its item-backed runtime composition is exact.
- UNKNOWN_SERVER_AUTHORITY: business/state transition after exact close/cancel request `C2S185(54195)`.

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
- `server/research/client_v308_coverage/dialogues/evidence/interface_cache_item_runtime_v308.txt`

Related exact-current concrete instance:
- `server/research/client_v308_coverage/makeover_mage/EVIDENCE.md`

## READY FOR CHAT 2

**yes — core transport normalization**

Chat 2 can normalize C2S40/C2S103/C2S185 into internal typed request forms while keeping opcode/widget identity internal. The standard augmented option-family close/cancel route is now exactly `C2S185(54195)`.

## READY FOR CHAT 3

**yes — standard Continue/option state-machine input contract**

Chat 3 can define semantic dialogue state independently of transport. Exact cache-backed statement roots, the parallel named 1–4-line model-dialogue family, the standard close/cancel transport, and runtime item-backed root `30700` are now closed. Remaining content-specific model-bearing/no-name roots and business branch effects should continue to be evidence-gated rather than guessed from generic 317 conventions.
