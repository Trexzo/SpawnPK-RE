# Evidence Package — Exact v308 S2C Reader / Handler Census R1

SYSTEM

Exact-current server-to-client packet framing and top-level handler coverage.

STATUS

CLOSED-EXACT-HANDLED-OPCODE-SET / CLOSED-FRAMING / PARTIAL-DEEP-SEMANTIC-COVERAGE

## AUTHORITY

Primary exact-current client:

```
SHA-256 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
build/config 308
Main-Class rs.gui.Launcher
```

The prior 307 regression fixture is byte-identical for every class involved in
post-login packet handling. Exact v308 was nevertheless re-read directly for
this census.

## EXACT DISPATCHER

Inbound framing and dispatch is owned by:

```
rs.Client.bP()
```

Exact state:

- `lO` = current decoded inbound opcode;
- `lN` = current packet body length;
- `mU` = inbound `rs.x.e` buffer;
- `lJ` = inbound ISAAC cipher;
- `nD` = socket abstraction.

When `lO == -1`, `bP()` reads one encrypted opcode byte, subtracts the next
ISAAC value, masks to 255, then loads:

```
lN = rs.f.d.b[lO]
```

Length rules are exact:

```
-1 -> one-byte variable body length
-2 -> two-byte variable body length
>=0 -> fixed body length
```

For the `-2` case, the exact client reads two length bytes and decodes them
through `rs.x.e.A()` before reading the body.

## EXACT HANDLED SET

A control-flow audit of `rs.Client.bP()` closes the handled set at:

```
75 distinct S2C opcodes
```

Framing split:

```
63 fixed-length
 8 VAR_SHORT
 4 VAR_BYTE
--
75 handled opcodes
```

Exact handled opcode set:

```
1,4,8,24,27,34,35,36,44,50,53,60,61,64,65,68,70,71,72,73,74,75,78,
79,81,84,85,87,97,99,101,104,105,106,107,109,110,114,117,121,122,126,
134,142,147,151,156,160,164,166,171,174,176,177,185,187,196,200,206,
208,214,215,218,219,221,230,240,241,246,248,249,250,253,254,255
```

The exact framing/family table is:

```
server/research/client_v308_coverage/s2c_census/v308_s2c_coverage.tsv
```

## IMPORTANT CORRECTION TO THE LEGACY LEDGER

Current `main` already contains:

```
protocol/INBOUND_HANDLED_OPCODE_LEDGER.csv
```

That file contains exactly the **65 direct dispatcher families**.

It is not the complete exact-client handled set.

Ten additional opcodes are routed by the shared local-region update decoder:

```
rs.Client.c(rs.x.e, int)
```

Those ten are:

```
4    local tile spot graphic
44   ground item add
84   ground item quantity update
101  local object remove
105  positional sound
117  projectile
147  player-attached temporary object/model
156  ground item remove
160  local object animation
215  ground item add excluding the local player
```

Opcode 151, local object spawn, is also implemented by the same shared decoder,
but it was already present in the legacy 65-entry ledger because it is the final
fall-through member of the dispatcher group.

Therefore:

```
65 legacy/direct ledger entries
+10 additional shared-decoder entries
=75 exact handled S2C opcodes
```

This is why the old ledger was useful but incomplete.

## SHARED LOCAL-REGION DECODER

S2C60 is itself a variable-short local-region update batch.

Exact v308 behavior:

1. reads two local-zone base bytes into `oF` and `oE`;
2. while unread bytes remain:
   - reads one local-region sub-opcode;
   - dispatches it through `c(buffer, subOpcode)`.

The shared decoder also handles the same regional update opcodes when they
arrive as top-level packets.

High-confidence exact effects include:

### S2C84 — ground-item quantity update

- derives an 8x8-local tile from the packed location byte;
- reads item id, old quantity and new quantity;
- walks the ground-item deque on that tile;
- finds matching item id + old quantity;
- replaces its quantity;
- refreshes the tile.

### S2C105 — positional sound

- derives local tile;
- reads sound id and packed radius/parameter byte;
- checks whether local player is within the packet radius;
- queues the sound into the client's bounded local sound arrays when sound is
  enabled and capacity remains.

### S2C215 — ground-item add excluding local player

- reads item id;
- reads packed local tile;
- reads a player index;
- reads quantity;
- adds the ground item only when that player index is not the local player;
- refreshes the tile.

### S2C156 — ground-item remove

- derives local tile;
- reads item id;
- removes the matching ground-item node;
- clears the tile deque when it becomes empty;
- refreshes the tile.

