# M4 schema audit — v0.3 (strict status: NOT CERTIFIED)

Authority client: `client(6).jar`
SHA-256: `6232bae206846a4ba8d09766a2dee886b69016066a3f50f83b201bf705f93662`

This document separates **exact packet decoder schemas** from the stronger claim that the
combined world bootstrap is correct end-to-end.  The former is substantially recovered;
the latter remains `M4_PARTIAL / NOT_CERTIFIED` until the v0.3 appearance candidate is
accepted by the real localhost client and the session remains coherent.

## Packet 249 — exact fixed payload schema

The packet-size table gives opcode 249 a fixed payload length of 3.
The current `rs.Client.bP()` branch performs:

```text
Client.mk = mU.N();
Client.di = mU.U();
```

Current buffer transforms:

```text
N(): (wireByte - 128) & 255
U(): ((wire[1] & 255) << 8) + ((wire[0] - 128) & 255)
```

v0.3 emits:

```text
80 81 00
```

which decodes to:

```text
mk = 0
local player index / di = 1
```

## Packet 73 — exact fixed payload schema

The packet-size table gives opcode 73 a fixed payload length of 4.
The current branch reads:

```text
regionX = mU.T();
regionY = mU.A();
```

where:

```text
T(): (wire[0] << 8) + ((wire[1] - 128) & 255)
A(): (wire[0] << 8) + wire[1]
```

v0.3 emits region chunks `385,436`, which establish a scene base of approximately
`3032,3440`. Local offset `55,55` therefore corresponds to world `3087,3495`.

## Packet 81 — exact framing and local movement bits

The packet-size table marks opcode 81 as `-2`, meaning a two-byte payload length precedes
the payload.

The decoder order in the current client is:

```text
c(buffer)                 local-player movement / placement bits
d(buffer)                 existing other-player list
b(buffer, packetLength)   new-player list
a(buffer)                 update masks queued by the previous stages
```

and the branch verifies that the final byte position equals the packet payload length.
A mismatch throws `RuntimeException("eek")`.

For local movement type 3, the client consumes:

```text
1 bit   local update present
2 bits  movement type (=3)
2 bits  plane
1 bit   branch field
1 bit   queue local player for update-mask processing
7 bits  local X
7 bits  local Y
```

Then the current zero-other-player candidate writes:

```text
8 bits  existing other-player count = 0
11 bits new-player sentinel = 2047
```

The established idle packet has local-update bit 0, player count 0 and sentinel 2047,
which packs to exactly:

```text
00 7F F0
```

## Local appearance synchronization — v0.3 addition

For movement type 3, setting the one-bit mask flag queues player index 2047 (the current
client's self-player slot) for synchronization-mask processing.

The current mask parser reads one mask byte. Bit `0x10` is the appearance block:

```text
if (mask & 0x10) {
    len = buffer.O();
    byte[len] appearance;
    player.a(new rs.x.e(appearance));
}
```

`O()` is:

```text
(-wireByte) & 255
```

So a 55-byte appearance block encodes its length byte as `201` (`0xC9`).

The v0.3 minimal appearance block is deliberately plain:

```text
5 leading appearance/state bytes      0
1 signed-short role                    0
12 equipment slots                     empty
1 optional block flag                  0
5 color indices                        0
7 animation ids                        808,823,819,820,821,822,824
1 title/prefix flag                    0
username                               newline terminated
combat level                           3
skill/total short                      0
rank extension selector                0
extended appearance selector           0
```

For username `localtest`, this is exactly 55 bytes.  An offline parity test invokes the
pinned client's real `rs.a.k.a(rs.x.e)` parser reflectively and proves:

```text
bytes=55
consumed=55
displayName=localtest
```

This proves the standalone appearance block is structurally accepted by the real parser.

A second, stronger offline test allocates a minimal `rs.Client` state and invokes the pinned
client's complete private packet-81 decoder (`b(int, rs.x.e)`) on the exact 62-byte v0.3
`localtest` payload.  The real decoder reaches its exact final-position check without `eek`,
parses the appearance, and reports:

```text
PACKET81_FULL_CLIENT_DECODER_PARITY_PASS payload=62 consumed=62 displayName=localtest plane=0
```

This materially strengthens the packet-81 candidate. It still does **not** certify the
combined live 249 -> 73 -> 81 world bootstrap until the real localhost GUI run confirms
coherent self-player rendering and continued session stability.

## Ordering used by the candidate

After local response `2,rank,flag` and the proven first client packet `185 / widget 912`:

```text
249  local session/index candidate
73   region coordinates
81   local type-3 placement + appearance mask + empty other-player lists
```

Afterward the server sends the exact empty packet-81 payload `00 7F F0` every 600 ms.

## Certification status

- packet 249 fixed-size decoder schema: **STATIC EXACT**
- packet 73 fixed-size decoder schema: **STATIC EXACT**
- packet 81 framing and local type-3 bit schema: **STATIC EXACT**
- mask bit `0x10` appearance path: **STATIC EXACT**
- minimal appearance block parser acceptance: **OFFLINE CLIENT-PARSER PROVEN**
- combined 249 → 73 → 81 world bootstrap: **M4 PARTIAL / NOT CERTIFIED**
- local player visibly rendered from v0.3 packet: **NEEDS RUNTIME TEST**
- walking: **NOT IMPLEMENTED**
