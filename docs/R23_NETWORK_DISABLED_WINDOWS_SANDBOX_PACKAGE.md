# R23 — review-only Windows Sandbox offline client package

**Purpose:** Move the next original-v308 graphical acceptance effort away from
host-Java-only firewall program rules. Use a disposable Windows Sandbox guest
with `<Networking>Disable</Networking>` and only **read-only** host mappings
for a small, SHA-pinned LocalLab server/airgap client bundle and a
dedicated R13 JDK11 snapshot.

This is a **package generator and manual guest preflight**, not an executable
one-click client acceptance test. It does not enable any Windows feature,
change any firewall rules, start Windows Sandbox, spawn Java, invoke Minecraft,
or modify the three existing SpawnPK owner clients.

## Why not just two Windows Firewall rules?

R22 examined actual pinned v308 bytecode. It found calls to
`ProcessBuilder.start`, `Runtime.exec`, and native library loaders, as well
as 41 bundled native-library entries. Static references do **not** prove
those methods execute during startup. But firewall rules scoped only to
`java.exe` and `javaw.exe` are insufficient to prove a child executable
cannot make its own outbound network connection.

Microsoft documents that Windows Sandbox networking is **enabled by
default**. A custom `.wsb` file may disable it; mapped folders can be
configured read-only, and protected client mode adds AppContainer isolation.
See:
- https://learn.microsoft.com/en-us/windows/security/application-security/application-isolation/windows-sandbox/windows-sandbox-configure-using-wsb-file
- https://learn.microsoft.com/en-us/windows/security/application-security/application-isolation/windows-sandbox/windows-sandbox-sample-configuration

## R23 inputs and host preparation

`tools/verification/r23_windows_sandbox_offline_package.py` validates:

- An exact SHA-pinned Git checkout of `Trexzo/SpawnPK-RE` with tracked
  execution source clean and the expected GitHub origin.
- A server JAR at the canonical checkout `server/build` path, with its
  independently measured SHA256 supplied by the operator.
- The **R11 certified v308 airgap derivative** SHA256
  `024fad774453430bb964076b98d460a6dae821ee31100322d104e30d9a9c97a7`.
  The proprietary **unmodified** original-v308 client is not placed in the
  guest.
- A separately owned R13 private Java11 JDK snapshot, with exact
  `java.exe` SHA256
  `b2484c2889365b93fb48434e12c8f9bc21ca6cd5d4eb2eae39a46b17ced3c8ef`,
  and `javaw.exe` SHA256
  `0a4656c826032ffe28d58ebfa93e85d4c099c50a1857f29416461848f0c6adaa`.
  The live v312 client JDK is not eligible.
- An existing absolute output parent outside user home and repository
  source subtree, with symlink-ancestor validation.

`--plan-only` validates these prerequisites and does not create a
bundle. The normal command creates one **unique** temporary output
directory. It copies just the SHA-checked airgap JAR, the LocalLab server JAR,
a read-only guest preflight script, and a minimal manifest of SHA pins.
It does NOT copy or share actual player accounts, real user caches, original
client binaries, browser profiles, credentials, or other host directories.

A `.wsb` file is generated separately, mapping:

| Guest path | Host folder | Access |
|---|---|---|
| `C:\SPKR23\assets` | Unique R23 staging `assets` only | Read-only |
| `C:\SPKR23\jdk` | Pinned private R13 Java11 snapshot | Read-only |

Other hardened options: `Networking=Disable`, `VGpu=Disable`,
`AudioInput=Disable`, `VideoInput=Disable`,
`ClipboardRedirection=Disable`, `PrinterRedirection=Disable`,
`ProtectedClient=Enable`. **There is no LogonCommand**; simply opening
the WSB file cannot automatically start Java or a game.

This reduces host exposure but also forces software graphics (WARP), which
could make the game's rendering slower or incompatible. No guest graphics
compatibility is claimed.

## Real authorized Windows host evidence — 2026-11-08

- Source server Git tree for R23 matched prior R20
  `2a4c3924b48e32fc684179a78253228d4c973769`.
- The independently built server JAR was reused only after the exact tree
  match and SHA256
  `b282147d3e74e32c0587ffa04e47183fb39d17a384562b880421a30a918c3a45`.
- Synthetic package/mapping negative controls and the native Windows
  PowerShell guest-script parser passed.
- Real `--plan-only` with exact SHA-pinned files returned
  `R23_OFFLINE_SANDBOX_PACKAGE_PREFLIGHT_PASS`.
- The real generator produced
  `R23_OFFLINE_SANDBOX_PACKAGE_PREPARED_NO_LAUNCH`, with two read-only
  folders, network disabled, clipboard disabled and no LogonCommand.
- The Windows Sandbox feature is **not currently installed** on this
  connected PC. A non-elevated session cannot enable it, and R23 neither
  attempts nor requests an administrative Windows change.
- Three existing user Java sessions remained running throughout.

These are **host-side source and package checks only**. Since a guest has
not run, the WSB configuration has not been tested in an actual virtual
machine, and there is no verified guest TCP loopback or original GUI session.

## Only after a guest exists: manual guest preflight

The staged `guest-preflight.ps1` requires the Windows Sandbox guest's
`WDAGUtilityAccount` and validates its mapped asset hashes against the
provided immutable manifest. It refuses any active network adapter or
IPv4/IPv6 default route and checks that 127.0.0.1 TCP can connect to
itself. **Route inspection errors are failures**, not evidence of
no networking. It prints a receipt only when these checks pass.

No server or game is launched by this script. It is intentionally a
precondition, not permission for an automatic GUI start. User-name checking
is a heuristic, not a hypervisor security attestation; the guest must
actually be started from the reviewed WSB with networking disabled.

## Remaining acceptance gates

- Enable Windows Sandbox only through a separately authorized host
  administration workflow if desired. Do not alter v312 client processes
  or existing host firewall profiles.
- Run the no-network guest preflight and verify **guest** network isolation
  and 127.0.0.1 loopback operation. VM boundary, guest networking and
  protected-client effects must be observed directly.
- Stage *sanitized, login-free client cache fixtures* inside the guest
  without mapping the original user's profiles. R23 intentionally does
  not include this material.
- Only then design a **separately authorized** guest-local server + client
  launch and genuine login/movement/bank/PvM/reconnect acceptance. Current
  R10 host live-client fence still blocks launching another client directly
  on the host; its existence should not be bypassed with another host JVM.
- No authenticated graphical v308 run, original production mechanics
  parity or full OS/VM side-channel-isolation certification is claimed.

R23 remains a separate draft PR stacked on verified R22, unmerged.
