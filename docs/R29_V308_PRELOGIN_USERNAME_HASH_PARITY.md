# R29 — exact v308 prelogin username hash parity and range admission

## What was observed in real v308 bytecode

The original v308 reference JAR SHA256
`854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6`
and R11 certified airgap JAR SHA256
`024fad774453430bb964076b98d460a6dae821ee31100322d104e30d9a9c97a7`
were inspected **read-only** with `javap`. Neither original client nor
airgap client has been run graphically.

The original `rs.Client` prelogin sequence obtains
`rs.O.a(username)`, performs an **arithmetic right shift of 16 bits**,
ANDs the result with **31**, then sends exactly two bytes:
`14, userHash5`.

The original `rs.O.a(String)` implementation:
- Processes at most 12 Java UTF-16 `charAt()` *code units*.
- Multiplies the result by **37** for each code unit.
- Adds **1..26** for ASCII letters A-Z or a-z, **27..36** for digits 0-9.
- Adds **0** for other code units (including spaces and surrogate pairs).
- Repeatedly removes trailing base-37 zero digits while the result is nonzero.
- Takes `(nameLong >> 16) & 31` for the five-bit prelogin hash.

Exact golden vectors, calculated independently against the bytecode:
`local=2`, `localtest=29`, `myau=10`, `opensrc=12`,
`spawnpk=24`, with ASCII case-insensitivity. The UTF-16 test vector
`ab😀cd=27` ensures Python's emulation uses **Java code units**
instead of Unicode code points.

## Concrete mismatch and R29 changes

Before R29, synthetic LocalLab's Python prelogin runner sent **the
hardcoded value 7** for all username fixtures, while the real client
sends a hash derived from the supplied username. The LocalLab
server accepted **arbitrary 8-bit values** for that byte, including
32..255, impossible for the original v308 algorithm.

R29:
1. Adds `V308PreloginNameHash.java`, an exact Java copy of this
   client prelogin base-37 algorithm for wire research/tests. It is
   **not** an authentication function.
2. Replaces the independent Python synthetic client's hardcoded
   `bytes([14,7])` with `bytes([14,v308_username_hash5(username)])`,
   including Java UTF-16 semantics.
3. Updates `LocalLoginTransport.readLogin` to fail closed before
   returning its 17-byte prelogin seed/status reply when
   `userHash5 > 31`. Correct request 14 and hash range 0..31 continue.
4. Adds `R29PreloginHashClientParityTest`: full valid ephemeral
   localhost prelogin+login using username hash `2` for `local`,
   `29` for `localtest`; verifies 32 and 255 fail before seed,
   wrong request type rejected; asserts a deliberately legacy but
   **in-range** synthetic hash7 is still admitted.
5. Extends Python synthetic-wire golden vectors and runs both Java
   socket checks and Python fixtures on Linux and Windows CI, alongside
   the existing R28/legacy handshake regression suite.

**Explicit boundary:** Checking that the five-bit value lies in
0..31 is **not** verifying that it matches the name subsequently sent
inside the login block. Many older LocalLab synthetic Java test fixtures
hardcode an arbitrary *in-range* 7; R29 intentionally preserves those
fixtures. Exact name/hash equality remains a separate migration and
must not be claimed here. The prelogin hash is a legacy routing aid,
not a security credential or proof of account ownership.

## Safety and acceptance scope

All new tests use synthetic usernames and ephemeral
`127.0.0.1` sockets. They do not launch or modify the original v308
GUI, original RSPS game, Windows Sandbox, or owner v312 clients. No
production credentials, cached game bytes or client binaries are
committed. Existing LocalLab world, bank, login success triplet and
ISAAC implementation remain unchanged.

An exact-tree Java11-compiled server build and the Linux/Windows CI
results are reported separately in the PR after the tests actually
finish. Source/bytecode parity alone does **not** establish a working
original GUI/login or gameplay session.
