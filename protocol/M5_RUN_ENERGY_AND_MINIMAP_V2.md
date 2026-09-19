# SpawnPK LocalLab v2 — run energy + minimap movement

## Server packet 110: run energy

Pinned raw client `rs.Client.bP()` branch:

```text
7225 getfield Client.lO
7228 bipush 110
7230 if_icmpne ...
7245 aload_0
7246 aload_0
7247 getfield Client.mU
7250 invokevirtual rs/x/e.y:()I
7253 putfield Client.eY:I
```

`rs.x.e.y()` reads one byte and returns `wire & 255`. The pinned server packet-size table has `110,1`.

Therefore server packet `110` is exact fixed-length 1 and directly initializes the client's `eY` run-energy/script value. v2 sends value `100` after the M4 player bootstrap.

v2 intentionally does **not** invent an original SpawnPK depletion/regeneration formula. The protocol/UI state is initialized exactly; formula reconstruction remains later server-logic work.

## Client packet 248: minimap walking

Pinned path writer statically selects:

```text
mode 0 -> opcode 164, payload length = steps*2 + 3
mode 1 -> opcode 248, payload length = steps*2 + 3 + 14
mode 2 -> opcode 98,  payload length = steps*2 + 3
```

The common path serializer writes first absolute waypoint X, signed waypoint deltas, first absolute waypoint Y and the run modifier. For mode 1, the minimap caller appends the declared 14 extra bytes after that ordinary path core.

v2 therefore parses `248` as:

```text
coreLen = packetLen - 14
core    = ordinary 164/98 movement body
suffix  = exactly 14 opaque minimap telemetry bytes
```

The suffix is preserved in `MovementRequest.telemetry` but is not interpreted or trusted for authority. Only the already-known path core is executed.
