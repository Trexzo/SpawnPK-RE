# Exact v308 S2C Widget Publisher Gap — R2

SYSTEM

High-value generic S2C widget/interface publisher families handled by exact v308 but not currently exposed as normalized LocalLab generic publisher primitives.

STATUS

EXACT-CLIENT-SCHEMAS-CLOSED / CURRENT-MAIN-GENERIC-PUBLISHER-GAP

## AUTHORITY

Exact-current client:

~~~
client(6).jar
SHA-256 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
build/config 308
~~~

Exact handler:

~~~
rs.Client#bP()
~~~

Exact buffer readers:

~~~
rs.x.e
~~~

Current-main baseline audited:

~~~
1ca841fdb413a06017b4729d6c5a286592f28aa1
~~~

## PURPOSE

These are presentation primitives, not gameplay systems.

Chat 2 should implement them once as typed internal publishers so Chat 3/content modules do not hand-roll raw packet bodies for Duel, Slayer, Tournament, Collection Log, dialogue, Marketplace, etc.

## EXACT READ/WRITE TRANSFORM MAP

Exact client reader -> matching/current LocalLab writer transform:

| Reader | Meaning | Writer inverse |
|---|---|---|
| y() | u8 raw | putU8 |
| P() | u8 128-minus | putU8_128Minus |
| A() | u16 BE | putU16BE |
| B() | i16 BE | putI16BE |
| S() | u16 LE | putU16LE |
| T() | u16 BE, low byte -128 on read | putU16BELowAdd128 |
| U() | u16 LE, low byte -128 on read | putU16LELowAdd128 |
| V() | i16 LE | MISSING helper: putI16LE |
| W() | i16 LE, low byte -128 on read | MISSING helper: putI16LELowAdd128 |
| f() | unsigned smart | putSmartU |
| D() | i32 BE | putI32BE |

Only the two signed-LE helpers are absent from PacketPayloadWriter today.

## EXACT PACKET CONTRACTS

### S2C8 — widget static model

Frame: FIXED_4

Exact client reads:

~~~
widgetId = U()
modelId  = A()
~~~

Effect:

- widget model type becomes static model;
- model id becomes modelId.

Suggested semantic publisher:

~~~
widgetStaticModel(widgetId, modelId)
~~~

### S2C24 — flashing sidebar tab state

Frame: FIXED_1

Exact client reads:

~~~
tab = P()
~~~

Suggested publisher:

~~~
flashingSidebarTab(tab)
~~~

### S2C34 — partial widget item-container update

Frame: VAR_SHORT

Exact client reads:

~~~
widgetId = A()
repeat until frame end:
  slot   = f()
  itemId = A()
  amount = y()
  if amount == 255:
      amount = D()
~~~

Effect:

- updates only supplied slots;
- item and amount arrays are mutated in-place;
- slots outside the supplied sparse update remain untouched.

Suggested semantic publisher:

~~~
widgetContainerPartial(widgetId, updates[])
~~~

### S2C70 — widget position

Frame: FIXED_6

Exact client reads:

~~~
x        = B()
y        = V()
widgetId = S()
~~~

Effect:

- updates widget X/Y offsets.

Requires new PacketPayloadWriter.putI16LE for y.

Suggested publisher:

~~~
widgetPosition(widgetId, x, y)
~~~

### S2C72 — clear widget item slots

Frame: FIXED_2

Exact client reads:

~~~
widgetId = S()
~~~

Effect:

- clears the target widget item-slot state.

Suggested publisher:

~~~
widgetContainerClear(widgetId)
~~~

### S2C75 — widget NPC model

Frame: FIXED_4

Exact client reads:

~~~
npcDefinitionId = U()
widgetId        = U()
~~~

Effect:

- target widget enters NPC-model presentation;
- exact client also refreshes NPC-model presentation fields from the definition.

Suggested publisher:

~~~
widgetNpcModel(widgetId, npcDefinitionId)
~~~

### S2C79 — widget scroll position

Frame: FIXED_4

Exact client reads:

~~~
widgetId = S()
scroll   = T()
~~~

Effect:

- clamps scroll to [0, contentHeight - viewportHeight] where applicable;
- assigns resulting widget scroll position.

Suggested publisher:

~~~
widgetScroll(widgetId, scroll)
~~~

### S2C122 — widget colour

Frame: FIXED_4

Exact client reads:

~~~
widgetId  = U()
packed555 = U()
~~~

Exact client expands:

~~~
r = (packed555 >> 10) & 31
g = (packed555 >> 5)  & 31
b = packed555 & 31
internalRgb = (r << 19) + (g << 11) + (b << 3)
~~~

Suggested publisher:

~~~
widgetColor555(widgetId, packed555)
~~~

Do not expose internal client RGB packing to domain code unless a presentation adapter owns conversion.

