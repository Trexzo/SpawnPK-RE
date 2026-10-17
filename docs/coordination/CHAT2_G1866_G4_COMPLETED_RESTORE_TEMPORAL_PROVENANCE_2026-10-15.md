# Chat 2 G18.6.6 — valid COMPLETED Slayer snapshot replay-time metadata and renewal

Date: 2026-10-15.

**Certified parent:** [G18.6.5 #2639](https://github.com/Trexzo/SpawnPK-RE/pull/2639), exact SHA a0015196c68f0cf77c38dddd08074fa6ca7c8fc1, [Actions #38722498548](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38722498548) SUCCESS, 507 focused unique / Java11 / full Gradle. The independent G21.203+G18.6.5 [#2641](https://github.com/Trexzo/SpawnPK-RE/pull/2641) 510-focused combined head is subject to its OWN hosted CI and is not this branch's source parent.

## New independent COMPLETED-state temporal provenance contrast

1. In isolated World A, a legitimate Monster Hunter task starts at tick77. The genuine installed Monster Spawner observer processes one canonical NPC death at tick91. The source-owned namespace decodes valid COMPLETED progress1/goal1, assigned77, transition91, completion-count1, with one G1 kill/receipt and coin ground settlement.
2. In independent fresh isolated World B, register another WorldPlayer with the same name and **test-only copy exactly the valid source COMPLETED extension before first G4 hydration**. LocalLabSlayerRuntime.ensureRestoredOwned first allocates a task with the new World clock, then replays a synthetic task completion through SlayerTaskService.recordValidatedKill at that replay tick. The reconstructed runtime task is COMPLETED with both assignedTick and transitionTick equal to B's replay clock, despite raw copied namespace remaining original source77/91; completion-count1 is preserved. No World B NPC death, G1 event receipt or ground settlement occurs.
3. A legitimate explicit G4 Monster Hunter renewal at tick113 in B resets completed objective and writes an ACTIVE0 task with new source-assigned-tick113 / source-transition-tick -1, preserving completion-count1. A's original COMPLETED77/91 extension remains unchanged. No phantom reward replay.

New exact marker: G1866_G4_COMPLETED_RESTORE_REPLAY_TIME_PROVENANCE_PASS.

## Evidence boundaries

This is CUSTOM_LOCALLAB semantic characterization only: no actual FilePlayerRepository save/load roundtrip, authenticated TCP login, JVM restart, original SpawnPK time/Slayer policy, external exploit or crash recovery. The completed-state replay is an in-memory task reconstruction and is NOT an authoritative server event; do not count its synthetic SlayerTaskService progress as a new NPC kill, player reward or outbox receipt.

## Diff, coordination and acceptance

To avoid further inflating the permanent 100k+ character G1847 terminal characterization, G18.6.6 uses its OWN dedicated test class G1866SlayerCompletedRestoreTemporalProvenanceTest.java. Exactly 3 files: new Java test, one focused manifest entry in server/build.gradle **507 to 508 unique**, this documentation. ZERO production Java, G21.203 core/account, default flags, client or main changes.

Fresh exact-head hosted CI REQUIRED: G1866 and retained G1865/G1864 markers, 508/508 focused, Java11 major55 and full Gradle. DRAFT/unmerged; future cumulative Chat1 should import ONLY this new class, one manifest line and scope note, not reapply already integrated older G1847 tests. No ACTIVE marker retirement. [Gameplay outbox #2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587) and [original reward policy #2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558) remain open.