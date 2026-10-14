# Chat 2 G21.189 + G18.5.2 — minimal consumer replay evidence on real TCP review veto

Date: 2026-10-14. Draft/unmerged.
**Core parent:** Chat 1 [G21.189 + G18.5.1 #2590](https://github.com/Trexzo/SpawnPK-RE/pull/2590), exact `e2df6ee1e58dfb4049f45a1dbf0a36492bd4d7dd`, on hosted-green [G21.188 + G18.5.1 #2588](https://github.com/Trexzo/SpawnPK-RE/pull/2588) SHA `cbbebcc8e8135f9de0dae9d6edb74d87ba86e8bd`.

**Gameplay source:** Chat 2 [G18.5.2 #2591](https://github.com/Trexzo/SpawnPK-RE/pull/2591) corrected branch. Its **first unverified** draft attempted to replace the installed observer, which is illegal under `MonsterSpawnerPvmRuntime.installTerminalObserver`'s single-install invariant. The corrected test is now **fully independent of observer replacement**. It executes real installed LocalLab terminal finalization ONCE and then performs an explicit **test-only manual replay** of actual G1 and progression consumer APIs.

## Exact composition

- Begin from exact Chat 1 #2590 tree, preserving **all production** Java, `LocalSessionPlayerInitializer.java`, `LocalReviewIntentAdmissionVeto.java`, real socket G21189 test and **entire `server/build.gradle` byte-for-byte**. Focused manifest remains **496 unique**.
- Overlay **only** the corrected `server/src/spk/local/G1847MonsterSpawnerNameReuseTerminalAttributionTest.java` from #2591 plus `docs/coordination/CHAT2_G1852_PARTIAL_TERMINAL_DELIVERY_2026-10-14.md` and this handoff (3 paths).
- The tested existing G1847 class already belongs to the current-train focused suite. No new test class/task or business policy.

## New negative replay property

Actual unmodified `LocalLabMonsterSpawnerProvisioning.create(world)` observer processes a canonical NPC kill with exactly **one G1 PvM record receipt** and a tracked **G18 Easy objective from progress 0 to 1**, plus one committed ground item. After terminal delivery, **TEST-ONLY** manually duplicate the same finalization to:
1. `world.pvmRecords().recordIfCurrent(player,generation,npcId,deathTick)`. The G1 receipt detects replay, does not increment kills and leaves one receipt.
2. `LocalLabMonsterSpawnerProvisioning.creditTerminalProgression(world,recipientRef,definitionId,deathTile,deathTick)`. This aggregate G4/G14/G15/G16/G18 path lacks shared event-delivery dedup and G18 objective progresses **1→2** on the same NPC event.

A runtime retry still returns `NOT_OWNED`; **production does not automatically replay** these consumers. The test demonstrates *asymmetric idempotency of distinct real consumer API surfaces*, relevant if engineering a future scoped `CUSTOM_LOCALLAB` terminal outbox under [#2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587). It does not establish that any installed consumer naturally throws, nor original SpawnPK transaction/credit semantics.

## Core and verification boundaries

Chat 1 G21.189 adds a DEFAULT-OFF veto for a **pre-existing** forced ACTIVE review sidecar on exact real TCP login path; it does **not publish intents** when sessions are admitted, retire them on durable final save, or repair G21.187 killed-JVM stale BOOTSTRAP admission. Existing standalone G21188 sidecar primitive is not a complete recovery solution. Original kill attribution [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558), durable account safety [#2540](https://github.com/Trexzo/SpawnPK-RE/issues/2540)/[#2559](https://github.com/Trexzo/SpawnPK-RE/issues/2559) remain open.

Require **fresh corrected exact-head** hosted `G1852_TERMINAL_CONSUMER_REPLAY_ASYMMETRY_PASS`, G1851/G1850/G1849/G1848/G1847, G21189/188/187, Java11 major55, **496/496** focused and complete Gradle before certification. The first invalid G1852 build does not certify the revised source. Chat 1 owns promotion, and predecessor Chat 2 source slices must not be imported twice. No main or frozen R25 change.
