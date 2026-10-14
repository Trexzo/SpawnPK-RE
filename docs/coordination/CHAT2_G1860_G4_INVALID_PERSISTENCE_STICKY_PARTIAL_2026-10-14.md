# Chat 2 G18.6.0 — soft-invalid G4 persistence remains cached while real G1/G18 terminal deliveries continue

Date: 2026-10-14.

**Exact parent:** [certified joint G21.198+G18.5.9 #2622](https://github.com/Trexzo/SpawnPK-RE/pull/2622), SHA `d2c3ca089545a5ba37a6169b96a7e2f7f414bd37`, [GitHub Actions #38598573806](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38598573806) **SUCCESS**, 505 unique focused, Java11 major55/full Gradle. Draft/unmerged: ancestor's hosted green does **not** certify this new test.

## New source-driven distinction

The installed LocalLab Monster Spawner observer directly chains G1, G4 Blood Slayer, G14, G15, G16, then G18 Daily Money Making. G18.5.9 exercised a **throwing G4 task/mode mismatch** that interrupted later consumers; G18.6.0 instead exercises G4's **nonthrowing fail-closed invalid persisted state**:

- `LocalLabSlayerRuntime.ensureRestoredOwned` catches invalid `LocalLabSlayerPersistence.decode` input, caches a failure against the exact `WorldPlayer` in `invalidPersistence` and marks it hydrated. `recordMonsterSpawnerFinalization` then returns a nonprogressing `KillCreditResult`, rather than throwing to the installed observer.
- Test-owned `WorldPlayer` activates legitimate G18 EASY DMM (0/3); injects malformed G4 snapshot extension **before first G4 hydration**; verifies `slayer.status` shows an invalid persistence error and no active task.
- Actual unchanged `LocalLabMonsterSpawnerProvisioning.create(world)` observer handles **one canonical NPC spawn/death/ground settlement**: G1 kill/receipt 1, G18 progress 0→1, one settlement receipt for one coin, G4 remains fail-closed. This is an actual installed observer path, not a manually repeated progress/kill call.
- Clear only the deliberately malformed G4 **persisted namespace**. The exact `WorldPlayer`'s in-memory invalid cache is intentionally **not** reset: `slayer.status` remains invalid. Run the genuine G1 World due-time respawn ticket to obtain a different live NPC, perform a **second independent canonical death**, and assert G1 kills/receipts 2, G18 progress 2/3, two distinct settlement receipts and total owner-scoped coin quantity 2 (ground stacks may coalesce), and G4 still rejects progress.
- There is no manual second-event replay, exception injection, direct reward grant, local state rehydration, account save/restart, automatic failed-credit retry, or original SpawnPK policy inference.

Marker: `G1860_G4_INVALID_PERSISTENCE_STICKY_PARTIAL_DELIVERY_PASS`.

## Reliability implication and limits

A **nonthrowing** consumer refusal can persist across distinct normal terminal deliveries even after the underlying invalid test bytes are repaired, while other consumers still progress. Neither `FinalizeStatus.FINALIZED` nor absence of an observer exception proves G4 was acknowledged. A future per-consumer outbox [#2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587) requires explicit consumer outcomes, durable identity/recipient-generation authority and an evidence-backed repair/replay policy. Original server kill-credit authority [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558) is UNKNOWN_SERVER_AUTHORITY.

This is a controlled **CUSTOM_LOCALLAB test-only malformed snapshot fixture**, not an externally reachable exploit or proof that invalid data occurs in actual account files. G4 is intentionally not offered a new live task; the test does not assert all intermediate G14/G15/G16 counters or any rewards. No production code or persistence policy is modified, and this does not authorize automatic repair or grants.

**Exactly 2 files**, the existing G1847 permanent terminal-regression class plus this note; zero production Java, Gradle/manifest/account/prototype or client modifications. 505 focused entries unchanged. **Fresh exact-head hosted CI** must pass G1860/G1859/G1858/G21198 markers, Java11 major55, 505/505 focused and full Gradle. Remain DRAFT/unmerged; Chat 1 owns final integration and promotion; frozen R25/main untouched.

**Ground-item identity nuance:** `GroundItemRegistry.addBatchDetailed` merges matching owner/item/tile stacks, so `groundItems().size()==2` is **not** a valid assertion for two independent deaths. The test checks one settled coin in each distinct NPC's settlement receipt and total owner-scoped coin amount `2` across current ground stacks instead.
