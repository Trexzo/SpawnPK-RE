# R40 — actual v308-shaped persistent reconnect and position restoration

## Scope

R39 proved one persistent `opensrc` login, authoritative walk, final
profile file save/reload and server-log compatibility with R38's
future guest-only GUI evidence parser. Reading `opensrc.properties`
on disk, however, does not by itself prove that the **next real
`LocalSession`** restores the player's authoritative position to the
actual outgoing packet stream.

R40 adds a two-session, real-127.0.0.1 Java socket integration test:
`server/src/spk/local/R40PersistentV308ReconnectPositionTest.java`.

## Two-session acceptance sequence

- Creates a unique temporary account root, an isolated shared World
  with a 600ms pulse and a dedicated ephemeral localhost ServerSocket.
  The World remains alive through both sessions.
- First `LocalSession`: authentic-v308-shaped prelogin14, revision317
  with config2=308, ISAAC185/widget912, exact initial 249/73/81 and
  native UI/scene bootstrap; two distinct World idle updates; C2S164
  movement from **3087,3495** to **3088,3495**, confirmed by an exact
  S2C81 east-walk packet.
- Closes the first socket and waits for its `LocalSession` worker.
  Requires zero registered players/tick targets, a saved
  `opensrc.properties` under the **new temporary directory only**,
  and an independently reloaded `PlayerSnapshot` with
  `movement.worldX=3088`, `movement.worldY=3495`,
  `movement.plane=0`.
- Second `LocalSession` against the *same* World and profile:
  independent login preamble, success `2,205,0`, fresh ISAAC state,
  exact initial S2C249/73/81, energy110 and orb126. Immediately
  afterwards the server must transmit **S2C81
  `player81TeleportNoAppearance(0,55,56)`**, corresponding to the
  saved world coordinates. This specifically exercises
  `LocalSessionPlayerInitializer` and
  `LocalSessionBootstrapPublisher.publish`, not just the repository.
- Consumes and verifies native sidebars, all seven skills, inventory and
  equipment containers, the complete 85/101/151 HOME scene overlay and
  a nonempty NPC65 bootstrap. Then requires a subsequent S2C81 World
  idle packet.
- Closes the second socket and confirms the player's registration and
  tick target are removed and the second persisted save preserves
  the earlier walked position.

The shared World keeps NPCs advancing between the two logins. The
second-session test **does not incorrectly require** a byte-identical
NPC65 packet for the original NPC spawn positions. It checks the
complete scene protocol and a nonempty existing-World NPC update while
preserving strict byte-for-byte checks for the v308 player bootstrap,
the restored position packet, UI and skills.

## Evidence and regression controls

An independent Windows Java11 run of the exact-source test returned:

`R40_PERSISTENT_V308_RECONNECT_POSITION_PASS assertions=60 ... reconnectRestore81Exact=true ... secondWorldTick=true ... finalSaveReload=true originalGui=false`

Real session diagnostics also recorded
`V51214_POSITION_RESTORE world=3088,3495,0 localX=56 localY=55`.

GitHub CI executes the new Java class on Linux and Windows alongside
the inherited v308 synthetic socket and account safety regressions.
The full 175-test current-train suite is also used as an exact-HEAD
acceptance gate.

**This is still a synthetic Java client** carrying original-v308-shaped
protocol messages, not a launched graphical original v308 JAR. It does
not establish the GUI, OpenGL rendering, native dependencies, real
credentials, economy or production gameplay parity. Actual guest
acceptance with the hash-pinned airgap derivative from R23–R38 remains
blocked by Windows Sandbox availability/authorization. No Windows
firewall, VM feature, live client, real account or owner data is changed.

R40 is a stacked draft PR on R39 and remains unmerged while CI is checked.
