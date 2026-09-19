# v2.1 run-energy orb correction

Authority client: `client(6).jar` SHA-256 `6232bae206846a4ba8d09766a2dee886b69016066a3f50f83b201bf705f93662`.

## Legacy field

Inbound opcode `110` is fixed length 1. `Client.bP()` reads `rs.x.e.y()` and stores it in `Client.eY`. `y()` is an unsigned byte reader.

## Custom SpawnPK orb

The actual run orb renderer is `rs.i.b.n()`. It does not read `Client.eY`. It reads `rs.n.e.H[149].at`, ignores the final character, and decimal-parses the preceding characters. Thus the expected wire/UI representation is a percentage string such as `97%`.

Existing production runtime evidence from this exact client generation previously decoded:

```text
opcode = 126
key    = 149
data   = "97%"
```

## Packet 126 payload

`Client.bP()` decodes:

1. `rs.x.e.F()` — newline-terminated string.
2. `rs.x.e.T()` — widget id; high byte raw, low byte decoded as `(wire - 128) & 255`.
3. For positive widget IDs, assigns the string to `rs.n.e.H[id].at`.

Opcode 126 uses var-short framing (`rs.f.d.b[126] = -2`).

For widget `149` and text `100%`, payload bytes are:

```text
31 30 30 25 0A 00 15
```

where `00 15` decodes through `T()` to `149`.
