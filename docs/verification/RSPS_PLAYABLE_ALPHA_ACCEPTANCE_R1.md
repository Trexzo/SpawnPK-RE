# RSPS playable-alpha acceptance — R1

## Authority and status
Source baseline: canonical draft #2962 at `20b703d8702c4a437945ee0cede868c97fac61bc`.
Account-safety work #2963 remains a separate draft; **do not assume it is integrated**.
This is a **verification plan**, not a claim of runnable GUI, original 179 packet parity, complete game fidelity or public release.
Pinned original v308 JAR SHA-256: `854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6`.
Historical v307 hash is not an acceptable substitution.

## Existing entrypoints and provenance
- `RUN_CURRENT_RELEASE_ACCEPTANCE.ps1 -V308ClientPath <absolute-path>`: existing exact-Git-head, clean-worktree guarded owner-local release gate. This is the **only** route for claiming current release acceptance; do not invent a new all-green marker.
- `scripts/Build-V308LocalClients.ps1`: pinned-JAR client preparation.
- `scripts/Check-WindowsReleaseEvidence.ps1`: informational evidence only; absence of v308 blocks 179/GUI verification.
- `scripts/Run-R13AssetAcceptance.ps1`: assets-specific verification; not a gameplay proof.
- `server/build.gradle`: current-train focused tests. As of baseline #2962, **911** focused tests were previously certified, *not* live GUI play.
- Issues #1106 (blocked-scenery Walk-here), #427 (v308 app/UI), #1862 (loadout editor), #9 (custom cache/assets), #2540/#2559 (account safety), #2587/#2558 (PvM delivery/recipient) remain separate unresolved acceptance scopes.

## Owner-machine preflight (no side effects)
1. Select the exact source commit; check `git rev-parse HEAD`, `git status --porcelain`, and pin all binary inputs by SHA-256 **before launching**.
2. Confirm the JAR is v308 exact; do not copy a historical v307 or a newly built client into the pinned evidence slot.
3. Confirm Java and build tools and the expected LocalLab startup scripts exist. Collect `git status` and timestamps after verification.
4. Execute existing release acceptance using its documented parameters and capture the **full logs**, including spawned-server PID, built-server JAR hash, bind port, client profile identity, cleanup, and current head. A successful source-only Windows CI is insufficient.
5. If client/GUI input is absent, mark cases **BLOCKED_MISSING_PINNED_CLIENT**; never silently mark PASS.

## Scenario matrix
Every row requires a **real v308 client session** unless explicitly noted. Record actual packet evidence, server-side observations, video/screenshot when visual, account pre/post snapshots, failure message, SHA and log segment. Synthetic tests are useful controls but cannot replace this matrix.

| ID | Scenario | Required observations | Failure class / existing issue |
|---|---|---|---|
| A01 | Original pinned v308 launch and socket login | Correct client hash, login response, WorldPlayer generation, HOME scene rendered | #427 |
| A02 | HOME open-ground walk and minimap click | Both moves accepted; local and server paths agree | #1106 |
| A03 | Blocked scoreboard tile Walk-here | Nearest *legal* reachable tile, no wall/diagonal clipping | #1106 |
| A04 | NPC interaction and numbered dialogue options | Correct widget root, option opcode and single action | #427 |
| A05 | Bank deposit and withdrawal | Exact inventory/bank conservation; no duplicate or missing items | core bank |
| A06 | Shop buy and sell | Stock/currency conserved and UI refreshed | shop |
| A07 | NPC attack, lethal hit, death and ground drop | One canonical NPC finalization and immutable drop receipt | #2587 |
| A08 | Loot pickup with two client sessions | Ground ownership, inventory capacity and no duplicate pickup | ground |
| A09 | Slayer/Challenges/Scrolls/Adventures/DMM kill progression | Exact intended recipient, five separately verified consumers; no inferred original attribution | #2558/#2587/#2547 |
| A10 | Logout/reconnect and normal final save | Same account generation transitions and identical persisted inventory/stats | #2540/#2559 |
| A11 | Two players nearby + independent worlds/clients | Correct appearance and remote NPC/player updates; no stale entity | shared world |
| A12 | Trade propose, offer, accept, cancel, disconnect | Exact item conservation and no partial double commit | Trade lifecycle |
| A13 | PvP combat, death, and postdeath state | Damage/hitmarks and appropriate safe inventory state | PvP |
| A14 | Duel/Tournament participation | Registration, match lifecycle, cleanup and no leaked ownership | Duel/Tournament |
| A15 | Loadout editor and special roots | Target selection, retained prior UI state and correct packet ownership | #1862/#427 |
| A16 | Restart persistence | Account snapshot survives graceful restart; no stale successful reconnect | #2540/#2559 |
| A17 | Forced final-save error and rejoin attempt | **Negative acceptance**: refuse uncertain state; never claim repaired/acknowledged without proof | #2540 |
| A18 | Crash after NPC drop settlement, before downstream progression | **Negative characterization only** until durable per-consumer ACK/outbox exists | #2587 |
| A19 | Custom cache/graphics asset pipeline | Correct provenance and actual in-game render, no placeholder claim | #9 |

## Evidence classification
Each result must use exactly one of:
- `PASS_REAL_V308` — reproduced on pinned real client and source/JAR evidence captured.
- `PASS_SYNTHETIC_ONLY` — Java/loopback test, **not** alpha gameplay pass.
- `FAIL_REPRODUCED` — evidence and minimal steps saved.
- `BLOCKED_MISSING_PINNED_CLIENT`, `BLOCKED_RUNTIME`, or `NOT_RUN`.

Track date, exact source head, v308 client SHA, Windows build/JAR SHA, local server port/PID, username pseudonym, World generation, test ID, expected/actual observation, log pointer and disposition. Do **not** store secrets, passwords, account credentials or full session tokens in evidence.

## Alpha gate and engineering sequencing
Minimal **single-player playable alpha**: A01–A08, A10, A16 pass with real-client evidence; A03 is a mandatory scene-specific regression. Minimal **multiplayer alpha** additionally requires A11, A12 and safe reconnect under failure. Do not promise a public server until A09, A17, A18 and the appropriate security/durability design have an approved disposition.

Fix the *first actual launch or live interaction failure* before adding new game modes. Keep #2962 canonical and #2963 separate; produce independently reviewed improvements with focused tests and exact-head CI. No auto-merges from this checklist.
