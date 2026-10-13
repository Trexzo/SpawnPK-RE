# Chat 1 / G21.173 — exact simulated socket reconnect across full World restart

**Parent:** certified [G21.172 Draft PR #2524](https://github.com/Trexzo/SpawnPK-RE/pull/2524), exact `9ac2a8dfc8e024f9ef63d9a23520aa2bb29d6b9f`, [Actions #38436723067](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38436723067), Java11 major55 and focused 472/472/full Gradle SUCCESS.

## Why it matters

The existing source includes `HandshakeIntegrationTest` (wire-level login), `SharedWorldSocketIntegrationTest` (two sessions and ticking), and `DualProfileSocketIntegrationTest` (two simultaneous local profile saves). There was no single exact-wire integration that *completely shuts down the first World*, persists the account, constructs a new World, and signs into the **same saved account profile** in the fresh World. This is essential RSPS core-server playability evidence and a separate concern from the Chat 2 gameplay/content pipeline.

## Acceptance surface

`G21173CoreSocketReconnectAfterWorldRestartIntegrationTest` uses **real loopback ServerSocket and Socket**, actual `LocalSession`, ISAAC opcode185/widget912 post-login, a genuine per-test private account disk directory (no live `.spawnpk` writes), and sequential independent World instances. Verify first World login, WorldPulse tick, logout, profile saved/readable; fully close original World; new World receives same canonical alias and reads persisted profile, starts its own ticks, logs out and saves; secondary profile never spuriously allocated; thread executors and JVM publication leases end.

This uses a **synthetic exact-format login client**, not the original v308/v309 GUI binary or a rebuilt clean-source client. A regression PASS is **not** proof of rendering, asset compatibility, complete gameplay or any historic original-server rule. Server process executable `Main` listener ownership is covered by separate smoke scripts but this test creates a real socket LocalSession directly.

**Target:** Java 11 major55, focused 473/473, exact-head hosted full Gradle, Draft/unmerged. Keep certified G21.172 as last known pass until hosted exact head.

**Chat 2 (no copy/paste):** standing issue [#2526](https://github.com/Trexzo/SpawnPK-RE/issues/2526) includes this core/session boundary and exact links. Chat 2 owns client-visible NPC/bank/combat/gear and must not conflate this synthetic transport test with a full playable real-client milestone.

No World production behavior, persistent account schema or original raw save policy is changed in this regression. No settlement, cleanup, rollover, pruning, unlink, grant, replay, session release or ACK. Historical signer/provenance and atomic crash epoch remain unproven.
