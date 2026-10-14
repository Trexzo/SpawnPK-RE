# Chat 2 G18.5.2 — consumer replay asymmetry with the ACTUAL installed terminal observer

Date: 2026-10-14. Parent [G21.188 + G18.5.1 #2588](https://github.com/Trexzo/SpawnPK-RE/pull/2588) exact `cbbebcc8e8135f9de0dae9d6edb74d87ba86e8bd`, hosted [Actions #38561270563](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38561270563) SUCCESS, 495 focused + Java11 55/full Gradle.

## Source-backed question for issue #2587

The installed `LocalLabMonsterSpawnerProvisioning.create(world)` terminal observer executes G1 `PvmRecordService.recordIfCurrent` and then `creditTerminalProgression` (G4/G14/G15/G16/G18) after canonical ground settlement. The G1 record service has an `(npcId,deathTick)` receipt map and rejects duplicates as `Receipt.replayed`; the aggregate progression path delivers name-based events to multiple consumers without a shared per-consumer terminal-delivery ledger. A future outbox/retry must not simply send the same event to all consumers again.

## Actual test sequence (NO observer replacement)

Add to the already checked `G1847MonsterSpawnerNameReuseTerminalAttributionTest`:

1. Register an exact WorldPlayer/generation, start G18 Easy objective at 0.
2. Instantiate **real unmodified** `LocalLabMonsterSpawnerProvisioning.create(world)` with its installed G1-plus-G4/G14/G15/G16/G18 combined observer **left in place**.
3. Open/select/activate Monster Spawner, spawn canonical definition1 NPC, kill it once, run its **actual finalization/settlement/callback**. Expect `FINALIZED`, one ground drop, one G1 kill+receipt, G18 progress exactly **1**.
4. AFTER that completed terminal event, deliberately simulate **consumer-only duplicate delivery in the test** by calling (a) the **same real** `PvmRecordService.recordIfCurrent(player,generation,npcId,deathTick)` API with identical kill identity and (b) the real `LocalLabMonsterSpawnerProvisioning.creditTerminalProgression(...same finalization fields...)` aggregate API exactly once again.
5. Characterize the difference: G1 receipt says `replayed=true, applied=false`, kill count remains 1 and receipts remain 1, but G18 objective advances from **1→2**. Ground loot stays at one committed item; retrying already-finalized NPC still yields `NOT_OWNED`, so the runtime is NOT performing replay itself.
6. Emit `G1852_TERMINAL_CONSUMER_REPLAY_ASYMMETRY_PASS`, while keeping all earlier G1847–G1850 markers.

**This is a test-only MANUAL consumer replay, not current installed observer automatically replaying or failing.** It is exact evidence that a naive future outbox re-delivery across different consumer APIs has asymmetric idempotency and can double-credit G18 in the current `CUSTOM_LOCALLAB` code. It is NOT an original SpawnPK transaction policy, not evidence of exploitation, and not a live fix. It also does not establish complete replay behavior for every other progression consumer; G18 and G1 are asserted.

## Correction from first unverified test draft

The initial G18.5.2 draft tried replacing the terminal observer after `LocalLabMonsterSpawnerProvisioning.create`. **That was invalid:** `MonsterSpawnerPvmRuntime.installTerminalObserver` is single-install and correctly rejects a different observer. The first draft's hosted test must NOT be treated as certification. This corrected test preserves that ownership invariant and uses the real combined observer. PR body and exact-head CI are being updated accordingly.

## Coordination and gate

Only existing covered test class plus this docs note; **no production source, account/session code or Gradle changes**, 495 unique focused unchanged. Track future **CUSTOM_LOCALLAB** idempotent per-consumer outbox in [#2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587); original reward authority [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558) remains unknown. Chat 1 G21.189 admission veto separately remains DEFAULT OFF and does not yet create intent markers or safely recover killed-JVM mutations.

Fresh hosted exact-head `G1852_TERMINAL_CONSUMER_REPLAY_ASYMMETRY_PASS`, G1851/G1850/G1849/G1848/G1847, Java11 major55, 495 focused, full Gradle required. Chat 1 owns any promotion. Draft/unmerged.
