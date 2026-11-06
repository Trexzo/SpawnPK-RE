# R13 — Windows-owned server runtime and fail-closed GUI egress boundary

**Verified on authorized Windows, November 6, 2026.** A real Java server was started, both loopback endpoints responded, port-owning PID was checked, and only the script-owned server child was terminated. Separately, a private JDK11 runtime was hash-snapshotted. **No graphical game client was launched.**

## R13 source tools

- `tools/verification/r13_windows_server_smoke.py` starts exactly one Java server child from a SHA-pinned, clean, official `Trexzo/SpawnPK-RE` checkout and the expected compiled Java11-bytecode JAR. It refuses occupied ports, noncanonical JARs, wrong source revision/origin/dirty tracked files and wrong Java version. The server receives a fresh private user.home and works exclusively on 127.0.0.1:43594 (game) and 127.0.0.1:43595 (AUX). It tests game TCP, the AUX `/spk_live/versions.txt` response, and on Windows verifies both bound ports are owned by its own child PID. The HTTP client explicitly ignores system proxies. It sends termination only to the `Popen` child owned by this invocation, never to an existing v312 game client.
- `tools/verification/r13_prepare_private_jdk.py` snapshots an installed Java11 JDK as a new `spawnpk-r13-owned-jdk-*` directory in system TEMP with byte-for-byte SHA256 checks on each copied file and a second source verification. It requires non-symlink/reparse ordinary files and dirs, a bounded total file size, and the copied private `java.exe -version` must report Java 11. It never modifies the source installation, firewall or any game process. Failure removes only the newly owned output.
- `tools/verification/r13_windows_gui_egress_gate.py` is **read-only**. It requires the GUI Java binary inside precisely that private JDK snapshot, confirms no currently running Java process uses its executable path, and checks a **single exact Windows Firewall ActiveStore outbound program BLOCK rule** for all profiles, addresses, ports and protocols, with the firewall service running. Missing or unverifiable conditions yield `R13_WINDOWS_GUI_OS_EGRESS_GATE_BLOCKED` and exit code 2. It neither installs a firewall rule nor launches the GUI.
- `tools/verification/test_r13_windows_boundary.py` provides synthetic tests for bad digests, occupied foreign ports, child-only termination, shared JDK rejection, private snapshot cleanup, profile/no-rule failures, and explicit refusal to claim graphical acceptance. CI also runs the real isolated Java server smoke and negative controls.

## Exact Windows server smoke evidence

The source checkout was a fresh private R13 Git clone from official `Trexzo/SpawnPK-RE`. Its tracked `server` subtree object was identical to the R11/R12 source tree: `cd4668e08d7202b0a66f72bde95906c211ea7d9d`. The canonical Java11-bytecode JAR was copied and verified against independently Windows-built SHA256 `88ff92b4866c213c7c82411d97b0b22083dec38de71b30da73cf23b8c6a63ec4`.

Running the committed R13 server smoke returned `R13_OWNED_LOOPBACK_SERVER_SMOKE_PASS` on Windows. It confirmed both listeners were bound to 127.0.0.1, auxiliary version data was returned with SHA256 `bd53e218806f2278e809d422af53f4a940ed3681f6a2c7e844ad1158ef13dee9`, and Windows assigned both ports to the new child PID 6212. The only server process it stopped was its own PID 6212. A subsequent independent Windows inspection confirmed that PID had exited, game/AUX ports had zero listeners and both user v312 client PIDs 25468 and 15816 were still alive. This is **real Windows server runtime** proof, not original GUI acceptance.

## Verified dedicated Java11 snapshot

The authoritative installed Amazon Corretto Java 11 tree was snapshotted into an invocation-owned `%LOCALAPPDATA%/Temp/spawnpk-r13-owned-jdk-*` folder. The copy verified 502 files and 309,625,028 bytes.

| Receipt field | Value |
|---|---|
| Result | `R13_PRIVATE_JAVA11_SNAPSHOT_PASS` |
| Private Java executable SHA256 | `b2484c2889365b93fb48434e12c8f9bc21ca6cd5d4eb2eae39a46b17ced3c8ef` |
| Full file inventory SHA256 | `de174d11e400f5a6fdacca626c5b09ceee8a6b8c9e14866531d6fb99fecb570b` |
| Original JDK modified | No |
| Firewall rules modified | 0 |
| Existing client processes stopped | 0 |
| GUI processes launched | 0 |

The specific private Java executable is not used by either existing v312 game process.

## OS-level egress admission — still BLOCKED

Running the R13 audit against the verified separate Java11 snapshot returned `R13_WINDOWS_GUI_OS_EGRESS_GATE_BLOCKED`: private executable PASS; live Java process collision check PASS (2 other Java clients inspected); exact Windows Firewall ActiveStore all-outbound program block MISSING/UNVERIFIED. The Windows account was not elevated, so no privileged firewall change was attempted. No bypass was added. Static client endpoint rewriting and loopback source code alone are not proof that every native/java network transport is restricted at runtime.

This is an additional safety check, not a replacement for R10's fail-closed running-game-process rule. Future graphical testing requires independently approved OS egress isolation, exact artifact checks immediately before launch, process-ownership and port gating, and an explicit non-destructive policy for existing v312 sessions. Do NOT run old `RUN_ALL_LOCAL_LAB.ps1` because it can force-stop other game/server processes. Do not treat the new private JDK snapshot as a firewall-enabled executable until the OS rule is independently installed and verified.

## Explicit nonclaims

R13 does not certify any graphical v308 login, bank, movement, HP, combat, loot, XP or reconnect, nor does it prove original SpawnPK PvM rules, crash-safe Windows saves, account lease concurrency, or G21.171 readiness. The PR stays stacked, draft and unmerged. Private client data remains in the earlier R12 HOME; neither existing v312 client was interrupted.

## CI

At final commit, require full GitHub Actions Java build, all R0–R12 regressions, R13 test suite, a real isolated server child smoke, and negative controls rejecting missing egress rule and missing Java11 input. No GUI pass can be inferred from CI.
