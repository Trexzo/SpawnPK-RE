# Chat 2 G18.4.9 — delayed G1 Monster Spawner respawn across same-name player generations

Date: 2026-10-14. Owner Chat 2 gameplay.
Parent: [Chat 1 + Chat 2 G21.185 cumulative #2575](https://github.com/Trexzo/SpawnPK-RE/pull/2575), exact `67fc549a6740b92992309392db33fdab453fd62b`, hosted [Actions #38551420416](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38551420416) **SUCCESS** (492 focused; Java11 major55; full Gradle).

## Source-backed seam

`G1MonsterSpawnerPvmLoopService.onFinalized` resolves `session.ownerRef` to a **current** `WorldPlayer` by username, then schedules a delayed `Ticket` storing `ownerRef`, `recipientRef`, NPC/death fields and an expected *MonsterSpawnerService session snapshot*, **not** a `WorldPlayer` + generation. In `run(ticket)`, after the due tick, it again resolves the **current username**, verifies only current ownership, and calls `spawner.activateIfCurrent(ticket.ownerRef,ticket.expectedSession, ...)`, followed by `executor.execute(ticket.ownerRef)`.

The expected spawner session snapshot can remain identical while a disconnected old player A is replaced by B using the same name. A successful delayed respawn could then create a canonical NPC for B from a ticket originating with A's NPC. This is an attribution/lifetime concern, **not proof of original SpawnPK policy** or necessarily a gameplay exploit.

## Test-only extension on certified G21.185

Extend the already checked `G1847MonsterSpawnerNameReuseTerminalAttributionTest`, which now also includes G18.4.8 G1 PvM record checks:
- Original A registers under user U, activates CUSTOM_LOCALLAB Easy PvM, opens/selects/activates real Monster Spawner session and spawns NPC carrying immutable A/generation.
- Unregister A and register B with same U; actual installed LocalLab terminal finalizer settles synthetic lethal damage at tick 41. G18.4.7 expects B gets DMM progress and G18.4.8 expects G1 PvM record attributed to B, while original stamp remains A.
- Drive the real WorldEventQueue at the known G1 scheduled due tick `41 + RESPAWN_DELAY_TICKS`. If the old session ticket automatically respawns a new NPC, require it to have a **different canonical NPC id** with **new spawn-time witness B/generation**, while A's original NPC identity stays old.
- Control has **no owner at terminal**: the G1 loop should not have scheduled a respawn, and executing due events should create no new NPC. An inspection user is reattached only after terminal delivery.
- Emit a secondary marker `G1849_MONSTER_SPAWNER_RESPAWN_NAME_REUSE_PASS` if this source-observed behavior is reproducible. Existing G18.4.7/G18.4.8 markers must remain true.

**Additional stricter scenario:** A remains registered while the first NPC dies at tick 54 and the G1 terminal callback schedules the delayed respawn ticket. Assert post-terminal session has no NPC and is inactive, then **only after the ticket exists** unregister A and register B under the identical account. Drive `WorldEventQueue.runDue(54 + RESPAWN_DELAY_TICKS)` and inspect the newly spawned canonical NPC for an exact B/generation witness. This distinguishes an existing **old-session scheduled ticket crossing a reconnect** from the weaker case where B was already present at terminal delivery.

**Hosted first-run fixture correction:** [Actions #38553540273](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38553540273) FAILED because the no-recipient control asserted `runDue(...)==0`, but `WorldEventQueue.runDue` counts **all World scheduled tasks**, not just this G1 respawn. It now asserts the relevant state instead: no tracked respawned NPC and the original finalized NPC absent. This is a test-only assertion correction, not a production behavior change.

**No new class or manifest entry**; focused remains **492**. No production files, business rules, player/session code, network, or persistence modified.

## Nonclaims and future authority

This describes current **CUSTOM_LOCALLAB** automatic respawn semantics. The original SpawnPK server rule for scheduled respawn across logout/reconnect is **UNKNOWN_SERVER_AUTHORITY**. Do not disable/recredit to old/new generation without source evidence or an expressly chosen LocalLab policy. Existing G1 account registration, G4/G14/G15/G16/G18 credit, progression and drop settlement are unchanged. Chat 1 G21.185 experimental file lock remains default-off/cooperative; not crash-durable or a complete multi-process writer fence.

Exact-head hosted Java11 bytecode major55, G18.4.7/8/9 markers, **492** focused and full Gradle need verification before any certification. This is a draft/unmerged Chat 2 child; Chat 1 owns merge/promotion. Open [#2547](https://github.com/Trexzo/SpawnPK-RE/issues/2547), [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558) remain open.
