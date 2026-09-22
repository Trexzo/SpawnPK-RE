# Exact v308 S2C Final Complex Publisher Gap — R5

SYSTEM

Final five exact-v308 S2C families remaining after R2/R3/R4 publisher-parity closure.

STATUS

EXACT-CLIENT-SCHEMAS-CLOSED / CURRENT-MAIN-COMPLEX-PUBLISHER-GAP / S2C-PARITY-FINITE

## AUTHORITY

~~~
client(6).jar
SHA-256 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
build/config 308
main handler rs.Client#bP()
local-region decoder rs.Client#c(rs.x.e,int)
~~~

Audited current-main baseline:

~~~
1ca841fdb413a06017b4729d6c5a286592f28aa1
~~~

Final exact families:

~~~
60  local region update batch
147 player-attached temporary object
215 ground item add excluding local player
241 constructed/dynamic region change
255 hit/block-drop popup event
~~~

## S2C60 — LOCAL REGION UPDATE BATCH

Frame: VAR_SHORT

Exact body begins:

~~~
localBaseY = y()
localBaseX = O()
~~~

Then until frame end:

~~~
childOpcode = y()
decode child body through the same shared local-region decoder
~~~

Exact child opcode vocabulary accepted by that decoder:

~~~
4,44,84,101,105,117,147,151,156,160,215
~~~

Therefore S2C60 is an envelope/batch transport over ordinary local-region scene mutations.

Current `SceneUpdatePublisher` already exposes exact standalone publishers for nine of those children:

~~~
4,44,84,101,105,117,151,156,160
~~~

but not 147/215 and not the S2C60 batch envelope.

Suggested internal API:

~~~
sceneBatch(localBaseX, localBaseY, operations[])
~~~

where operations are semantic scene-update records rather than raw child opcodes.

## S2C215 — GROUND ITEM ADD EXCLUDING ONE PLAYER

Frame: FIXED_7

Exact reads:

~~~
itemId              = T()
packedLocalTile     = P()
excludedPlayerIndex = T()
amount              = A()
~~~

Exact behavior:

- derives local tile from the current S2C60/S2C85 local-region base;
- if `excludedPlayerIndex == localPlayerIndex`, does nothing;
- otherwise creates the ground-item entry with supplied itemId and amount.

Suggested semantic publisher:

~~~
groundSpawnExcept(itemId, amount, tile, excludedViewer)
~~~

Do not make client scene index the domain identity; the presentation adapter resolves viewer/index encoding.

## S2C147 — PLAYER-ATTACHED TEMPORARY OBJECT

Frame: FIXED_14

Exact reads, in order:

~~~
packedLocalTile    = P()
playerIndex        = A()
xOffsetA           = R()
startDelayTicks    = S()
yOffsetA           = Q()
endDelayTicks      = A()
shapeRotation      = P()
xOffsetB           = z()
objectDefinitionId = A()
yOffsetB           = Q()
~~~

Derived exact fields:

~~~
shape    = shapeRotation >> 2
rotation = shapeRotation & 3
sceneGroup = nJ[shape]
~~~

Exact client behavior:

- resolves local player or another player by playerIndex;
- resolves the exact object definition;
- builds an object model against the tile's four terrain heights using shape/rotation;
- attaches that model to the player;
- active model window is:
  - `currentTick + startDelayTicks` inclusive;
  - `currentTick + endDelayTicks` exclusive;
- object footprint width/height swaps for rotation 1 or 3;
- xOffsetA/xOffsetB and yOffsetA/yOffsetB are independently min/max normalized;
- the resulting normalized tile bounds are stored on the player for the temporary-object presentation window.

Exact signed-byte readers:

~~~
R() = signed byte (128 - wire)
Q() = signed byte (-wire)
z() = raw signed byte
~~~

Suggested semantic publisher:

~~~
attachTemporaryObjectToPlayer(
  player, tile, objectDefinitionId, shape, rotation,
  startDelayTicks, endDelayTicks,
  xOffsetA, xOffsetB, yOffsetA, yOffsetB
)
~~~

## S2C241 — CONSTRUCTED / DYNAMIC REGION CHANGE

Frame: VAR_SHORT

Exact order:

~~~
regionY = T()
enter bit-access mode

