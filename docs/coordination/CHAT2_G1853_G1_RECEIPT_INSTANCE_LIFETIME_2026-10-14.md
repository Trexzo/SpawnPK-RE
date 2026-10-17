# Chat 2 G18.5.3 — G1 PvM kill receipts are bound to the PvmRecordService instance

Date: 2026-10-14. Source: [G21.190 + G18.5.2 joint draft #2594](https://github.com/Trexzo/SpawnPK-RE/pull/2594) exact `4211f07162c2bb494db23fa625856d0f711fb3f6`, hosted [Actions #38565757908](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38565757908) **SUCCESS**, G21190/G1852, Java11 major55, **497 focused**, full Gradle.

## Why this is a separate reliability boundary

`PvmRecordService.recordIfCurrent` keeps applied terminal-event identities in a private in-memory `LinkedHashMap<KillIdentity,Receipt> receipts`, with `KillIdentity=(EntityId npcId,long deathTick)`. The per-player `WorldPlayer.snapshotExtensions()` `pvm-record` namespace serializes version/authority/**kill count**, but **does not include these receipt identities**. The checked current player/generation lock prevents old-generation mutation; it does not establish the old terminal event was already delivered if a different `PvmRecordService` instance is used.

G18.5.2 proved duplicate event delivery to the same original G1 service is deduplicated while manual repeated aggregate G18 progression increments. G18.5.3 checks the additional lifecycle dimension: **what if the G1 consumer service object is reconstructed with an empty receipt map?**

## Actual controlled test — explicitly NOT a JVM restart

Extend the existing permanently checked `G1847MonsterSpawnerNameReuseTerminalAttributionTest` (no manifest changes):
1. Register exact WorldPlayer/generation, configure the **actual unmodified LocalLab provisioning graph**, and complete one canonical NPC kill at tick 101. Original production terminal observer emits one G1 kill and one G1 receipt, one ground item.
2. Direct duplicate `world.pvmRecords().recordIfCurrent(player,generation,npcId,101)` returns `replayed=true,applied=false`, kill count stays 1.
3. Construct **`new PvmRecordService(world)`** while reusing the exact SAME World and WorldPlayer. Fresh service sees the `pvm-record` player namespace's kill count 1, but its new instance has **zero terminal receipt entries**.
4. Pass same `npcId,deathTick` to the new service's real `recordIfCurrent` API. It recognizes a currently owned player but **not prior delivery**, applies the same kill again and updates the shared namespace kill counter to 2, with one new instance receipt. A subsequent duplicate to this new service is then correctly rejected as replay.
5. Confirm the original NPC stays finalized, existing ground item remains unchanged, and no terminal observer is invoked again. Emit `G1853_PVM_RECORD_SERVICE_RECEIPT_LIFETIME_PASS`, including explicit `JVMRestartOrFilePersistenceTested=false`. All G1847/48/49/G1850/G1852 markers retained.

**This is a synthetic service-replacement/consumer-only replay over one live WorldPlayer; NOT an actual process crash, new World, disk reload, or a claim that the real server naturally replaces its PvmRecordService in mid-session.** The kill count is present in the player extension namespace, but whether or how it is safely flushed/restored across real process restarts is **not tested** here. This shows per-instance G1 receipts are insufficient alone for a future durable outbox or replay protection after consumer reconstruction. Original SpawnPK kill-credit attribution, receipt identity and recovery semantics remain `UNKNOWN_SERVER_AUTHORITY`.

## Coordination and gate

Only the existing checked test class plus this note. **No production Java, persistence, account admission, protocol, Gradle or focused list changes** (still **497 unique**). Future scoped `CUSTOM_LOCALLAB` per-consumer receipt persistence/replay/outbox acceptance tracked at [issue #2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587), without implementing an original-server rule. Original kill authority [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558), World/account durable recovery [#2540](https://github.com/Trexzo/SpawnPK-RE/issues/2540), [#2559](https://github.com/Trexzo/SpawnPK-RE/issues/2559) unresolved.

Require fresh exact-head hosted `G1853_PVM_RECORD_SERVICE_RECEIPT_LIFETIME_PASS`, G1852, G1851, G21190, Java11 major55, **497/497** current-train and full Gradle. This draft is on Chat 1's cumulative G21.190 source; no duplicate merge of older Chat 2 slices, no changes to frozen R25. Chat 1 owns promotion.
