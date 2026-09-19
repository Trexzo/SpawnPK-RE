# Login protocol — exact current client(6).jar static reconstruction

Authority client SHA-256: `6232bae206846a4ba8d09766a2dee886b69016066a3f50f83b201bf705f93662`

1. TCP game connection: port `43594`.
2. Client sends two pre-login bytes: `14`, then `(usernameLong >> 16) & 31`.
3. Client reads/discards 8 server bytes, then reads one status byte.
4. Status `0` continues. Client then reads an 8-byte big-endian server seed.
5. ISAAC seed words = random int, random int, serverSeed high32, serverSeed low32.
6. Inner block begins `10`, four seed ints, current config ints, then newline-terminated login strings.
7. `rs.x.e.M()` performs BigInteger round-trip only; no `modPow`. It prefixes the resulting block with a one-byte block length.
8. Outer normal/reconnect type = `16`/`18`.
9. Outer payload = `255`, BE short `317`, one memory/graphics flag byte, nine BE checksum ints, inner login block.
10. Client->server post-login opcode = `(opcode + ISAAC.nextInt()) & 255` using original seeds.
11. Server->client opcode expected by client = `(wireByte - ISAAC.nextInt()) & 255`, with each seed word incremented by 50 for that direction.
12. Login success bytes = `2`, physical rank byte, boolean flag byte.
13. Current client immediately queues opcode `185` with BE short widget id `912` after accepting response `2`.

The local server logs username/device fields but deliberately redacts the password.
