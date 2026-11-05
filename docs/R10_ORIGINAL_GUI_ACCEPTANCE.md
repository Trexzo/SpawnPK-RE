# R10 original-client GUI acceptance gate

**Status:** read-only admission preflight implemented. **No actual GUI acceptance yet.**

R0–R9 are real Java server tests using synthetic revision317 clients. Real
graphical acceptance is separate.

## Exact client pins

- Unmodified original SpawnPK **v308**: SHA-256
  `854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6`.
  Its original external endpoints may be live. Do not launch it as LocalLab
  just because its hash matches.
- Certified v308 **airgap patched derivative**: SHA-256
  `024fad774453430bb964076b98d460a6dae821ee31100322d104e30d9a9c97a7`.
  This is a different client JAR, NOT original bytes.
- Server: exact Trexzo/SpawnPK-RE Git SHA, clean tracked files, exact
  `server/build/SpawnPKLocalServer.jar`, independently pinned JAR SHA,
  embedded metadata, Java 11 bytecode (major 55).
- Runtime: Java 11/17/21, separate already seeded user.home with .spawnpk,
  no existing SpawnPK client Java processes and game/AUX loopback ports
  43594/43595 free. Any failed condition blocks launch.

## Running the read-only audit on Windows

Use Python 3.10+ from a clean, exact SHA-pinned R10 checkout. Example invocation
with required command arguments (fill all placeholder paths/hashes):

```text
python tools/verification/r10_original_gui_preflight.py --repo C:/clean/SpawnPK-RE --source-head FULL_GIT_COMMIT_SHA --server-jar C:/clean/SpawnPK-RE/server/build/SpawnPKLocalServer.jar --server-sha256 INDEPENDENT_SERVER_JAR_SHA256 --original-jar C:/reference/v308-client.jar --airgap-jar C:/reference/client-airgap.jar --java-exe C:/Java11/bin/java.exe --client-home C:/isolated/seeded-profile
```

Exit 0: `R10_ORIGINAL_GUI_PREFLIGHT_READY` (**not** a GUI PASS).
Exit 2: `R10_ORIGINAL_GUI_PREFLIGHT_BLOCKED`. Every check is reported.

It never launches, kills, modifies, downloads or injects a game process and
contains no bypass flag. Do NOT call the historical
`RUN_ALL_LOCAL_LAB.ps1` from it; that script can stop existing servers.
Use fake LocalLab credentials only; never the actual OS home.

## Windows evidence observed November 5, 2026

The authorized Windows device had two already running SpawnPK v312 Java
clients. They were left untouched. Its local `SpawnPK-LocalLab-Git` tracked
files had user changes, and both it and `SpawnPK-Src-Git` were on older heads,
not the R9/R10 SHA. The local `client-airgap.jar` digest was
`9ff1b80fe81b1af2e174df71db33f5aeb5ee489c265ab2880bcda35f7797019b`
for both directories, **not** the original pinned `024fad...` derivative.
Java 11/17/21 runtimes existed; ports 43594/43595 appeared free at that
inspection instant. These observations do not authorize a GUI launch.

## Acceptance remains outstanding

When verified matching artifacts are available, a **separately reviewed**
launcher can snapshot immutable JARs into a private directory, use a seeded
isolated user.home and synthetic credentials, and clean up only processes
that it created. Record the graphical client's actual login, HOME movement,
bank actions, HP/hits and respawn, ground-item pickup, XP, kill event,
logout/login restore; capture redacted GUI screenshots and server logs, and
record exact client/server hashes/PIDs/socket endpoints.

Synthetic CI, preflight READY or a window opening is not sufficient. Exact
historical NPC server mechanics, Windows safe-save, crash fsync durability,
concurrent account ownership and G21.171 remain unresolved.
