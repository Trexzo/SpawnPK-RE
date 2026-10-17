# G19.5.1/52 — in-JVM post-settlement terminal observer diagnostics

**Exact parent:** [Chat 2 canonical mask batch PR #2813](https://github.com/Trexzo/SpawnPK-RE/pull/2813) commit `e6597476a91899db85f6705886ce5b051e4e8aa1`, **671/671 hosted focused + Java11 major55 + full Gradle certified**. The parent includes G1947–50 current-client shared canonical hitmask delivery/batch controls on Chat 1 cumulative G21.278 account and World source. Source in this PR changes only `MonsterSpawnerPvmRuntime.java`, a test fixture, two tests, Gradle and this note. No account code modified.

## Source-backed issue #2587

`MonsterSpawnerPvmRuntime.settlePending` commits `NpcDropGroundSettlementService.settle`, removes its exact NPC entry and invokes its installed `TerminalObserver` **after** the drop is committed. A thrown observer exception (including `Error`) is intentionally logged and swallowed, but no in-memory queryable record of this exact post-settlement observer outcome existed. A subsequent `retrySettlement(npcId)` sees `NOT_OWNED`, so one consumer may have been credited while another has not. Blind replay would be unsafe because a failed callback may mutate arbitrary state before throwing.

## Minimal production phase: bounded observational records, not replay

- Add a World-local, bounded **128-entry** `TerminalObserverAttempt` ring on the actual `MonsterSpawnerPvmRuntime`, not a new global service.
- Each immutable record contains exact canonical `npcId`, `deathTick`, source finalization `recipientRef`, immutable spawn recipient generation (or -1 when absent), `RETURNED/THREW`, and **exception class name only**, never a retained Throwable or WorldPlayer reference.
- A synchronized `terminalObserverDiagnostics()` returns a detached, read-only snapshot of the ring plus saturating total/failure/eviction counters. Observation runs after the external observer without retaining the runtime monitor through callbacks.
- Preserve existing callback error containment and log marker, existing ground drop commitment and existing no-replay semantics. A normal return is **not** proof that any specific G1/G4/G14/G15/G16/G18 objective was credited. A throw is **not** proof that no consumer changed.

## Executable real-graph tests

**G1951:** actual `MonsterSpawnerPvmRuntime` + `NpcLifecycleService.applyDamageOwned` lethal 10 HP, finalizer, ground settlement and installed successfully returning observer. Verify one 995 coin-stack amount 5, exact NPC teardown, `RETURNED` record including source/death/recipient generation, immutable returned snapshot and zero duplicate observer or drop on `retrySettlement` / `finalizeIfOwned`.

**G1952:** same real graph twice, with explicit `IllegalStateException` and `AssertionError` injected **after** settlement into the observer. Both failures remain nonfatal to the already settled NPC/drop; each is captured as `THREW` with its exception class, exact death identity and counters. No second callback/ground-drop on retry attempts. These tests do not install the complete G1+G4/14/15/16/18 progression consumers; those per-consumer failures need separate idempotency contracts.

Manifest **671→673 unique**, two JavaExec test gates. Exact-head hosted **673/673**, G1951/G1952 pass markers, Java11 bytecode major55 and full Gradle mandatory before certification. Draft, `main` unchanged.

## Next phase (not implemented)

Issue [#2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587) remains **OPEN**. Durable replay requires a persistent death-event identity tied to a World epoch, immutable spawn recipient/attacker authority, and per-consumer idempotent mutation + transactionally committed acknowledgments. Never trigger automated reward delivery from the diagnostic ring. Test partial failure after G1/G4, reconnect, restart, and World close only after defining each consumer's idempotent apply/ACK behavior. Original SpawnPK server kill credit remains [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558) unknown.
