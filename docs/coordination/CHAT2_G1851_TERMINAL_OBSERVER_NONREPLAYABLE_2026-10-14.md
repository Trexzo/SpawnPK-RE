# Chat 2 G18.5.1 — terminal observer failure AFTER drop settlement is non-replayable

Date: 2026-10-14.
Parent: Chat 2 [G18.5.0 #2583](https://github.com/Trexzo/SpawnPK-RE/pull/2583), exact `db32f6afb37767412dbe3f71d3eb086dad09c358`, stacked on hosted-green [G21.187 + G18.4.9 #2581](https://github.com/Trexzo/SpawnPK-RE/pull/2581), `77f3bd54745e8694268941c4fec04869fa5f8bc8`.

## Source fact — independent of original SpawnPK policy

In `MonsterSpawnerPvmRuntime.settlePending`, after `NpcDropGroundSettlementService.settle` succeeds under World-open ownership, the runtime removes the NPC's `entries` map entry **BEFORE** invoking `TerminalObserver.onFinalized`. The callback is guarded with `try { observer.onFinalized(terminal); } catch(Throwable failure) { System.err.println(...) }`, and the method still returns `FinalizeStatus.FINALIZED`. Therefore any thrown terminal consumer error occurs after the canonical drop has committed and after the NPC runtime entry is gone; `retrySettlement(npcId)` and `finalizeIfOwned(npc)` subsequently see NOT_OWNED, so there is no built-in observer replay for that terminal event.

The real LocalLab terminal callback invokes G1 PvM records then G4 Blood Slayer, G14 Daily Challenges, G15 Task Scrolls, G16 Adventures and G18 Daily Money Making. A failure within that callback could leave **partial derived progression** without a replayable terminal callback; this is a **source-level potential** requiring an actual injected consumer failure to verify per consumer. This regression only injects a synthetic throwing terminal observer into the actual runtime, so it proves the runtime contract but not particular consumer failures in the installed provisioning graph.

## Test-only extension

Add one method to existing checked `MonsterSpawnerPvmRuntimeTest` (no new manifest entry):
- Install a terminal observer that increments a counter, validates the exact `FINALIZED` and settlement result, then deliberately throws `G1851_SYNTHETIC_TERMINAL_OBSERVER_FAILURE`.
- Spawn a canonical NPC, apply lethal damage, run actual finalizer, and assert `FINALIZED` returns without propagating the observer failure, exact one installed ground drop, drop resolver once and entry permanently removed.
- Invoke `retrySettlement(npcId)` and duplicate `finalizeIfOwned(npc)`; both must return `NOT_OWNED`, observer callback count remains 1, and no new ground item appears.
- Emit `G1851_TERMINAL_OBSERVER_FAILURE_NONRETRYABLE_PASS`, retaining the preexisting `MONSTER_SPAWNER_PVM_RUNTIME_PASS`.

**No production changes**, no claim to original SpawnPK server reward/transaction semantics or automatic recovery. G18.4.7/8/9 and G18.5.0 existing tests remain; focused manifest remains **494**.

## Future engineering/policy split

Original SpawnPK server attribution, guaranteed progression and post-settlement transaction policy remain `UNKNOWN_SERVER_AUTHORITY`. Engineering remediation, if desired for **CUSTOM_LOCALLAB**, needs a durable/idempotent terminal event/outbox or transactional replay design, exact player generation policy, recovery semantics, and explicit acceptance tests; never simply re-invoke drop settlement after committing. This test does **not** implement that. Keep [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558) kill-credit origin and [#2540](https://github.com/Trexzo/SpawnPK-RE/issues/2540) account durable admission open.

Require fresh exact-head GitHub-hosted Java11 bytecode major55, G1851 and G1850/G1849/G1848/G1847 markers, core G21187, **494/494 focused**, full Gradle. Chat 1 reviews/promotes the cumulative draft, with frozen R25 unchanged and Chat 3 client recovery separate.
