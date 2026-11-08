# R28 — v308 login inner-block boundary and sensitive trace redaction

## Authentic-client evidence

The exact original v308 client JAR (SHA256
`854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6`)
and R11 certified airgap version (SHA256
`024fad774453430bb964076b98d460a6dae821ee31100322d104e30d9a9c97a7`)
were inspected by **read-only** `javap -c -p rs.Client` without loading or
launching the original GUI. The login frame construction still emits
`sipush 317` as the *outer protocol revision*, even with v308, and writes
a first inner block followed by nine CRC/checksum integers. This supports
the existing LocalLab contract:

- A two-byte prelogin request starts with **14**.
- The server returns 8 zero bytes, status 0 and an eight-byte server seed.
- The client sends login type **16** or **18**, one byte of outer length,
  a marker 255, big-endian revision **317**, memory flag, nine CRC32
  values, a one-byte inner-block length, marker **10**, four ISAAC seeds,
  two big-endian configuration integers and four newline-terminated
  ISO-8859-1 strings.
- Config integer 2 encodes a build marker such as **307** or **308**.
  It must **not** be mistaken for the outer protocol revision.

These observations agree with the existing synthetic
`ClientBuildRevision308CompatibilityTest` and
`HandshakeIntegrationTest`. Bytecode inspection plus a synthetic
wire handshake is **not** an original graphical v308 login.

## Concrete R28 defect and fix

The earlier `LoginFrame.parse` used
`Binary.readNlString(payload,cursor)` which searched for each newline
against **the full outer byte array** rather than stopping at the
one-byte-declared *inner block end*. It also permitted extra bytes after
the fourth field and declared 59 bytes as a minimum even though four
empty newline fields require a minimum **70-byte outer payload**.

R28 tightens this one narrow transport-parser boundary:

- The inner block must have at least **29 bytes** (marker 1, seeds 16,
  config ints 8 and four newline terminators 4).
- The declared inner length must exactly equal the remaining outer
  payload length. Underdeclared and overdeclared blocks both fail closed
  with `IOException`.
- Each of the **four strings** is decoded only up to the declared
  `innerEnd`; a missing newline is rejected there, not searched for
  elsewhere in the packet.
- After parsing exactly four fields, the cursor **must equal**
  `innerEnd`; trailing padding/data is rejected.
- Existing types 16/18, revision 317, ISAAC seed behavior and build
  307/308 support are retained. Account authentication, World,
  movement and combat logic are **unchanged**.

In addition, `LoginFrame.toString()` now redacts all attacker-supplied
username/password/device/client identity values and ISAAC session
seeds from the transport log. The parser still preserves the actual
values for account selection and cipher setup; only its diagnostic
string changes. Redaction is local to this class and does **not**
claim all server logging or third-party logging is free of sensitive
data.

## Regression evidence

`R28V308LoginFrameBoundaryTest` is a no-client, no-socket test
covering positive 307/308 × 16/18, correct decoded seeds and strings,
minimum legal 70-byte frame, malformed/oversized/undersized inner
boundaries, trailing bytes inside and outside the declared block,
missing fourth terminator, wrong markers and no sensitive metadata in
`toString()`. A malformed frame must produce an expected
`IOException`, never an accidental unchecked array overrun.

Linux and Windows GitHub CI run this R28 test explicitly **alongside**
the unchanged `ClientBuildRevision308CompatibilityTest` and
`HandshakeIntegrationTest`. Independent exact-head Windows source
build evidence is added to the PR after it actually completes.

## Scope

This fixes a real Java wire-parser defect in **LOCAL LAB**. It does not
make the proprietary original v308 graphical client playable. R27's
Windows Sandbox GUI gate is still untested, because the connected
Windows host has no confirmed running Sandbox guest. No original GUI,
external server, production credentials or real v312 user process is
needed for R28. Keep the PR stacked on R27, draft and unmerged until
full regression acceptance.
