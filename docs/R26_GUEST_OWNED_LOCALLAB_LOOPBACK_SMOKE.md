# R26 — Guest-only owned LocalLab server loopback acceptance

## What R26 adds

R25 provided the SHA-pinned static v308 cache, a private Java11 runtime
and a *manual* procedure to copy the read-only cache into a new writable
home INSIDE a future Windows Sandbox guest. R26 adds a separate manual
server-only acceptance step, using **existing LocalLab server semantics**:

- `Main.main()` binds **only 127.0.0.1:43594** (game) and
  **127.0.0.1:43595** (AUX HTTP).
- The existing R14 acceptance-only property pair enables a
  **64-lowercase-hex private stdin token**. Matching the token requests a
  normal JVM exit and permits the existing World persistence/shutdown
  hooks to run; there is no network shutdown endpoint.
- The R15 account directory lease and R16 atomic file publisher protect
  the two local synthetic account files.

This is **CUSTOM_LOCALLAB**, not a live SpawnPK production server.
Connecting to a game TCP port without sending login packets proves
**TCP connect only**, not protocol handshake or graphical rendering.

## R26 artifacts

`tools/verification/r26_guest_owned_server_smoke.ps1` is a guest-only,
**manual** acceptance action. It requires a real Windows Sandbox guest
account, a canonical R25 private guest HOME with valid receipt, the
R24 guest preflight (which itself calls R23), a currently empty private
`.spawnpk-data`, free localhost game/AUX ports and an exact manual
approval phrase.

By default the script produces a **read-only plan** and starts no server.
Only with explicit `-Run` does it create a **new synthetic account
folder inside the existing disposable guest HOME**, start **one child
Java server**, verify both listeners belong to that exact child PID,
query the AUX `versions.txt` response **directly on 127.0.0.1**
with proxies bypassed, and connect to the game TCP listener.

It then sends a unique `secrets`-equivalent 32-random-byte hex token
via the child’s private standard input, waits for clean JVM exit,
checks both ports are released, and writes a guest-only receipt.
If shutdown fails, only its own `System.Diagnostics.Process` object
may be forcibly stopped, and the result is BLOCKED. It never uses
`taskkill /IM java.exe`, `Stop-Process` or other global Java termination.

`tools/verification/r26_guest_server_package.py` extends a **fresh R25
package**, keeps the two existing read-only host mappings,
`Networking=Disable`, the hardened R23 settings and **no LogonCommand**.
It adds only the guest-only server smoke script under the existing
`C:\SPKR23\assets` folder and creates
`SpawnPK-R26-NetworkDisabled-OwnedServer-REVIEW-ONLY.wsb`.
No graphical client launch command is included.

`tools/verification/r26_owned_local_server_stdin_smoke.py` is a separate
**host-side** opt-in synthetic-server acceptance check. It permits
an actual controlled Java11 LocalLab server child in an invocation-owned
temporary HOME on ports 43594/43595, only after exact source/JAR/JDK and
free-port admission. It verifies the real 127.0.0.1 services, private
stdin token shutdown, persistence-close log, and port release, then
leaves other client processes alone. Its results are **host server only**,
not proof of guest network isolation.

## Manual guest workflow (only after separate Sandbox setup)

A future authorized operator would:

1. Open the reviewed SHA-pinned R26 `.wsb` with Windows Sandbox networking
   **disabled** and no autostart.
2. Manually run `C:\SPKR23\assets\guest-private-home.ps1`
   in default plan mode. After all R23/R24 checks pass, explicitly
   create a new guest writable HOME using
   `-Hydrate -ExactApproval 'CREATE R25 PRIVATE GUEST HOME'`.
3. Copy the exact guest HOME path printed by R25, then run a **plan-only**
   R26 command:

   ```powershell
   & 'C:\SPKR23\assets\guest-server-smoke.ps1' -GuestHome '<R25 guest-only HOME>'
   ```

4. Only after independent review and explicit authorization, execute:

   ```powershell
   & 'C:\SPKR23\assets\guest-server-smoke.ps1' -GuestHome '<R25 guest-only HOME>' -Run -ExactApproval 'RUN R26 OWNED GUEST LOCAL SERVER SMOKE'
   ```

There is **no automatic GUI startup**. R26's guest receipt is considered
valid only after it is actually observed in a real guest.

## Claims and open blockers

At source/pipeline preparation time, Windows Sandbox is not installed/
enabled on the connected host. It must not be enabled, launched, or
reconfigured implicitly by R26. The three existing owner v312 Java
clients must remain unaffected.

Host-side source, Python unit and exact-jvm LocalLab tests can establish
the LocalLab server runtime behavior, but cannot establish actual
Sandbox effective no-egress policy, guest loopback, guest GUI
compatibility, v308 login/scene/graphics or game playability. A static
cache inventory plus SHA256 does **not** certify opaque asset content
privacy or safety.

R26 is a new stacked **draft, unmerged PR** on R25. All code and
verification scripts are public, but no original proprietary client
JAR, game cache bytes, real user accounts or passwords are committed.
