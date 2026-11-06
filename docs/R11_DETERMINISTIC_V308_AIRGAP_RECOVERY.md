# R11 — Reproducible private v308 airgap recovery

**Status:** exact v308 client derivatives rebuilt and independently hash-verified on Windows. **No graphical-client acceptance performed.**

## Why R11 was needed

R10's existing airgap candidates were SHA256 `9ff1b80fe81b1af2e174df71db33f5aeb5ee489c265ab2880bcda35f7797019b`, which did not match the independently certified v308 airgap SHA256 `024fad774453430bb964076b98d460a6dae821ee31100322d104e30d9a9c97a7`. No hash check was bypassed and no user's existing files were overwritten.

An older owner-controlled `Trexzo/SpawnPK-Src` checkout contains the original deterministic v308 client variant builder at `tools/runtime/build_v308_local_clients.py`. This source-only builder was imported into R11's `tools/runtime/` after inspecting its deterministic ZIP_STORED implementation, fixed original input SHA256, pinned class-level preimages/postimages, and strict canonical-output publication policy. Its Windows CRLF source SHA256 was `6db991b3982b19a7ac11c1efc77a0ab6b21c0fe1f5d085cbb02c00bac7cc7cc8`. Neither original nor patched proprietary client JAR bytes were committed.

## Reproduced SHA256 identities

| Artifact | SHA256 |
|---|---|
| Unmodified original v308 | `854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6` |
| Deterministic localhost derivative | `15ceb89669ddfe0a666e65b4a5291705af692a50e479bbde17e751eceb7fd23e` |
| Deterministic airgap derivative | `024fad774453430bb964076b98d460a6dae821ee31100322d104e30d9a9c97a7` |
| Generated v308 patch manifest | `279c31de900a10b9e62d14ee3d7b06a95395baa6dfddaa65c9f9ec7a19092bfa` |

Builder output verifies exactly two changed class files for localhost and eleven for airgap; unrelated JAR entry order and payload bytes stay unchanged. The custom static verification reports no known original SpawnPK/AWS endpoint strings in the certified airgap class-file authority. This does NOT substitute for a runtime egress capture or OS firewall.

## Safe private rebuild

From a clean, current R11 checkout with Python 3.10+, run:

```powershell
python tools/verification/r11_recover_v308_airgap.py --original-v308 'C:/path/to/exact/v308-client.jar' --private-parent "$env:LOCALAPPDATA/Temp"
```

The tool checks the input SHA256, creates a unique new private directory, copies its versioned builder **into that directory**, and generates `local-client/client-airgap.jar`, `client-localhost.jar`, and a manifest there. The strict canonical-output guard inside the builder therefore applies to the new private tree, not any existing user LocalLab source or client directory.

On success the wrapper rechecks the original, both complete derivative SHA256 values, strict manifest fields, ZIP entry order, every declared original/modified class SHA256, and all untouched resources. It prints `R11_PRIVATE_V308_AIRGAP_RECONSTRUCTION_PASS` and saves `r11-reconstruction-receipt.json` in the owned directory. On invalid input it fails closed. It never runs game Java, injects code, changes live accounts, or terminates existing game processes.

## Windows verification — November 6, 2026

The owner-controlled Windows machine produced two independent successful rebuilds. The first used a copy of the original deterministic builder from the older source repository in a new temporary tree, with `V308_LOCAL_CLIENT_FINAL_VERIFY_PASS`. The second used the **GitHub-committed** builder and new R11 wrapper, producing `R11_PRIVATE_V308_AIRGAP_RECONSTRUCTION_PASS`. Both produced the exact same four SHA256 identities shown above. The second run also independently checked class-level pre/post transformations and re-read the original input.

A separate private `SpawnPK-RE` Git branch checkout was cloned from GitHub with the correct origin and clean tracked source. Existing `SpawnPK-LocalLab-Git` user modifications and the two active v312 Java game processes were left untouched.

## Remaining acceptance barriers

The recovery fixes the **missing certified airgap artifact** issue but is **not** a successful original-client GUI session. Before any GUI PASS, build a version-matched server in the separate clean checkout, prepare a correctly seeded isolated `user.home`, preserve users' existing live v312 sessions, and collect actual graphical login/movement/bank/NPC HP/loot/XP/logout-reconnect and egress evidence. `RUN_ALL_LOCAL_LAB.ps1` is not a safe substitute because its historical behavior may stop existing game/server processes. Original production SpawnPK PvM formulas/drop tables, Windows positive save, fsync crash durability, concurrent account ownership, and G21.171 remain unresolved.
