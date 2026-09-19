# Client opcode 3 schema — v0.3.3

Authority: exact `client(6).jar` SHA-256 `6232bae206846a4ba8d09766a2dee886b69016066a3f50f83b201bf705f93662`.

Static bytecode in `rs.Client.bz()` emits opcode `3` only on changes to `rs.C.hJ`:

- if `hJ` becomes true and the last sent focus state was false: `a(3); b(1)`
- if `hJ` becomes false and the last sent focus state was true: `a(3); b(0)`

`rs.C.focusGained(...)` writes `hJ = true` and `rs.C.focusLost(...)` writes `hJ = false`.

Therefore the exact wire schema is:

```
opcode 3
payload length: 1 byte (fixed)
payload[0]: focus state, 1=focused, 0=unfocused
```

This is a client UI/focus notification, not movement or world state.
