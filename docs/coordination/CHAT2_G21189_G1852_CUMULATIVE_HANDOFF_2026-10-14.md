# Chat 2 G21.189 + G18.5.2 — exact minimal cumulative overlay

Date: 2026-10-14.
**Core parent** [Chat 1 joint G21.189 + G18.5.1 #2590](https://github.com/Trexzo/SpawnPK-RE/pull/2590) head `e2df6ee1e58dfb4049f45a1dbf0a36492bd4d7dd`, on previously certified [Chat 2 G21.188 + G18.5.1 #2588](https://github.com/Trexzo/SpawnPK-RE/pull/2588) SHA `cbbebcc8e8135f9de0dae9d6edb74d87ba86e8bd`. G21.189 adds a DEFAULT-OFF read-only existing forced-review-intent marker TCP login veto. It is not automatic creation or recovery, nor a durable crash-reentry fix.

**Gameplay source** [Chat 2 G18.5.2 draft #2591](https://github.com/Trexzo/SpawnPK-RE/pull/2591) head `204e499398e773ecc875e9ee7bd0238a7065a5fa`, stacked on exact #2588. The new controlled test uses an **actual G1 consumer instance with the actual World / MonsterSpawnerService / executor / PvmRecordService**. A **test-only substituted** combined terminal observer invokes real G1 then throws deliberately before running later G4/G14/G15/G16/G18 progression. It verifies one G1 receipt/kill recorded and one ground drop settled, G18 objective still zero, terminal callback not replayed on retry, and no duplicate drops. This does **not** demonstrate the installed combined callback naturally throws or a production G18 bug.

## Exact composition
1. Start with Chat 1 #2590 exact tree, retain `LocalReviewIntentAdmissionVeto.java`, `LocalSessionPlayerInitializer.java`, `LocalJvmAccountSessionLease.java`, real socket G21189 test and **entire** `server/build.gradle` exact SHA. **496 unique** checked focused tests unchanged.
2. Replace only `server/src/spk/local/G1847MonsterSpawnerNameReuseTerminalAttributionTest.java` with exact Git blob `9c10b60ef489b9f9834e4bea3a2d48d50229f347` from G18.5.2. Existing G1847 checked test class already in manifest, so no new build configuration.
3. Add original G18.5.2 source note `docs/coordination/CHAT2_G1852_PARTIAL_TERMINAL_DELIVERY_2026-10-14.md` and this new joint handoff. **Exactly three files**.
4. No production gameplay, account/session, persistence, grant, reward, settlement or client source changed. Do not merge predecessor Chat 2 G18.5.1 / G18.5.0 / G18.4.9 branches separately if this cumulative is accepted.

## Independent verification
Standalone [#2591](https://github.com/Trexzo/SpawnPK-RE/pull/2591) and core [#2590](https://github.com/Trexzo/SpawnPK-RE/pull/2590) each have hosted CI to verify their OWN exact SHAs. **Fresh combined exact-head** Java11 major55, `G1852_PARTIAL_TERMINAL_CONSUMER_DELIVERY_PASS`, G1851/G1850/G1849/1848/1847, G21189/G21188/G21187, **496/496** focused, full Gradle success required for combined certification. Do not inherit predecessor success.

**Claims:** `CUSTOM_LOCALLAB` synthetic partial-terminal failure only, no original SpawnPK server rewards or recovery semantics. Original kill authority [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558) unresolved; per-consumer event outbox issue [#2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587) open; crashed-account durable admission [#2540](https://github.com/Trexzo/SpawnPK-RE/issues/2540) / [#2559](https://github.com/Trexzo/SpawnPK-RE/issues/2559) open. No main merge or frozen R25 release changes.
