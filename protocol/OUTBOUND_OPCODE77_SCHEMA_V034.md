# Client opcode 77 schema (v0.3.4)

Pinned client: `client(6).jar`, SHA-256 `6232bae206846a4ba8d09766a2dee886b69016066a3f50f83b201bf705f93662`.

Static writer site: `rs.Client.g(int,int)`.

Exact framing:

1. `fv.a(77)` writes the ISAAC-obfuscated opcode.
2. `fv.b(0)` reserves a one-byte payload length.
3. The current buffer offset is saved.
4. A periodic/random telemetry payload is emitted.
5. `fv.j(currentOffset - savedOffset)` patches the reserved byte with the payload length.

`rs.x.e.j(int)` writes the supplied length into `buffer[h - len - 1]`, proving one-byte variable-length framing.

Observed localhost v0.3.3 instance: payload length `12`. The writer contains one optional two-byte field, so another valid instance may be length `14`. The local decoder therefore treats opcode 77 as var-byte and does not depend on a fixed payload length.

This packet is decoded only to preserve framing; no server-side behavior is attached to it.
