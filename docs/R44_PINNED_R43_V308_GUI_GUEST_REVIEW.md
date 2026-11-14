# R44 — R43 server and v308 graphical-client offline guest staging

## Goal and evidence

R43 has passed full Linux and Windows CI for two simultaneous synthetic v308-shaped TCP clients, player81 remote add/walk/remove, and player options104. The previously prepared R38 guest bundle still contained the older server build. R44 upgrades the **review-only guest staging**, preserving the client/cache/JDK and using the now-verified R43 LocalLab server instead.

The newly added tools/verification/r44_prepare_v308_gui_guest_review.ps1:

- On the Windows host, copies only existing, pinned inputs to a **new temporary destination**; refuses an existing destination, missing or hash-mismatched client/JDK/old and new server, unsafe WSB settings or missing original client audit.
- Keeps the certified airgap **derivative** v308 GUI client and exact 10,764 static cache files, and copies the exact R43 server JAR.
- Writes a new asset-pins.json containing R43 source commit and new server hash, and a new network-disabled Windows Sandbox .wsb with **two read-only mappings**, clipboard disabled, vGPU disabled, ProtectedClient enabled and **no LogonCommand**.
- Never starts any Java, network, Sandbox or GUI process, does not modify Windows optional features, firewall, original R38 guest assets, or real accounts.

### Verified connected-PC staging on 2026-11-14

Windows built this staging successfully at:

C:\Users\Felix\AppData\Local\Temp\spawnpk-r44-guest-v308-r43-v3-20261114

Review-only configuration:

SpawnPK-R44-NetworkDisabled-REVIEW-ONLY.wsb

Manifest:

r44-review-only-receipt.json

The package contains **10,774 files / 0.239 GiB**. Audit checks PASS for the following hashes:

| Artifact | SHA256 |
| --- | --- |
| Airgap v308 client derivative | 024fad774453430bb964076b98d460a6dae821ee31100322d104e30d9a9c97a7 |
| Private Java11 executable | b2484c2889365b93fb48434e12c8f9bc21ca6cd5d4eb2eae39a46b17ced3c8ef |
| Exact R43 server JAR | 9e080fde40d00ef8c565df19b117978b409c1c817c594cec9919512591e34363 |
| R43 source commit | 14858fde32d652a8057fee575d477cb0884e86ef |

The original R38 source server hash remains e311aed8a8f283bd964aa65d96c48cb4bb281c760d32e081d50942905a295949, unmodified. The first incomplete R44 scratch staging was removed only after the second staging succeeded, and both LocalLab host game/aux ports were free afterwards.

### Reproducing the review-only staging

Run from a detached checkout of R44, with a NEW unique Temp destination, using the explicit parameters SourceAssets, SourceWsb, GuestJdkRoot, ServerJar, ExpectedServerSha256, RepositoryHead and DestinationRoot. The server must be exactly the R43 digest above and the source commit must match. Inspect the returned JSON receipt and the output .wsb before any guest startup. The staging utility never launches anything.

## Precise blocker for the graphical session

On 2026-11-14, the connected host reported Windows 11 Pro build 26300, but System32/WindowsSandbox.exe was **absent**; read-only Windows Sandbox optional-feature status access failed without elevation. The processor reported VirtualizationFirmwareEnabled=False while Windows reported HypervisorPresent=True. These two observations **do not prove BIOS virtualization is disabled**; Windows feature/guest availability needs to be resolved by a separate explicitly authorized local administrative action.

**No Windows Sandbox guest, graphical original v308 or certified airgap derivative client was launched.**

After an offline guest becomes available and its networking/clipboard/read-only mappings are independently verified, follow the existing R23–R26 guest-only preflights to produce an isolated writable R25 HOME and R26 server receipt. The R38 guest-only GUI script (C:\SPKR23\assets\guest-gui-window-smoke.ps1) then provides a default plan-only mode. The explicit optional -Launch -CaptureFrame -ManualLoginMovement test requires TWO exact approval strings, a disposable guest-only account and password, a same-peer client opcode185/widget912 login and accepted opcode164 walk, an active socket owned by the launched client PID, and a reviewed screenshot. See docs/R38_GUEST_MANUAL_V308_LOGIN_WALK_ACCEPTANCE.md.

Even a future successful R38 guest test proves only the **airgap derivative** graphical login/walk, not the unmodified original client, full scene quality, NPC combat/loot/banking GUI journeys, or real production credentials. Never type real credentials.

## Actual non-GUI gameplay evidence from this task

On the same exact R43 Windows JAR, the established real-JVM R9 fixture passed with two genuine LocalLab NPC deaths, two earned owner-scoped ground drops collected and banked, and a second fresh JVM restoring **100,002 bank coins** and +60 Attack XP. Result: R9_CUSTOM_PVM_TYPED_NPC_KILL_OUTCOME_RECONNECT_PASS.

This proves a **CUSTOM_LOCALLAB synthetic client to real server gameplay loop**, not original SpawnPK combat formulas/drop rates, original v308 graphical-client gameplay, or power-loss durability.

R44 remains **DRAFT/STACKED ON R43/UNMERGED** pending CI and real guest availability.