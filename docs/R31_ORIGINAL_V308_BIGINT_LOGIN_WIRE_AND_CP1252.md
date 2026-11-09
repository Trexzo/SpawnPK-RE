# R31 — original v308 BigInteger login wire and Windows Java 11 Cp1252 parity

## What the exact original v308 bytecode establishes

The original v308 JAR SHA256 is
`854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6`;
its certified R11 airgap derivative SHA256 is
`024fad774453430bb964076b98d460a6dae821ee31100322d104e30d9a9c97a7`.
They were inspected **read-only** with `javap -c -p rs.Client` and
`javap -c -p rs.x.e`, not executed as graphical applications.

`rs.Client.a(String username,String password,boolean reconnect)` builds
a 10-prefixed inner buffer containing four ISAAC seeds, two config ints,
and four newline-terminated string fields (in the verified current
configuration). It invokes `rs/x/e.M()` before copying that block
into the outer login frame. The method's exact bytecode:

1. Reads the inner bytes, constructs a Java `BigInteger(byte[])`.
2. **Does not call `BigInteger.modPow` or any RSA operation.**
3. Immediately calls `toByteArray()`, then prepends **one byte with
   the resulting byte-array length** before copying the raw bytes.

Because the real verified client login block begins with 10 (a positive
signed leading byte), `BigInteger(byte[]).toByteArray()` produces the
same bytes. There is **no encryption of the username/password in this
observed client path**. It must not be described as secure transport.
The existing LocalLab parser's inner-length marker shape is therefore
consistent with these actual instructions. The two-byte outer revision
writer is big-endian and remains **317** even for v308 builds.

Another actual bytecode detail: `rs/x/e.a(String)` calls
`String.getBytes()` **without an explicit charset**, then copies
`String.length()` bytes (UTF-16 code units), *not*
`getBytes().length`, before writing the newline byte 10. On a
multibyte default charset such as UTF-8, this can truncate the
encoded representation of non-ASCII strings. That is a limitation of
the original client writer, not evidence that we can safely normalize
every possible Unicode username.

The verified private, **exact-SHA-pinned Windows JDK 11** used for
LocalLab reports `file.encoding=Cp1252` (Java 11.0.32), so common
Windows extended characters such as the euro sign produce Cp1252
bytes. In contrast, LocalLab's `LoginFrame.parse` previously decoded
all NL fields using ISO-8859-1, where byte `0x80` is a control
character, not a euro sign.

## R31 code and limits

- `LoginFrame.parse` now accepts an **optional**
  `-Dspk.local.login.decodeWindows1252=true` local JVM property.
  When enabled, its **four** NL fields are decoded as
  `windows-1252` to match the actual pinned Windows JDK 11.
- **OFF by default**: existing ISO-8859-1 parsing, ASCII login,
  auth/session semantics, the R28 exact inner frame bound and R30's
  separate default-off strict prelogin name/hash option remain
  unchanged.
- The overload `parse(loginType, payload, charset)` permits only
  `ISO_8859_1` or `windows-1252`. Arbitrary decoders such as
  UTF-8 are rejected; this avoids assuming Unicode compatibility the
  client writer never promises.
- `R31OriginalV308LoginWireCodecTest` independently models the
  observed `rs/x/e` `BigInteger` length prefix, the char-count
  copy quirk, outer marker/length/revision, 307/308 and types16/18.
  It tests non-ASCII Cp1252 decoding versus legacy ISO bytes, length
  corruption, supported charset modes and preservation of R30's
  username hash calculation.
- `R31Windows1252RealLoopbackLoginTest` uses actual **ephemeral
  127.0.0.1 TCP sockets**, a synthetic client reproducing this
  original wire shape, and the unchanged `LocalLoginTransport`.
  With the opt-in, the received username remains the intended
  Cp1252 text; with default ISO the original behavior is preserved.
  A separately strict R30 hash mismatch is refused. The test
  creates NO persistent accounts and registers no WorldPlayers.

Neither this optional decoder nor the 5-bit R30 name hash is an
authentication mechanism. The original buffer has no observable RSA
encryption in this path and no implied confidentiality. Host-level
network isolation and actual original-client login still require a
real network-disabled guest and genuine graphical acceptance.

## Evidence and outstanding gates

The initial **standalone Windows Java 11 tests** from exact branch
source passed: 48 checks of wire-format and Cp1252 decoder parity,
and 62 real-loopback TCP checks. The complete server build,
dedicated Linux/Windows GitHub CI and two-JVM LocalLab regression
results are recorded in PR comments once observed, not assumed here.

**Still unverified:** actual original v308 graphical launch,
native OpenGL compatibility in Windows Sandbox, authentic
username/password entry or gameplay. No original JAR was executed,
modified, packaged into GitHub or sent to any external service.
The R31 source change remains **draft, stacked and unmerged**.