### S2C142 — open sidebar-overlay interface

Frame: FIXED_2

Exact client reads:

~~~
interfaceId = S()
~~~

Effect includes clearing incompatible chat/input state and assigning the side/overlay root.

Suggested publisher:

~~~
openSidebarOverlay(interfaceId)
~~~

### S2C171 — widget visibility

Frame: FIXED_3

Exact client reads:

~~~
hidden   = (y() == 1)
widgetId = A()
~~~

Suggested publisher:

~~~
widgetHidden(widgetId, hidden)
~~~

### S2C185 — widget local-player model

Frame: FIXED_2

Exact client reads:

~~~
widgetId = U()
~~~

Effect:

- target widget enters local-player model presentation;
- model identity is derived from the local player's current appearance/morph state.

Suggested publisher:

~~~
widgetLocalPlayerModel(widgetId)
~~~

Direction note: this inbound S2C185 is unrelated to outbound C2S185 widget actions despite sharing the numeric opcode.

### S2C187 — open name input

Frame: FIXED_0

No body.

Exact client:

- opens generic name-entry mode;
- sets prompt text to `Enter name:`;
- clears current input text.

Suggested publisher:

~~~
openNameInput()
~~~

### S2C200 — widget animation

Frame: FIXED_4

Exact client reads:

~~~
widgetId    = A()
animationId = B()
~~~

Exact dialogue special cases:

~~~
4883, 4888, 4894, 4901, 969, 974, 980, 987
~~~

receive dialogue-style model camera defaults after animation update.

Suggested publisher:

~~~
widgetAnimation(widgetId, animationId)
~~~

### S2C218 — dialog/chat-area interface root

Frame: FIXED_2

Exact client reads:

~~~
interfaceId = W()
~~~

W() is signed LE16 with low-byte Add128 on the wire.

Requires new PacketPayloadWriter.putI16LELowAdd128.

Suggested publisher:

~~~
dialogChatAreaRoot(interfaceId)
~~~

### S2C230 — widget model rotation / zoom

Frame: FIXED_8

Exact client reads:

~~~
zoom      = T()
widgetId  = A()
rotationX = A()
rotationY = U()
~~~

Effect:

- widget zoom = zoom;
- widget model rotations = rotationX / rotationY.

Suggested publisher:

~~~
widgetModelTransform(widgetId, rotationX, rotationY, zoom)
~~~

### S2C246 — widget item model

Frame: FIXED_6

Exact client reads:

~~~
widgetId = S()
scale    = A()
itemId   = A()
~~~

Special case:

~~~
itemId == 65535 -> clear model presentation
~~~

Otherwise the client:

- switches widget to item-model presentation;
- loads item definition itemId;
- copies item model rotations;
- computes zoom from item-definition zoom and the supplied scale divisor.

Suggested publisher:

~~~
widgetItemModel(widgetId, itemId, scale)
~~~

## CURRENT-MAIN AUDIT RESULT

The current canonical packet/presentation surfaces were inspected on main baseline `1ca841f...`, including:

- ServerPacketWriter;
- PacketPayloadWriter;
- BootstrapPackets;
- ApplicationBus126Publisher;
- ApplicationPacket250Writer / ApplicationUiService;
- Authority R16/R25 and R22 presentation publishers;
- SceneUpdatePublisher;
- NativeEquipmentDeathUi;
- other named packet/publisher/UI helper surfaces from the current source tree.

No normalized generic publisher implementation for the 16 families above was found in those current-main presentation surfaces.

Use status:

~~~
CURRENT_MAIN_GENERIC_PUBLISHER_GAP
~~~

rather than claiming the client capability itself is missing.

## IMPLEMENTATION SHAPE FOR CHAT 2

Prefer one presentation utility/facade, for example:

~~~
WidgetPresentationPublisher
~~~

backed by exact packet writers.

Do not implement these separately inside Tournament, Slayer, Duel, Collection Log, dialogue, Marketplace, etc.

Minimum internal API families:

- model presentation;
- container full/partial/clear;
- widget geometry/style;
- input prompts;
- interface roots/overlays;
- animation;
- visibility/scroll;
- item/NPC/player model projection.

## ACCEPTANCE

Focused tests should prove exact bytes for each family and decode them through exact v308 where practical.

At minimum:

- golden byte vectors per packet;
- signed LE16 transform tests;
- signed LE16 low-Add128 transform tests;
- partial container short/extended quantity paths;
- S2C122 5/5/5 colour conversion vector;
- S2C246 itemId 65535 clear case;
- S2C187 zero-length frame;
- no raw widget/opcode identity leaks into public gameplay/content APIs.

## READY FOR CHAT 2

yes

## READY FOR CHAT 3

yes after generic publishers exist; domain systems should consume semantic presentation methods only.