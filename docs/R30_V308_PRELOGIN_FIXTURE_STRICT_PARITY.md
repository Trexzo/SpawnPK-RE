# R30 — authentic v308 prelogin fixture migration and optional strict name/hash parity

## Source evidence and motivation

R29 verified the original v308 prelogin username hash is a 5-bit base37
value derived from the username; LocalLab legacy Java test clients
nevertheless still sent a hardcoded `7`, independent of their login
username. This left many tests unrepresentative of genuine v308 traffic.

Read-only inspection of the exact-hash original v308 client `rs.Client`
method `a(String username,String password,boolean)` shows the SAME
`aload_1` username argument is used by both operations:

1. `rs/O.a(username)` → `(long >> 16) & 31` → byte after prelogin 14.
2. `rs/x/e.a(username)` to serialize the first NL-delimited string
   of the actual login block.

This supports a name/hash consistency check for the *observed client
code path*, but is not an actual runtime GUI acceptance and cannot
certify all possible character-encoding or alternate-client behavior.

## Changes

- **22** existing Java LocalLab socket-fixture sources now transmit
  `V308PreloginNameHash.hash5(...)` instead of `out.write(7)`.
  The argument is the exact static username `local` or `opensrc`,
  or the same dynamic `user` passed to `loginPayload(seeds,user)`.
  The 22 fixtures cover bootstrap, bank, inventory, equipment, pets,
  logout, developer panels, trade, region commands, revision
  compatibility and shared-world packets.
- `LocalLoginTransport.readLogin` adds an optional *post-frame*
  equality check:
  `userHash5 == V308PreloginNameHash.hash5(frame.username)`.
  It executes only when process property
  `-Dspk.local.prelogin.enforceUsernameHash=true` is explicitly set
  in a synthetic isolated LocalLab JVM or when a test invokes the
  new boolean overload. This option is **OFF BY DEFAULT** to preserve
  existing LocalLab/session compatibility before a real original v308
  GUI login can be independently observed.
- A mismatched frame fails with `IOException` *after* the seed reply
  and after parsing the framed username, but before the transport
  returns a login frame to session/account initialization. Its
  rejection message includes neither the username nor the hashes.
- `R30OptionalStrictPreloginNameHashTest` exercises true and false
  enforcement on **real ephemeral 127.0.0.1 sockets** for username
  pairs `local`, `localtest` and `opensrc`. Deliberate mismatches
  are rejected in strict mode, but accepted in legacy mode; a JVM
  property switch is independently exercised and restored.
- `test_r30_prelogin_fixture_migration.py` checks each of the 22
  migrated source files, fails if hardcoded `out.write(7)` recurs,
  and asserts the strict gate is opt-in. It also verifies the older
  R29 `hash7` compatibility test remains a **deliberate** negative
  control, not an accidentally migrated fixture.
- GitHub Actions runs the R30 migration audit and actual localhost
  strict-check test separately on both Linux and Windows, in addition
  to existing R29/R28 login and LocalLab gameplay regressions.

## Boundaries and tests

**Not an authentication control.** Five-bit name hashes collide and
are trivially forgeable. Strict mode validates a particular client
wire protocol property, not account ownership, authorization,
cryptography, origin or egress isolation. Never treat hash5 as a
credential, account ID or login secret.

No external network is accessed; all new test sockets are local
ephemeral loopback endpoints. No original client JAR, game cache
binary, real account, password or other user profile is committed.
This is a server/test-code-only draft PR stacked on R29.
The already running owner v312 clients and Windows firewall remain
untouched. No Windows Sandbox guest, actual original-v308 graphical
login, OpenGL rendering or real-player gameplay has been demonstrated.

Do not set the optional strict mode as a production-wide default based
on these synthetic tests alone. The original v308 GUI login and
encoding path need acceptance in an independently verified isolated
guest first. Local Windows and GitHub CI evidence are added to the PR
only after observed execution.
