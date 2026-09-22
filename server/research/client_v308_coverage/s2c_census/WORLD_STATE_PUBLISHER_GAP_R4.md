# Exact v308 S2C World / Client-State Publisher Gap — R4

SYSTEM

Exact-current global/player-world client-state S2C families handled by v308 but not exposed as normalized current-main LocalLab publishers.

STATUS

EXACT-CLIENT-SCHEMAS-CLOSED / CURRENT-MAIN-WORLD-STATE-PUBLISHER-GAP

## AUTHORITY

~~~
client(6).jar
SHA-256 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
build/config 308
handler rs.Client#bP()
buffer rs.x.e
~~~

Current-main baseline audited:

~~~
1ca841fdb413a06017b4729d6c5a286592f28aa1
~~~

## EXACT PACKET CONTRACTS

### S2C1 — reset actor animations

Frame: FIXED_0

No body.

Exact client sets current player/NPC animation ids to -1.

Suggested publisher:

~~~
resetActorAnimations()
~~~

### S2C35 — camera shake channel

Frame: FIXED_4

Exact reads:

~~~
channel = y()
paramA  = y()
paramB  = y()
paramC  = y()
~~~

Effect:

- enables the selected camera-shake channel;
- stores the three supplied channel parameters;
- resets that channel's accumulated shake state to zero.

Suggested internal publisher:

~~~
cameraShake(channel, paramA, paramB, paramC)
~~~

Do not over-name the three parameters unless presentation code proves stronger semantic names.

### S2C61 — multicombat state

Frame: FIXED_1

~~~
state = y()
~~~

Suggested publisher:

~~~
multicombatState(state)
~~~

### S2C68 — reset varps to defaults

Frame: FIXED_0

No body.

Exact client copies its default/current varp baseline into active varps and refreshes changed configs.

Suggested publisher:

~~~
resetVarpsToDefaults()
~~~

### S2C74 — music track

Frame: FIXED_2

~~~
trackId = S()
if trackId == 65535: trackId = -1
~~~

Suggested publisher:

~~~
musicTrack(trackId)
~~~

### S2C78 — destination-state reset

Frame: FIXED_0

No body.

Exact client clears its active destination/path marker state.

Suggested publisher:

~~~
resetDestinationState()
~~~

### S2C99 — minimap state

Frame: FIXED_1

~~~
state = y()
~~~

Suggested publisher:

~~~
minimapState(state)
~~~

### S2C107 — camera reset

Frame: FIXED_0

No body.

Exact client disables forced camera mode and clears all five camera-shake channel active flags.

Suggested publisher:

~~~
resetCamera()
~~~

### S2C114 — system-update timer

Frame: FIXED_2

~~~
seconds = S()
absoluteDeadline = now + seconds * 1000
~~~

Suggested publisher:

~~~
systemUpdateTimerSeconds(seconds)
~~~

### S2C121 — queued music track

Frame: FIXED_4

~~~
trackId    = U()
delayValue = T()
~~~

Exact client schedules/loads the supplied music track and stores the secondary timing value.

Suggested publisher:

~~~
queuedMusicTrack(trackId, delayValue)
~~~

Keep the second field presentation-named until a stronger exact read site proves units/meaning.

### S2C166 — forced camera position

Frame: FIXED_6

~~~
tileX        = y()
tileY        = y()
heightOffset = A()
speed        = y()
acceleration = y()
~~~

If the final field is >=100, exact client immediately derives the camera world position from the supplied tile/height.

Suggested publisher:

~~~
forceCameraPosition(tileX, tileY, heightOffset, speed, acceleration)
~~~

### S2C176 — welcome/login metadata tuple

Frame: FIXED_10

Exact reads:

~~~
fieldA = O()
fieldB = T()
fieldC = y()
fieldD = Y()
fieldE = A()
~~~

`Y()` is the exact 32-bit byte order already supported by current `PacketPayloadWriter.putI32Y()`.

The client uses these values in its welcome/login presentation path.

Do not import historical 317 field names without exact-current proof.

Suggested internal API until stronger semantic naming exists:

~~~
welcomeLoginMetadata(fieldA, fieldB, fieldC, fieldD, fieldE)
~~~

### S2C177 — forced camera look-at

Frame: FIXED_6

~~~
tileX        = y()
tileY        = y()
heightOffset = A()
speed        = y()
acceleration = y()
~~~

If the final field is >=100, exact client immediately derives pitch/yaw toward the supplied point.

Suggested publisher:

~~~
forceCameraLookAt(tileX, tileY, heightOffset, speed, acceleration)
~~~

### S2C240 — weight

Frame: FIXED_2

~~~
weight = B()
~~~

`B()` is signed big-endian 16-bit.

Suggested publisher:

~~~
weight(weight)
~~~

### S2C254 — hint icon

Frame: FIXED_6

First byte:

~~~
type = y()
~~~

Exact type-dependent body use:

~~~
type == 1:
  npcIndex = A()

type in 2..6:
  type is normalized client-side to location-hint mode
  tileX = A()
  tileY = A()
  heightOffset = y()
  original type selects exact local marker offsets

type == 10:
  playerIndex = A()
~~~

Because the frame is fixed six bytes, branches that consume fewer semantic fields leave trailing body bytes unused by the handler.

Suggested semantic API:

~~~
hintNpc(npcIndex)
hintLocation(typeVariant, tileX, tileY, heightOffset)
hintPlayer(playerIndex)
clearOrRawHint(type)
~~~

rather than exposing one raw six-byte payload.

## READER TRANSFORMS USED

~~~
y = raw u8
O = (-wire) & 255
A = u16 BE
B = i16 BE
S = u16 LE
T = u16 BE with low-byte Add128 inverse
U = u16 LE with low-byte Add128 inverse
Y = exact mixed-order i32; current writer inverse is putI32Y
~~~

Current `PacketPayloadWriter` already has the required inverse primitives for this R4 set.

## CURRENT-MAIN AUDIT

Current main was checked through exact opcode-writer searches plus source-tree inspection for camera/music/minimap/hint/system-update/multicombat/weight publishers.

No normalized generic publisher implementation was found for:

~~~
1, 35, 61, 68, 74, 78, 99, 107, 114, 121, 166, 176, 177, 240, 254
~~~

Use:

~~~
CURRENT_MAIN_WORLD_STATE_PUBLISHER_GAP_R4
~~~

## ARCHITECTURE BOUNDARY

These are client presentation/state controls.

They must not become authoritative gameplay state merely because the client renders them.

Examples:

- S2C61 displays multicombat state but server combat-region rules remain authoritative;
- S2C114 displays an update deadline but scheduler authority remains server-side;
- S2C240 displays weight but inventory/equipment state must compute authoritative weight;
- camera/minimap/hint packets never become gameplay identity.

## ACCEPTANCE

Focused tests should cover exact frame and byte vectors for all 15 families, including:

- zero-body packets 1/68/78/107;
- S2C74 65535 -> no-track sentinel;
- S2C114 seconds encoding;
- S2C166/177 immediate >=100 final-field path vectors;
- S2C176 mixed-order 32-bit fieldD;
- S2C240 negative/positive signed weights;
- S2C254 NPC/location/player variants and fixed-six-byte padding.

## READY FOR CHAT 2

yes

## REMAINING S2C PARITY AFTER R4

Only the complex families remain unclassified:

~~~
60  local region update batch
147 player-attached temporary object
215 ground item add excluding local player
241 constructed/dynamic region change
255 hit/block/drop popup event
~~~