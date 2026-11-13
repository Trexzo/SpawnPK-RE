# R39 — real persistent v308-shaped first-walk trace against R38's guest parser

## What was missing

R36 verified a real `LocalSession`/World tick for a **nonpersistent** synthetic
`local` login. R34 separately verified persistent `opensrc` profile admission,
but without movement. R38's guest-only `Read-R38LocalGameTrace` was verified
only against hand-authored, synthetic log samples. Consequently its parsing of
the *real* stdout emitted by `LocalSession`, including the correct per-session
ordering, WorldPlayer identity and `persistent=true` marker, was not proven.

## New exact-source integration fixture

`R39PersistentV308LoginWalkTraceTest` creates a fresh temp directory,
a private `FilePlayerRepository`, an isolated `World` and a single ephemeral
**127.0.0.1** server socket. A synthetic original-v308-shaped wire client sends:

- Prelogin14 with genuine `opensrc` base37 `hash5`; revision317 and config2=308;
- Login success triplet `2,205,0` and first ISAAC185/widget912;
- Exact initial S2C249/73/81 packets, run energy110/orb126 and native sidebar,
  inventory and local HOME-NPC bootstrap;
- Two idle S2C81 World pulses;
- C2S164 one-tile east movement, expecting the actual World publisher's
  S2C81 walk direction 4 from **3087,3495** to **3088,3495**.

It closes the client socket, waits for the session to exit, verifies no World
membership/tick target remains and checks the disposable
`opensrc.properties` was saved and can be loaded by
`FilePlayerRepository`. This uses actual World/session/account persistence;
it never touches a real player account.

## Direct R38 parser compatibility

`tools/verification/test_r39_real_persistent_trace.ps1` starts **only this
test class** in an owned Java process (no graphical client). It captures its
actual stdout to a uniquely owned temporary file, extracts precisely the
production `Read-R38LocalGameTrace` parser from the current
`r27_guest_gui_window_smoke.ps1` and applies it unchanged. The parser must
find an in-order same-peer, same-WorldPlayer sequence of:

1. First `CLIENT_PACKET seq=1 opcode=185 len=2 widget=912`;
2. `V512_WORLD_REGISTER playerId=N`;
3. `LOGIN_SUCCESS_LOCAL rank=205 ... persistent=true`;
4. `M5_MOVEMENT_REQUEST MovementRequest{opcode=164,...} action=ACCEPTED`;
5. `[world player=N] M5_AUTHORITATIVE_TICK mode=WALK`, with displacement.

The test asserts precise 3087,3495 → 3088,3495 coordinates, single-tile
motion and expected WorldPlayer identity. Any failed Java exit, missing
success marker, parser rejection or wrong movement fails the test. It deletes
its own temporary stdout file on exit. This runs on the real Windows
PowerShell/Java platform and is a specific CI step.

The existing R38 negative-control fixture remains in the workflow, as do
the R32–R36 regressions. Linux runs the new Java integration test directly;
Windows tests both Java and log-parser evidence.

## Boundaries and remaining blocker

This is **not** a graphical v308 login or original-client acceptance test.
The network packets are sent by an isolated *synthetic* Java fixture. It
proves the persisted-account gameplay cycle and that R38's parser accepts
the server's actual log syntax. A real client-owned TCP socket, GUI window,
login screen, OpenGL rendering, bank/PvM/reconnect or real credential handling
are not established.

The R38 network-disabled guest package is already staged at
`spawnpk-r23-offline-g866_l6w` under the host's Temp directory, but Windows
Sandbox remains unavailable on the connected host. This PR neither enables
Windows features nor launches the proprietary JAR on the host. It does not
alter host firewall, owner-Java sessions or existing user data.

R39 is a stacked draft PR on R38 and must remain unmerged pending CI and
future expressly authorized Windows Sandbox GUI acceptance.
