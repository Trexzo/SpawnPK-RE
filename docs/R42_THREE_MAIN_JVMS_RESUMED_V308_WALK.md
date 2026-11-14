# R42 — verify resumed walking after a recovered v308-shaped login

## Progression beyond R41

R41 proves that a synthetic v308-shaped player walks one tile to
**3088,3495** in a first complete Main JVM, then sees the exact restored
S2C81 position in a *second* fresh Main JVM. That did not yet establish
whether **C2S164 movement still works after the restored spawn**.

R42 executes **three sequential, real Main JVM server processes** against
one isolated temporary account. Each JVM has its own loopback listener,
client login, ISAAC stream, and clean owned-child shutdown. All client
messages remain synthetic v308-shaped; the original graphical client is
not launched.

## Actual three-process acceptance

| Process | Incoming saved position | Gameplay step | Required account state after EOF |
|---|---|---|---|
| JVM 1 | New disposable `opensrc` profile (HOME 3087,3495) | C2S164 east → byte-exact walking S2C81 `b0 01 ff c0` | **3088,3495,0** |
| JVM 2 | Must receive one exact saved-position S2C81 `e4 dd c0 07 ff` for **3088,3495** | Another C2S164 east → byte-exact walking S2C81 `b0 01 ff c0` | **3089,3495,0** |
| JVM 3 | Must receive one exact saved-position S2C81 `e4 dd c8 07 ff` for **3089,3495** | No new movement; observe normal login and inventory | **3089,3495,0** |

Each phase must pass these independent checks:

- The real server's disposable private `opensrc.properties` contains
  exactly one canonical instance of each of
  `movement.worldX`, `movement.worldY`, and `movement.plane`
  and matches the expected coordinates.
- Both walking phases have a real LocalSession log
  `M5_AUTHORITATIVE_TICK mode=WALK tiles=1 from=... to=...`
  proving the accepted step was applied in the authoritative World.
- Phases 2 and 3 have actual server log
  `ACCOUNT_LOADED profile=opensrc` and
  `V51214_POSITION_RESTORE world=...,...,0`.
- Before the ordinary inventory53, the client sees **exactly one**
  S2C81 with the correct saved-coordinate payload; no missing, duplicate,
  unexpected or wrong-position restoration can pass.
- Socket EOF triggers session World unregister and final account save
  before shutting down the exact test-owned server JVM. Both ports
  43594 and 43595 must be released before the next process starts.

The byte vectors are independently encoded by the same MSB-first bit
layout as `BootstrapPackets.BitWriter`. The C2S164 path is LE short-A
X, LE Y, run flag 0. The separate four-test negative-control suite
checks the exact 3088/3089 restoration payloads and 3088/3089 walk
packets, bad/duplicate restore updates, and out-of-bounds coordinates.

The implementation reuses R4's already established
`synthetic_persistence_reconnect` harness: fresh temp user home,
pre-admission occupied-port refusal, individually owned Main JVM
processes, private authenticated stdin shutdown on Windows,
clean shutdown/writer termination and no host-profile access.
The incoming Java `--bootstrap --movement` behavior is unchanged.

## CI and what this does not establish

`synthetic_v308_post_restore_walk.py` plus
`test_r42_v308_post_restore_walk.py` run on **GitHub Actions Linux
and Windows**, stacked after R41's full two-process verification.

A successful result establishes **continuing authoritative movement
across multiple fresh JVM cycles and correct saved-position packet
parity with synthetic clients**. It does not establish graphical v308
client execution, OpenGL rendering, real game authentication,
crash/power-loss fsync consistency, live multiplayer, or complete RSPS
parity. The user desktop was offline when this PR was created; no user
host action is needed or performed. The separate Windows Sandbox
graphical-client acceptance remains blocked by missing guest support.

R42 remains **DRAFT, STACKED on R41, UNMERGED** until its independent
Linux and Windows CI run passes and any failure is corrected.
