# R41 — persist v308 walking position across two independent Main JVMs

## Why this is not the same as R40

R40 reconnected a persistent `opensrc` account through two genuine
`LocalSession` socket sessions in **one shared World and one JVM**. Its
strict packet check proves a persisted position is restored on login, but
does not exercise complete Java server shutdown and the following fresh
process's on-disk account lifecycle.

R41 explicitly boots **two distinct full `spk.local.Main` server JVMs**
on 127.0.0.1:43594/43595 using the repository's existing, tested
`synthetic_persistence_reconnect.start_server / stop_own_server` contract.
The incoming clients are synthetic Python original-v308-shaped protocol
fixtures. No original graphical JAR is launched.

## Exact evidence from each JVM

Both JVMs share only a disposable `TemporaryDirectory` and a private
`only-test-account/opensrc.properties`; no user directory is mounted.

**JVM 1**

1. No pre-existing process may listen on either 43594 or 43595; refuse
   the trial rather than contact, terminate or take over another server.
2. Start an individually owned loopback-only Main JAR with
   `--bootstrap --movement` and a private absolute account path.
3. Send v308-shaped prelogin14, revision317/config2=308, ISAAC185,
   native widget912. Verify initial S2C249/73/81, energy110/orb126 and
   normal inventory53.
4. Send original C2S164 movement to **3088,3495** from the HOME
   origin **3087,3495**. Require the exact 4-byte
   `BootstrapPackets.player81WalkStep(4)` payload
   (`b0 01 ff c0`) in an authentic ISAAC-decrypted S2C81 frame.
5. Close the session socket, require World unregistration, clean
   persistence and private account snapshot with exact
   `movement.worldX=3088`, `movement.worldY=3495`,
   `movement.plane=0`. Stop **only this owned JVM** through the
   preexisting graceful stdin shutdown token on Windows (or POSIX
   SIGTERM on Linux); require clean shutdown/port release before
   beginning JVM 2.

**JVM 2**

1. Start a separate Main JVM on the same ports with the unchanged
   private account file.
2. Repeat synthetic revision317/v308-shaped login and ordinary GUI
   packet bootstrap. Independently inspect all opcode81 frames before
   the inventory53. Require **exactly one** byte-for-byte restored
   position S2C81 payload, matching the Java
   `BootstrapPackets.player81TeleportNoAppearance(0,55,56)`
   (`e4 dd c0 07 ff`). Refuse no packet, duplicated packet, or
   different coordinates.
3. Confirm the actual server log includes
   `ACCOUNT_LOADED profile=opensrc` and
   `V51214_POSITION_RESTORE world=3088,3495,0`, and final clean
   session accounting; inspect the private on-disk snapshot again.
4. Gracefully stop this second owned JVM and require both ports are free.

The R41 Python reader uses a strict, fail-closed S2C framed packet decoder,
the verified legacy Main JVM harness, and a local independent MSB-first
bit-packing function. Its four unit tests reject missing/duplicate/wrong
restore frames, invalid bit fields, malformed or duplicate persisted
position keys, and absent account files. All temporary artifacts are
removed. Any accepted local server, binary read error, missing packet or
failed persistence causes a nonzero test exit.

## Limitations

A passing R41 would establish **real Java-process restart durability
and protocol-level restoration** of one synthetic account and position.
It would **not** establish crash/power-loss fsync guarantees, a live
SpawnPK account, original graphical v308 client compatibility, renderer
functionality, or full gameplay parity. It does not use the owner's
Windows computer or alter the firewall. Original GUI acceptance remains
blocked until an isolated Windows Sandbox guest becomes available
through a separately authorized administrator.

The new test runs on **GitHub Linux and Windows CI** using owned
temporary Main JVM processes. R41 is a stacked **draft/unmerged PR**
on R40. Never interpret mere creation of the CI fixture as a passing
cross-JVM trial until the job finishes.
