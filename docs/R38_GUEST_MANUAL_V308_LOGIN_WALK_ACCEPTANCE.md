# R38 — manual v308-derivative graphical login and first walking tick, guest only

## Purpose

R27 verifies only the existence of a Windows GUI window. R37 optionally
captures a reviewable screenshot, but neither proves an actual **GUI to
LocalLab login** nor a playable first world tick. The earlier R36 integration
test does prove protocol-shaped login/movement but it uses a **synthetic
Java socket client**, not the graphical client.

R38 adds a deliberately opt-in, **guest-only manual operator workflow** to
the existing R27/R37 script. It binds the observed LocalLab login and
movement to the **same 127.0.0.1 session endpoint**, the same registered
`WorldPlayer`, and an active game socket owned by the launched guest client
Java process. No automated typing or production credentials are involved.

## Prerequisites and execution mode

All R23–R27 preconditions must already pass inside the *actual*
network-disabled Windows Sandbox guest: R25 writable private home, R26
guest-owned server/loopback receipt, exact pinned R11 certified airgap v308
JAR, isolated static cache, private JDK11, server SHA, current guest routing
and localhost checks.

The default script still performs **plan only**; no new automatic startup:

```powershell
& 'C:\SPKR23\assets\guest-gui-window-smoke.ps1' -GuestHome '<R25 guest HOME>'
```

Only after separate operator authorization *inside that guest*, the
manual-login trial may be invoked:

```powershell
& 'C:\SPKR23\assets\guest-gui-window-smoke.ps1' -GuestHome '<R25 guest HOME>' -Launch -CaptureFrame -ManualLoginMovement -ManualProofSeconds 120 -ExactApproval 'LAUNCH R27 GUEST V308 GUI WITH LOCAL SERVER' -ManualApproval 'VERIFY R38 GUEST SYNTHETIC LOGIN AND MOVEMENT'
```

The script presents instructions to the operator to type only the
synthetic username `opensrc`, use a **disposable** password in this private
guest, and manually walk one tile after logging in. Never type a
production/live SpawnPK or RuneScape password. The harness does not
synthesize GUI keystrokes, inject client inputs or send game packets.

## Exact observable requirements

R38 starts only the R27-owned server and the certified airgap client
inside the preflighted Sandbox guest. Only for this opt-in mode, it
asynchronously captures the owned server's **stdout and stderr** into
new guest-only files under the disposable R25 HOME. The 5 MiB-bounded,
read-only server stdout parser requires all of the following in order:

1. First `CLIENT_PACKET seq=1 opcode=185 len=2 widget=912`.
2. `V512_WORLD_REGISTER playerId=<id>` from the **same session peer**.
3. `LOGIN_SUCCESS_LOCAL rank=205 ... persistent=true` from that peer,
   indicating the disposable persistent `opensrc` profile was admitted.
4. `M5_MOVEMENT_REQUEST MovementRequest{opcode=164,...} action=ACCEPTED`
   from that same peer.
5. `[world player=<id>] M5_AUTHORITATIVE_TICK mode=WALK ...`
   with a plausible actual tile displacement.

It also requires a current `Get-NetTCPConnection -State Established`
entry whose **OwningProcess is the exact GUI Java child PID**, whose remote
address is **127.0.0.1**, and whose remote port is **43594**. The R37
foreground-owned window image must be captured and hashed. No success
receipt is created unless both test-owned client/server processes stop
cleanly, the owned server output streams have completely drained, and
both loopback server ports have been released.

Final guest-only receipt:
`r38-guest-login-walk-receipt.json`, and a SHA256-pinned
`r38-local-server-stdout.log`; both remain **inside the guest's private
HOME**. R37's own screenshot and receipt remain separate.

## What this can and cannot certify

A successful real guest trial would be **strong evidence** that the
**certified airgap *derivative*** GUI sent a local 317/v308-style login,
reached LocalLab, caused a first accepted walk and remained visible.
It would not establish production authentication, original **unmodified**
v308 client compatibility, complete scene/OpenGL rendering quality, PvM,
bank, reconnect, or any original production account authorization. A
single server trace and PID-owned socket are not cryptographic packet
provenance, which the JSON receipt makes explicit.

No such trial has yet occurred. Connected Windows host has no available
Windows Sandbox executable; the optional-feature query requires
elevation. **R38 does not install or enable Windows Sandbox**, change
Windows Firewall, start the game directly on host, or terminate any
owner-Java v312 client.

## Offline, safe verification

- `test_r38_guest_manual_login_movement.py` checks static approval,
  guest gating, owned client socket, byte-level marker and evidence policy
  without executing Java.
- `test_r38_guest_login_trace.ps1` extracts only the **pure bounded parser**
  function from the pinned guest script. On Windows it exercises a synthetic
  in-temp log with positive and negative scenarios (wrong widget, wrong peer,
  wrong WorldPlayer, nonpersistent account, rejected walk and reordered
  markers) without any GUI, Java or live account.
- Native Windows PowerShell parser and both host-execution denial modes are
  required CI preconditions.
- Linux and Windows GitHub CI exercise these alongside inherited R27/R37
  and LocalLab server test suites.

This work is stacked as a **draft unmerged PR** on R37, pending CI and a
future explicitly authorized isolated GUI acceptance. No guest-run result
is fabricated.
