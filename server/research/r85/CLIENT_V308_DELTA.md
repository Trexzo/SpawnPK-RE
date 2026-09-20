# Exact-current client v308 delta

## Scope

This note records a static byte-for-byte comparison between the previous
LocalLab pinned SpawnPK client and the newer supplied v308 client.

No server gameplay behavior is inferred from this delta.

## Artifacts

Previous LocalLab pinned client:

- SHA-256: `6232bae206846a4ba8d09766a2dee886b69016066a3f50f83b201bf705f93662`
- `rs/f/a.c = 307`

Newest supplied client:

- source filename: `client(4).jar`
- SHA-256: `854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6`
- `rs/f/a.c = 308`

A second independently uploaded 17,629,002-byte JAR was byte-for-byte
identical to the v308 artifact above and had the same SHA-256.

## Whole-JAR comparison

Both JARs contain exactly 10,970 ZIP entries.

Comparison result:

- added entries: 0
- removed entries: 0
- changed entries: 1
- changed entry: `rs/f/a.class`
- old/new class size: 13,627 bytes / 13,627 bytes

The only `javap -p -c -constants` difference in that class is:

```diff
- sipush 307
+ sipush 308
```

No other class or resource byte changed.

## Meaning of rs/f/a.c

The exact login routine writes both:

1. `rs/v/a.c` into the inner login block; LocalLab treats this as the
   protocol/cache revision and currently requires 317.
2. `rs/f/a.c` immediately afterward as the second inner configuration
   integer.

The v308 artifact changes only item 2.

LocalLab's `LoginFrame` parses that second value as `configValue2`.
`LocalLoginTransport` validates the outer/protocol revision 317, not
`configValue2`.

Therefore the static evidence supports:

- outer protocol revision remains 317;
- client build/config value advances from 307 to 308;
- no packet decoder/application protocol change is visible in the JAR;
- v308 should remain compatible with the current LocalLab login parser.

## Authority classification

- JAR hashes, entry counts, changed class, bytecode constant and login write
  location: `EXACT_CURRENT_CLIENT`.
- LocalLab acceptance of both 307 and 308: repository behavior, covered by
  `ClientBuildRevision308CompatibilityTest`.
- Full runtime parity / inherited 179/179 with v308: **not yet proven**.

Do not replace the existing pinned-client 179/179 acceptance hash solely from
this static delta. Run the real LocalLab regression gate with v308 before
promoting it to the new pinned runtime authority.
