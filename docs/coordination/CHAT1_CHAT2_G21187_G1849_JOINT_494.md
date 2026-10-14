# Chat 1 + Chat 2 G21.187 and G18.4.9 — joint test integration

## Exact independently certified inputs

- **Certified joint parent**: [Draft PR #2580](https://github.com/Trexzo/SpawnPK-RE/pull/2580), SHA `6e31c8b4aab4b9bf67e6a0b80756836539e0296f`, [Actions #38557125092](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38557125092) SUCCESS Java11 major55 **494/494** focused, full Gradle 7m50s. G21.187 is NEGATIVE evidence of account stale-BOOTSTRAP relogin after a force-killed JVM with no completed final save. It does NOT provide durable quarantine/recovery.
- **Certified Chat 2 additive test**: [G18.4.9 Draft PR #2577](https://github.com/Trexzo/SpawnPK-RE/pull/2577) exact SHA `031bd37f6ad3e1166d3e0f66db4bf0ead55748e9`, [Actions #38556879916](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38556879916) SUCCESS Java11 major55 **492/492** focused, full Gradle 7m56s. Its `G1849_MONSTER_SPAWNER_RESPAWN_NAME_REUSE_PASS` established LocalLab's delayed NPC respawn across same-username generations as a negative observation; neither original server respawn policy nor a mitigation is proven.

## Exact composition

- Base exactly on the certified **494**-focused G21.187 joint parent above. Import the exact Chat 2 G18.4.9 compiled test file `G1847MonsterSpawnerNameReuseTerminalAttributionTest.java` and its handoff note from Chat 2's certified ref.
- **No new focused class entry or Gradle task**: G18.4.9 extends the already registered G18.4.7 test. Count stays **494 unique**, not 495.
- Do not touch production Java: `LocalSession.java`, `World.java`, `LocalJvmAccountSessionLease.java`, `LocalCrossJvmAccountSessionLock.java`, gameplay G18.4.x production service files, and all existing G21.187 test/Gradle registration must remain exact.
- The earlier CI-red Chat 2 draft head has been superseded by the corrected exact SHA noted above; use **only** that certified test blob. The aggregate `no-current-owner` control is now source-constrained rather than using unrelated global World event counts.

## Fresh integrated certification required

- Exact-head hosted GitHub Actions SUCCESS on THIS new combined source tree.
- Java11 bytecode major55; `G21187_KILLED_JVM_PASS`, `G1847_NAME_REUSE_TERMINAL_ATTRIBUTION_CHARACTERIZATION_PASS`, `G1848_PVM_RECORD_USERNAME_REUSE_CHARACTERIZATION_PASS`, `G1849_MONSTER_SPAWNER_RESPAWN_NAME_REUSE_PASS`; **494/494 current-train focused**, full Gradle SUCCESS.
- PASS means negative behavior characterized, not repaired. Neither OS file lock nor its JVM-token wrapper is crash-durable after force-kill, does not protect raw/uncooperative writers, and is DEFAULT OFF.

Keep Draft/unmerged. No generic account durable intent/automatic recovery, original client GUI or historical server reward-policy claim, settlement/grants/replay/cleanup/rollover/pruning/unlink/durable ACK. Core design [#2540](https://github.com/Trexzo/SpawnPK-RE/issues/2540), [#2559](https://github.com/Trexzo/SpawnPK-RE/issues/2559) remain OPEN. Frozen R25 #1847/G21.87/G21.88 unchanged; Chat 3 client untouched. Shared [#2526](https://github.com/Trexzo/SpawnPK-RE/issues/2526).
