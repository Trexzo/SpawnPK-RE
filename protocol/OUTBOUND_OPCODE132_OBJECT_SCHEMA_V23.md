# Outbound opcode 132 — object first-option interaction

Pinned client: SHA-256 `6232bae206846a4ba8d09766a2dee886b69016066a3f50f83b201bf705f93662`.

Static proof appears twice:
1. `rs.Client` menu action case `502`.
2. `rs.o.a.a.a.N#a(rs.x.e)` dedicated action serializer.

Both emit exactly:

```text
fv.a(132)
fv.p(worldX)
fv.d(objectId)
fv.o(worldY)
```

Therefore payload length is fixed at 6 bytes.

Transforms:
- `p(v)`: `(v + 128) low byte`, then high byte.
- `d(v)`: high byte, then low byte.
- `o(v)`: high byte, then `(v + 128) low byte`.

Live v2.2 regression vector:

```text
payload = 97 0C 69 5C 0D 25
worldX = 3095
objectId = 26972
worldY = 3493
```

Runtime scope in v2.3:
- decode and log the object interaction,
- preserve C2S ISAAC/framing,
- do not yet open/simulate the bank UI.
