# Exact v308 Missing Widget/Interface S2C Publisher Contract — R2

Authority: client(6).jar SHA-256 `854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6`, build/config 308.

Current-main baseline audited: `1ca841fdb413a06017b4729d6c5a286592f28aa1`.

Status: exact client decoders closed; no current-main direct or semantic publisher found for these 16 families in the code-search pass.

## Exact primitive read transforms

- `y()` = unsigned byte.
- `P()` = unsigned byte-C: `(128 - wireByte) & 255`.
- `A()` = unsigned big-endian short.
- `B()` = signed big-endian short.
- `S()` = unsigned little-endian short.
- `T()` = unsigned big-endian short-A (low byte decoded as `(wire-128)&255`).
- `U()` = unsigned little-endian short-A (low byte decoded as `(wire-128)&255`).
- `V()` = signed little-endian short.
- `W()` = signed little-endian short-A.
- `f()` = unsigned smart: one raw byte if first byte <128, else BE u16 - 32768.
- `D()` = big-endian 32-bit int.

## Missing exact publishers

| S2C | Frame | Exact body decode | Exact client effect |
|---:|---|---|---|
| 8 | FIXED_4 | `U(widgetId), A(modelId)` | widget static model; sets model type 1 and model id |
| 24 | FIXED_1 | `P(tabIndex)` | flashing sidebar-tab state |
| 34 | VAR_SHORT | `A(widgetId)` then repeated `f(slot), A(rawItemValue), y(qty)`; if qty=255 read `D(qty32)` | partial widget item-container update |
| 70 | FIXED_6 | `B(x), V(y), S(widgetId)` | widget position |
| 72 | FIXED_2 | `S(widgetId)` | clears widget item-id slots; exact client loop writes -1 then 0 to the same item-id array |
| 75 | FIXED_4 | `U(npcDefinitionId), U(widgetId)` | widget NPC model; normally model type 2, with special type-10 handling in exact client |
| 79 | FIXED_4 | `S(widgetId), T(scrollPosition)` | widget scroll position, client clamps into valid scroll range |
| 122 | FIXED_4 | `U(widgetId), U(rgb555)` | widget colour; converts packed 5/5/5 value into internal colour |
| 142 | FIXED_2 | `S(interfaceId)` | opens/replaces sidebar overlay; wire zero maps to internal -1 |
| 171 | FIXED_3 | `y(hiddenFlag), A(widgetId)` | widget visibility; hidden iff byte == 1 |
| 185 | FIXED_2 | `U(widgetId)` | widget local-player model; model type 3 and local appearance hash/model key |
| 187 | FIXED_0 | empty | opens generic name input with prompt `Enter name:` |
| 200 | FIXED_4 | `A(widgetId), B(animationId)` | widget animation; animation -1 clears relevant animation state |
| 218 | FIXED_2 | `W(interfaceRoot)` | dialog/chat-area interface root |
| 230 | FIXED_8 | `T(zoom), A(widgetId), A(rotationA), U(rotationB)` | widget model zoom/rotations |
| 246 | FIXED_6 | `S(widgetId), A(zoomDivisor), A(itemId)` | widget item model; itemId 65535 clears model; otherwise rotations/zoom are derived from item definition |

## Packet-specific exact details

### S2C34 partial container

The packet is variable-short and runs records until packet-body end. `slot` uses the exact client's unsigned-smart decoder. The raw item value is stored directly into the widget item-id array; keep the raw wire convention isolated in the presentation adapter rather than exposing it as a domain item id. Quantity is u8 unless the marker 255 is present, in which case a BE 32-bit quantity follows.

### S2C72 clear container

The exact v308 handler iterates the widget's item-id array and performs `-1` followed immediately by `0` into the same array element. No separate quantity-array clear is visible in this branch. The final item-id state is zero. Preserve exact wire compatibility; do not claim more than the decoder proves.

### S2C75 NPC model

The first `U()` is the NPC definition id; the second `U()` is the widget id. The normal path sets widget model type 2 and model id to the NPC definition. If the widget was already model type 10, the exact client additionally pulls NPC-definition presentation data and rewrites zoom/rotation/model fields. The server publisher only needs to emit the exact two-field body; the extra behavior is client-local.

### S2C79 scroll

The client clamps negative values to zero and caps the scroll position at `contentHeight - visibleHeight` for ordinary scrolling widgets.

### S2C122 colour

`rgb555` is split as:
- red = bits 10..14
- green = bits 5..9
- blue = bits 0..4

then stored as `(red << 19) + (green << 11) + (blue << 3)`.

### S2C142 sidebar overlay

A zero interface id is normalized to internal `-1`. Non-zero roots invoke the client's interface preparation path before becoming the active sidebar overlay.

### S2C185 local-player model

This packet carries only the widget id. The model key is derived client-side from the current local player's appearance/NPC-transform state. Do not add a server-supplied model-id field that the exact packet does not contain.

### S2C200 dialogue/model animation

The exact handler explicitly special-cases these eight widget/model roots:

`4883, 4888, 4894, 4901, 969, 974, 980, 987`

for model zoom/rotation defaults when an animation is assigned. This proves the alternate `969/974/980/987` family participates in the same native dialogue/model animation machinery. It does **not** by itself identify that family's business/dialogue semantic role.

### S2C218 dialog root

The only payload field is `W()` — signed little-endian short-A — stored as the active dialog/chat-area root. This is distinct from the main-interface and chatbox-interface packet families.

### S2C230 rotation/zoom

Wire order is zoom, widget id, rotation A, rotation B. Do not reorder fields to match a domain API. The adapter can accept semantic named arguments and encode exact wire order internally.

### S2C246 item model

Wire order is widget id, zoom divisor, item id. `65535` clears the model. Otherwise the exact client loads the item definition, selects model type 4, copies the item definition's two rotations, and calculates widget zoom as `itemDefinitionZoom * 100 / zoomDivisor`. A zero divisor would fault client-side; a typed publisher should reject zero.

## Current-main absence audit

Against current main `1ca841fdb413a06017b4729d6c5a286592f28aa1`:

- no direct `ServerPacketWriter.fixed/varShort` call was found for these opcode families;
- no semantic helper was found by widget/model/visibility/scroll/animation/position/name-input/dialog-root/model-zoom/item-model vocabulary searches;
- therefore these 16 are strong missing-publisher findings for the current main baseline.

This is stronger than the previous `NO_COMPLETE_MAIN_PARITY_CLAIM` status.

## Architecture handoff

Chat 2 should add reusable typed presentation encoders/facades, not feature-specific packet calls. Suggested semantic publisher surface:

- `setWidgetStaticModel(widgetId, modelId)`
- `flashSidebarTab(tabIndex)`
- `updateWidgetItemsPartial(widgetId, updates)`
- `setWidgetPosition(widgetId, x, y)`
- `clearWidgetItems(widgetId)`
- `setWidgetNpcModel(widgetId, npcDefinitionId)`
- `setWidgetScroll(widgetId, position)`
- `setWidgetColour(widgetId, rgb555 or semantic colour)`
- `openSidebarOverlay(interfaceId?)`
- `setWidgetHidden(widgetId, hidden)`
- `setWidgetLocalPlayerModel(widgetId)`
- `openNameInput()`
- `setWidgetAnimation(widgetId, animationId)`
- `setDialogRoot(interfaceRoot)`
- `setWidgetModelTransform(widgetId, zoom, rotationA, rotationB)`
- `setWidgetItemModel(widgetId, itemId, zoomDivisor)`

Raw opcodes and transform methods remain transport-adapter implementation details.
