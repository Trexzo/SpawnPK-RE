# Chat 2 G18.5.7 — installed terminal observer partial success after G1 PvM INVALID_STATE

Date: 2026-10-14.

**Exact certified Chat 2 parent:** [G18.5.6 #2607](https://github.com/Trexzo/SpawnPK-RE/pull/2607), `d0654ae17656e0aadcb40590fbffc1039570ed6b`, [Actions #38584749001](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38584749001) SUCCESS. 500 focused checks, Java11 major55/full Gradle. Chat 1's later G21.194 integration [#2609](https://github.com/Trexzo/SpawnPK-RE/pull/2609) independently stacks on #2607 and owns core promotion; avoid changing its integration branch or importing duplicate gameplay blobs.

## New real-observer negative regression

The installed `LocalLabMonsterSpawnerProvisioning.create(world)` observer invokes `G1MonsterSpawnerPvmLoopService.onFinalized`, then `creditTerminalProgression` for G4/G14/G15/G16/G18. `G1MonsterSpawnerPvmLoopService.onFinalized` calls `PvmRecordService.recordIfCurrent(...)` but discards the returned `Receipt`; a rejected PvM record does not prevent its subsequent respawn schedule or other progression consumers.

G18.5.7 sets up a genuine isolated WorldPlayer, activates the G18 DMM easy objective, then installs a deliberately **invalid** `pvm-record` snapshot namespace (version mismatch). It uses the **actual installed observer** to spawn one canonical NPC, perform one lethal transition, and finalize the real ground settlement.

Expected characterization:

- G1 still has **0 kill credits and 0 receipts**. An explicit diagnostic `recordIfCurrent` call for the same event returns `INVALID_STATE` with no mutations.
- The unchanged G18 DMM consumer nevertheless increments objective progress **0→1**, the ground drop settles once, and G1's delayed ticket produces a new NPC when the actual World event queue runs at `deathTick+5`.
- No additional callback/replayed death is injected. This isolates **partial consumer success** in the real terminal pipeline as distinct from the earlier test-only G18.5.2 manual replay.

Test marker: `G1857_G1_INVALID_PVM_STATE_PARTIAL_TERMINAL_PASS`. Scope: **CUSTOM_LOCALLAB** controlled corrupt-extension fixture, *not* evidence of invalid data from the real SpawnPK client, original server behavior, automatic outbox replay, durability, or a production fix. The observer does not throw here; it silently continues after a rejected **G1 record receipt**. Unverified G4/G14/G15/G16 state is not asserted.

### Engineering consequence

Any future LocalLab [#2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587) replayable terminal event/outbox must capture **per-consumer completion**, not assume one installed observer pass means all downstream G1 and G18 consumers committed. Original server reward authority [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558) remains unknown; the experimental account review mode in core G21.194 is unaffected.

Only two changed files vs certified G18.5.6: existing Java test class and this handoff. ZERO production Java, zero Gradle manifest changes; **500** focused unique tests unchanged. Fresh exact-head GitHub Actions Java11 bytecode/full Gradle required before certification; draft/unmerged and no main or release changes.
