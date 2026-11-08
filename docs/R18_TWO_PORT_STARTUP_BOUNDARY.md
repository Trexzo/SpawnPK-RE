# R18 — real Main two-port startup admission

R18 strengthens the LocalLab **server-only** startup boundary. It is not a
graphical client acceptance, and does not alter production launcher behavior.

## Previous failure

`Main.runServer` previously created and started the shared `World` before
attempting to bind either listener. If loopback 43594 or 43595 was already
owned, startup could fail after World startup, with an incomplete pair of
listening sockets. Even with R15's exclusive account directory lease, there
was no proof that startup performed no gameplay work under a port conflict.

## New admission discipline

1. Acquire R15's account directory lease before doing any startup work.
2. Create both fresh, test-controlled `ServerSocket` objects.
3. Bind **both** sockets to 127.0.0.1:43594 and :43595.
4. Only then create/start `World`, configure the session pool, install
   shutdown coordination and accept client connections.
5. `try-with-resources` ensures a successful first port is released when
   the second bind fails. R15's surrounding scope releases the directory
   lease. Neither process-owned socket is reused from a prior server.

## Real JVM test

`MainTwoPortStartupBoundaryTest` runs the actual `spk.local.Main` in
fresh, separately launched Java processes with a fully isolated temporary
account directory and `user.home`. It verifies, in order:

- An already-held R15 lease blocks Main before socket/World startup.
- An independently occupied AUX port makes Main exit unsuccessfully,
  releases its earlier game-port bind and leaves the test-owned AUX socket
  untouched. Main must not announce World/game readiness.
- An independently occupied game port fails similarly without touching AUX.
- After both conflicts are released, an actual new Main JVM starts both
  sockets. Its active R15 lease refuses a competitor.
- The admitted JVM exits cleanly through the R14 private stdin token;
  both listener ports become available again, both original profile bytes
  remain unchanged and the lease is reacquirable.

Negative tests may force-stop **only** the subprocess they just created
if it exceeds its bounded deadline. They never terminate any unrelated
Java processes, use real player accounts, or modify existing firewall rules.

## Proof and limitations

The fixture is compiled to Java 11-compatible bytecode and runs on Linux and
Windows GitHub Actions. Local Windows pre-CI verification compiled the new
Main and test source with Java 21 `--release 11` against the exact R16
server JAR, then executed the real Main child JVM under a dedicated Java11
runtime. Its result was:

`R18_REAL_MAIN_TWO_PORT_STARTUP_BOUNDARY_PASS`

This checks deterministic, process-owned startup behavior only. It does not
prove concurrent port-admission atomicity against hostile external binders,
full power-loss durability, original v308 graphical client compatibility or
real SpawnPK mechanics. Egress-gated v308 GUI admission remains blocked
without a validated OS outbound network boundary.
