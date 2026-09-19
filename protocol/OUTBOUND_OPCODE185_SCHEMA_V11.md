# Opcode 185 schema — v1.1 hotfix

Pinned client: evidence/client(6).jar, SHA-256 6232bae206846a4ba8d09766a2dee886b69016066a3f50f83b201bf705f93662.

Static bytecode audit: every outbound opcode-185 writer callsite has the same shape:

```
fv.a(185);
fv.d(widgetId);
```

`rs.x.e.d(int)` writes exactly two bytes in big-endian order (`value >> 8`, then `value`). Therefore client->server opcode 185 has fixed payload length 2 everywhere in this client.

Live v1 failure instance decoded as widget 152 because the two payload bytes immediately after encrypted opcode 185 were `00 98`. v1 paused because only the first login-time 185/widget-912 packet was special-cased. v1.1 decodes every later 185 identically and preserves ISAAC alignment.