for plane in 0..3:
  for localChunkX in 0..12:
    for localChunkY in 0..12:
      present = readBits(1)
      if present:
        descriptor = readBits(26)
      else:
        descriptor = -1

leave bit-access mode
regionX = A()
~~~

Exact client stores a:

~~~
int[4][13][13]
~~~

descriptor grid.

For each present descriptor, exact client later extracts at least:

~~~
sourceChunkX = (descriptor >> 14) & 1023
sourceChunkY = (descriptor >> 3)  & 2047
~~~

and derives source map-region identity from those chunk coordinates.

Do not over-name the remaining descriptor bits without a dedicated transform/rotation audit; preserving the exact 26-bit descriptor in the presentation adapter is valid.

Suggested API:

~~~
constructedRegion(regionX, regionY, descriptorGrid4x13x13)
~~~

with raw descriptor encoding kept inside exact-region presentation code.

## S2C255 — HIT / BLOCK-DROP POPUP EVENT

Frame: FIXED_12

Exact reads:

~~~
value            = A()
popupEventId     = E()
protectionType   = A()
~~~

Exact receiver:

~~~
rs.l.e.h#a(int,long,int)
~~~

Exact popup behavior:

- `value == 0` selects `popups/block drop`;
- `value > 0` selects `popups/hit drop` and renders the numeric value;
- events arriving within the client's short aggregation window may combine their numeric value;
- protectionType > 0 adds a protection icon:
  - 1 -> `popups/protmelee`
  - 2 -> `popups/protmagic`
  - other positive -> `popups/protrange`.

The 64-bit popupEventId is retained on the client popup record; separate exact application-state evidence can address existing popup records by unique identity.

Suggested semantic publisher:

~~~
combatPopup(value, popupEventId, protectionType)
~~~

This is combat presentation only; it must consume authoritative combat outcomes rather than becoming damage truth.

## READER / WRITER TRANSFORMS

~~~
y = raw u8
A = u16 BE
E = u64 BE
O = negated u8
P = 128-minus u8
Q = signed negated byte
R = signed (128-minus) byte
S = u16 LE
T = u16 BE with low-byte Add128 inverse
z = raw signed byte
~~~

Current `PacketPayloadWriter` already has the necessary inverses:

~~~
putU8
putU8Neg
putU8_128Minus
putI8
putI8Neg
putI8_128Minus
putU16BE
putU16LE
putU16BELowAdd128
putI64BE
~~~

## CURRENT-MAIN AUDIT

No normalized current-main emitters were found for:

~~~
60,147,215,241,255
~~~

Current SceneUpdatePublisher does, however, already implement nine standalone S2C60 child operations, so 60/147/215 should extend/reuse that exact scene-presentation substrate rather than creating a parallel scene engine.

Use status:

~~~
CURRENT_MAIN_COMPLEX_PUBLISHER_GAP_R5
~~~

## ARCHITECTURE BOUNDARY

Recommended implementation split:

~~~
SceneUpdatePublisher / SceneBatchPublisher
  -> 60,147,215

RegionPresentationPublisher
  -> 241

CombatPopupPublisher
  -> 255
~~~

These are presentation primitives.

- scene packets must consume canonical world entities/items/objects;
- dynamic region descriptors are presentation/map-instance layout, not instance ownership;
- popup values must consume authoritative combat facts;
- raw scene/player indices must never become public domain identity.

## ACCEPTANCE

Focused tests should prove:

- S2C60 empty and multi-child batch framing;
- S2C60 child decoding for all 11 allowed child opcodes or at minimum every implemented child;
- S2C215 local-player exclusion behavior;
- S2C147 exact 14-byte vector including all signed-byte transforms and start/end window;
- S2C241 all-empty grid and mixed descriptor grid, exact bit packing, regionY-before-grid/regionX-after-grid order;
- S2C255 block, hit, melee/magic/range protection-icon vectors;
- exact-v308 parser acceptance where practical;
- no raw opcode/scene-index identity in public gameplay/content APIs.

## RESULT

After R5, all 75 exact-v308 handled S2C families have a current-main parity classification.

There are no remaining `NO_COMPLETE_MAIN_PARITY_CLAIM` rows in the canonical S2C coverage ledger once this package is applied.