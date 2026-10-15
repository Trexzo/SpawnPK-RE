# Chat 2 G18.6.7 — file-backed COMPLETED G4 timestamp provenance: raw disk vs reconstructed World

Date: 2026-10-15.

**Certified cumulative parent**: Chat1/Chat2 [G21.205 + G18.6.6 #2646](https://github.com/Trexzo/SpawnPK-RE/pull/2646) exact SHA `3673fa0b81713e515ee9f53988b9ff8dcd93a9e7`, [GitHub Actions #38725404483](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38725404483) SUCCESS, **513/513 unique focused**, Java11 bytecode major55 and full Gradle. This branch is additive test-only and remains draft/unmerged.

## Gap covered beyond G18.6.6

G18.6.6 [#2644](https://github.com/Trexzo/SpawnPK-RE/pull/2644) verified a valid COMPLETED Slayer snapshot with source assigned77/transition91 reconstructs as a new World task with replay-clock timestamps, using only an in-memory extension copy. Existing `LocalLabSlayerPersistenceTest` verifies basic real FilePlayerRepository save/load without asserting these original-vs-runtime temporal fields. **G18.6.7 adds a REAL explicit file-backed repository roundtrip while preserving these distinctions.**

1. Isolated World A: legitimate Monster Hunter task starts at tick77; real installed Monster Spawner NPC death at tick91 completes G4. G4 state is codec-valid COMPLETED progress1/completions1/source-assigned-tick77/source-transition-tick91; G1 records one real World receipt and coin settlement. Test explicitly calls `FilePlayerRepository.save(PlayerSnapshotCodec.capture(account, ownerA))` into a temporary folder, then `load` to confirm a physically persisted account snapshot; World A closes.
2. Isolated World B with the SAME `FilePlayerRepository`: load the account file, call **`PlayerSnapshotCodec.applyValidated`** on a fresh WorldPlayer before registering. Confirm its G4 namespace equals EXACT source COMPLETED77/91 bytes. First `LocalLabSlayerRuntime.status(account)` reconstructs COMPLETED World task at the new replay clock for both assigned and transition fields, while the WorldPlayer's raw extension stays original. Independently read the account file again and apply to an unregistered probe WorldPlayer to verify **the disk still contains source77/91**, not replay-clock metadata. No fake G1 NPC death/receipt, no ground items in replay World.
3. Legitimate G4 renewal at tick113 writes ACTIVE0/completions1/source-assigned-tick113/source-transition-tick-1 into B's owned extension. The original disk file STILL contains COMPLETED77/91 before explicit publication. Only after a **second explicit `FilePlayerRepository.save`** does `load` return ACTIVE113/-1 and count1; World B has no G1 receipt or ground drop.

Marker: `G1867_COMPLETED_SLAYER_FILE_ROUNDTRIP_PROVENANCE_PASS`.

### Authority and scope

This is stronger **real on-disk** file serialization validation than G18.6.6, but writes are **explicit test-owned repository API calls**. It does **NOT** exercise an authenticated TCP LocalSession, real async World final save, opt-in G21.205 JVM witness, review ACTIVE sidecar retirement, crash/power-loss postimage, cross-JVM concurrency, or original SpawnPK timestamp/rewards/Slayer rules. Reconstructed internal G4 completed state is **not a legitimate NPC event**, and no external consumer/ground grant follows. Do not treat this as account recovery or a durable event outbox.

Exactly **three files** vs #2646: new `G1867SlayerCompletedFileRoundtripTemporalTest.java`, one focused entry plus both fixed-count guards in `server/build.gradle` **513→514 unique focused**, and this document. ZERO production Java, core G21.205 checkpoint logic, other gameplay classes, main/client/default flags changed. No duplicate G18.6.6 test import.

**Fresh exact-head CI REQUIRED**: G1867 marker, G1866/G21205/G21184 markers, 514/514 focused, Java11 major55, full Gradle. DRAFT/unmerged; Chat1 can directly adopt this small additive branch after own hosted success rather than recreating it. [Gameplay outbox #2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587), [unknown original kill authority #2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558), and account ownership #2540/#2559 remain unresolved.