### S2C44 — ground-item add

- reads item id;
- reads quantity;
- derives local tile;
- constructs a ground-item node and appends it to the tile deque;
- refreshes the tile.

### S2C4 — local tile spot graphic

- derives local tile;
- reads graphic id, height offset and duration/delay;
- converts the tile to scene coordinates;
- constructs the client's spot-graphic entity and adds it to the graphics deque.

### S2C151 / 101 / 160

These are exact local-object spawn, removal and animation/update paths. They
derive the local tile and object type/orientation from the packet and update
the scene through the client's object-placement/model machinery.

### S2C117

Exact projectile construction:

- local start tile;
- signed destination tile offsets;
- target/entity key;
- projectile id;
- start/end height;
- start/end cycle offsets;
- trajectory parameters;
- client projectile object creation and queueing.

### S2C147

Exact temporary object/model attachment to a player:

- identifies local/remote player;
- identifies local object tile/type/orientation;
- builds the corresponding object model;
- stores start/end client cycles;
- assigns the model and world/tile bounds to the player presentation state.

This describes client presentation only. It does not prove why the production
server used this effect.

## DIRECT HIGH-VALUE FAMILIES ALREADY EXACT

The current exact client plus existing LocalLab research already gives strong
contracts for major direct handlers including:

- S2C65 NPC synchronization;
- S2C81 player synchronization;
- S2C73 static region change;
- S2C241 constructed/dynamic region change;
- S2C249 local session flag + player index;
- S2C53 full widget item-container update;
- S2C34 partial widget item-container update;
- S2C36 / 87 varp updates;
- S2C71 sidebar assignment;
- S2C97 main-interface open;
- S2C164 chatbox-interface open;
- S2C208 walkable interface;
- S2C248 main + sidebar interface open;
- S2C126 SpawnPK widget-text/control update bus;
- S2C250 SpawnPK application bus;
- S2C134 skill update;
- S2C110 run energy.

The R1 TSV records the complete 75-family client effect names, while keeping
three less-resolved labels deliberately conservative:

```
218 -> exact field en update, higher semantic name not yet closed
221 -> exact field kB/status update, higher semantic name not yet closed
255 -> exact custom tuple dispatch to rs.l.e.h, application meaning not yet closed
```

No production server meaning is invented for these three.

## CLIENT EFFECT != SERVER POLICY

A packet's client effect does not by itself prove the original server rule.

Examples:

- S2C50 proves friend/presence list presentation; it does not prove social
  persistence/privacy policy.
- S2C196 proves private-message receive presentation; it does not prove PM
  authorization/rate limits.
- S2C35/166/177 prove camera effects; they do not prove which gameplay content
  should invoke them.
- S2C4/117 prove graphic/projectile presentation; they do not prove damage,
  combat timing or hit rules.
- S2C74/121 prove music control; they do not prove region/music selection policy.

Those remain server/domain concerns.

## RELATIONSHIP TO CURRENT MAIN

The following existing repository artifacts remain useful and are now
subordinate to this exact-current census where coverage differs:

- `protocol/INBOUND_HANDLED_OPCODE_LEDGER.csv` — 65 direct handlers only;
- `protocol/SERVER_PACKET_SIZE_TABLE_ALL_0_256.csv` — exact length table;
- R84 S2C126 research;
- R84/R85 S2C250 application research;
- packet-65 NPC parity work;
- packet-81 player parity work;
- bootstrap/interface writer tests.

This Chat 4 package does not rewrite runtime packet writers.

## NEXT S2C WORK

1. Close the higher semantic identity of 218 / 221 / 255 if exact evidence
   exists.
2. Add per-family field-schema rows for the 75 handlers, prioritizing:
   - interfaces/containers/configs;
   - social/chat;
   - camera/audio;
   - regional entity/object presentation.
3. Compare all 75 client effects against current LocalLab S2C writers and
   identify concrete missing publisher families.
4. Link the finite S2C census into Issue #427 application/UI recovery.
5. Keep original server invocation/business rules evidence-gated.

## READY FOR CHAT 2

**yes for framing and internal presentation transport**

Chat 2 may treat the 75-opcode set and `rs.f.d.b[]` framing as exact-current
transport authority.

No runtime implementation is requested merely because a client handler exists.

## READY FOR CHAT 3

**yes for client-visible effects**

Chat 3 may consume semantic presentation contracts without depending on packet
numbers. Server gameplay reasons for sending them remain domain policy unless
separately recovered.
