# v4.1 live decoder + interaction + item foundation

## Runtime symptom

The v4 localhost runtime first lost C2S framing at sequence 81 on opcode 226. Once the decoder paused, later movement, bank and tab clicks were necessarily emitted only as raw bytes.

## Exact current-client C2S schemas

### Opcode 226

Current client writer sequence begins with:

- `fv.a(226)`
- reserve one byte for payload length
- write randomized telemetry payload
- backfill the one-byte payload length

Therefore: **VARBYTE**. Payload is deliberately treated as opaque telemetry in LocalLab.

### Opcode 36

After applying the exact 226 framing to the captured raw tail, the next previously unsupported opcode was 36. Current client writer:

- `fv.a(36)`
- `fv.g(0)`

Therefore: **fixed 4 bytes**. Payload is treated as opaque periodic movement/checksum telemetry.

## Captured v4 tail replay

Source runtime login seeds:

`[8044414, 81717801, 19088743, -1985229329]`

The v4 log contains 601 raw TCP bytes after the first unknown-226 event. Replaying those bytes at the exact ISAAC position with:

- 226 = VARBYTE
- 36 = FIXED4

keeps the remainder decodable through later focus, movement, bank object, interface-close and telemetry traffic with no later unknown opcode in the captured tail.

## Bank adjacency

Object 26972 now follows a queued interaction contract:

1. decode object click;
2. if authoritative player Chebyshev distance to clicked object tile is `<= 1`, open immediately;
3. otherwise retain the pending bank interaction and let the already-submitted movement path advance on 600 ms server ticks;
4. when distance becomes `<= 1`, clear any remaining queued path and open the bank;
5. cancel stale pending interaction after timeout rather than opening remotely.

## Item foundation

`items.json` provides broad readable ID/name coverage. SpawnPK `i.bin` augments custom definitions with actions/clone/stackability metadata. The embedded catalog supports `::item <id> [amount]` against any known catalog ID.

Boundary: broad ID coverage is not the same as complete item mechanics. Equipment slot mapping cannot be inferred reliably for every item from `i.bin` alone.
