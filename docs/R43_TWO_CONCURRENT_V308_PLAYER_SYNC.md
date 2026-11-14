# R43 — real simultaneous LocalLab player sockets and v308 player81 sync

## Why this closes a new gap

R36–R42 prove increasingly complete **single-client** protocol,
persistent account, game tick, and process-restart behavior. Engine R3
already implements player options S2C104 and per-view remote player
S2C81, but this behavior needs acceptance through **two simultaneously
connected TCP clients on the actual full Main JVM**, not just constructed
`Player81WorldSync` objects or sequential logins.

## What the test does

`tools/verification/synthetic_v308_two_live_players.py` starts one
private, owned `Main` JVM on loopback, refusing to proceed if either
normal LocalLab port is occupied. It creates a fresh temporary account
root and authentic-v308-*shaped* synthetic clients.

- Client 1 prelogin14 / revision317 config2=308 / ISAAC185 widget912;
  initial scene and UI bootstrap. The alias `localtest` resolves to the
  persistent primary `opensrc` account.
- With Client 1 still **connected**, Client 2 repeats a separate
  v308-shaped login, independently admitted as the local `src`
  profile. The World must reach **two simultaneous registered players**.
- Both clients must receive all five native **var-byte S2C104**
  player options (Attack, Follow, Trade with, two disabled null slots)
  with correct slots/flag byte transforms.
- Both clients must receive real **S2C81 remote-player add**
  bit sections for the other player, including the appearance and
  discard-queue flags and signed 5-bit relative x/y at the shared HOME
  coordinate. This is an independently decoded bit-level check, not a
  string match on server diagnostics.
- Client 2 sends a real C2S164 east walk from **3087,3495** to
  **3088,3495** and must receive its own **exact** S2C81 walk payload.
  Client 1 must simultaneously see the remote walk as
  **S2C81 existing-player movement type 1, direction 4**.
- After Client 2 closes, Client 1 stays connected and must receive
  S2C81 remote-player **type-3 removal**. The World member count must
  decrease from 2 to 1 and ultimately 0 after Client 1 closes.
- Both separate persistent profile files must exist inside the private
  Temp root; the real LocalSession logs must include two successful
  logins, the expected `src` profile alias, and authoritative walk.

A new four-test negative/control suite exercises byte-exact local
idle/walk, remote add, remote east walk, removal, and truncated or
unbounded malformed S2C81 sections.

GitHub Actions Linux and Windows run both the negative controls and
the real Main JVM integration. The existing 175 focused and all CI
regressions are preserved.

## Boundaries

These are **synthetic** v308-shaped TCP/ISAAC clients using verified
wire framing. Real original graphical Java v308 clients, actual
rendered players, manual gameplay, and PvP/trade acceptance are **not**
established. Successful R43 checks support multiplayer networking
compatibility but cannot count as original GUI certification.

No owner machine or live java.exe is touched. The desktop was offline
during implementation. The only server is a uniquely owned child
process on a disposable CI machine, bound to localhost, with
individually authorized graceful shutdown. No external connections,
live accounts, firewall changes or Windows feature enabling.

Keep R43 DRAFT and stacked on R42, unmerged until independent CI
acceptance and correction of any failures.
