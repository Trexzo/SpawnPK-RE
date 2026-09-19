# M5 authoritative movement — v1

## Status

v1 enables **server-owned movement** for client opcodes `164` and `98` inside the already loaded M4 region.

The client still performs its normal local pathfinding and submits the resulting compressed path. The localhost server:

1. decodes the ISAAC-protected movement opcode;
2. decodes the one-byte packet length;
3. recovers the first absolute waypoint and subsequent signed waypoint deltas;
4. expands turning points into one-tile 8-direction steps;
5. replaces the prior queued path with the newest client request;
6. advances the authoritative world coordinate on the 600 ms server tick;
7. sends packet `81` type-1 walk or type-2 run movement back to the client.

## Exact client writer schema

For opcodes `164` and `98` the pinned client writes:

- `p(firstWaypointX)` — low byte with +128 transform, then high byte;
- zero or more `(signed dx, signed dy)` pairs relative to that first waypoint;
- `n(firstWaypointY)` — little-endian unsigned short;
- `l(runFlag)` — negated byte.

The writer reconstructs its route from the clicked destination back toward the current tile, then serializes the points in forward walking order. Therefore the first absolute coordinate on the wire is the first waypoint the server should walk toward, not the current player coordinate.

## Exact server packet-81 movement schema

The pinned client's `Client.c(rs.x.e)` decoder accepts:

### one walking step

- local-update bit = `1`
- movement type = `1` (2 bits)
- direction = 3 bits
- self synchronization-mask bit = `0`
- zero existing other players
- new-player sentinel `2047`

### two running steps

- local-update bit = `1`
- movement type = `2` (2 bits)
- first direction = 3 bits
- second direction = 3 bits
- self synchronization-mask bit = `0`
- zero existing other players
- new-player sentinel `2047`

Direction numbering is copied from `rs.a.c.a(boolean,int)`:

| dir | delta |
|---:|:---|
| 0 | -1,+1 |
| 1 | 0,+1 |
| 2 | +1,+1 |
| 3 | -1,0 |
| 4 | +1,0 |
| 5 | -1,-1 |
| 6 | 0,-1 |
| 7 | +1,-1 |

## v1 safety/authority boundaries

- Movement is active only with `--bootstrap --movement`.
- `RUN_SERVER_LOCAL_WORLD.ps1` enables it.
- `RUN_SERVER_M4_BASELINE.ps1` keeps the certified world bootstrap but disables M5 movement.
- The newest client movement request replaces the previous queued route.
- Maximum expanded queue: 128 tiles.
- v1 refuses requested waypoints outside the currently loaded 104x104 region.
- v1 does **not** yet reconstruct an independent server collision map. It follows only paths submitted by the real client, so collision legality is still client-derived.
- Opcode `248` remains framed/observed only. Its additional 14-byte minimap telemetry extension is not used for authoritative movement in v1.

## Offline certification

The release must pass all of:

- `MOVEMENT_STATE_PASS`
- `M5_PACKET81_CLIENT_PARITY_PASS` against the original `client(6).jar`
- `M5_AUTHORITATIVE_MOVEMENT_INTEGRATION_PASS`
- existing M4 appearance/full packet-81 parser parity tests
- existing login/ISAAC/bootstrap regressions

Runtime certification remains separate: the actual airgapped client should visibly walk in response to a normal click and server logs should show matching `M5_MOVEMENT_REQUEST` and `M5_AUTHORITATIVE_TICK` lines.
