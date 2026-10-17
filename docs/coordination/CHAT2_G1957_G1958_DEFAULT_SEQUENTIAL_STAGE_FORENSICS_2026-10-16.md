# Chat 2 G19.5.7/58 — observational default sequential consumer-stage trace

**Certified gameplay parent:** [#2820](https://github.com/Trexzo/SpawnPK-RE/pull/2820), exact `d892ce6af194f4d3118ac5de66a2bbfe06552188`, **677/677 hosted + Java11 major55 + full Gradle**. Parent contains source G1951–54 and 131-death bounded observer history tests G1955/56.

## Actual source gap

In the **default** `LocalLabMonsterSpawnerProvisioning.creditTerminalProgression`, G4 (Slayer), G14 (Daily Challenge), G15 (Task Scrolls), G16 (Adventure) and G18 (Daily Money Making) execute sequentially. One throwing stage stops the remaining callbacks. The new G1951 runtime-level `TerminalObserverAttempt` correctly reports `THREW` but has no stage identity or indication of whether G15/16/18 were even reached. The opt-in G18.8.x stage isolation diagnostic is *not* enabled by default and **does not preserve fail-fast semantics**. Turning it on to obtain diagnostics would change gameplay continuation behavior.

## New production module

`LocalLabSequentialTerminalStageTrace` is one World-local **read-only** forensic module, bounded to 128 reports. The actual installed default observer creates an invocation-local capture after G1 and canonical ground settlement, delegates the original five consumer callbacks in their original order, and records the capture in a finally block even if a callback throws. It stores:

- Exact canonical terminal `npcId`, `deathTick`, original recipientRef, spawn-recipient generation witness or -1;
- For G4/G14/G15/G16/G18 one of `RETURNED`, `THREW`, `NOT_REACHED`, `SKIPPED_NO_CURRENT_RECIPIENT`;
- Exception **class name only** on a throwing consumer; no Throwable reference, stack trace, account objects, reward handles, or applied-grant claims.

All five stage callbacks are the same existing methods in the same order, not replacement policy implementations. A stage `THREW` is *not* evidence that its side effects were rolled back; `RETURNED` does *not* mean an inactive objective was credited. The trace does not suppress or replay the original exception, does not reconfigure the optional isolation mode, and records **zero default-stage reports** in isolation mode. `LocalMonsterSpawnerActivationRuntime.sequentialTerminalDiagnosticSnapshot()` exposes only an immutable detached view; no write, apply or replay API. Existing `creditTerminalProgression(World,...)` callers retain the old signature.

## Real installed graph regressions

Both use the existing genuine G1884 test graph, active G15/G16/G18 consumers, a truly spawned Monster Spawner NPC, actual lethal finalization/ground settlement and its installed G1+progression observer.

- **G1957:** corrupt only G14's TEST-ONLY persistence namespace. Default sequential G4 returns, G14 throws `IllegalStateException`, **G15/G16/G18 are NOT_REACHED**. Overall observer THREW, G1 and ground settlement remain once. Exact source/death/recipient generation and immutable snapshot asserted.
- **G1958:** leave G14 healthy. Default G4, G14, G15, G16, G18 all return; previously activated G15/G16/G18 progress once, and no fictional active G14 reward is asserted. Overall observer RETURNED, same exact terminal identity and one drop.

The preexisting G1884 test with both normal and opt-in isolation is retained, so G1954's isolated stage-failure behavior must still pass; no stage-replay grant is allowed.

Focused manifest **677→679 unique** and two dedicated JavaExec regressions. Mandatory hosted exact-head **679/679**, G1957/G1958 markers, Java11 major55, full Gradle. Draft, `main` untouched.

**Unresolved:** [#2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587) still requires durable event identity, immutable attribution provenance, per-consumer idempotent state mutations and transactionally committed ACK/reconciliation. This diagnostic history is not the outbox or an authority to retry grants. Original SpawnPK server kill-credit authority remains [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558) unknown.
