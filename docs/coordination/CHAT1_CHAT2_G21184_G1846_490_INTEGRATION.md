# Chat 1 + Chat 2 integration — G21.184 late opt-in witness, 490 focused

This is an **additive evidence-only** integration, not a new gameplay policy or merge into main.

## Verified parent

- Exact certified joint parent: [Draft PR #2566](https://github.com/Trexzo/SpawnPK-RE/pull/2566), SHA `8a4272b58bb1a5973be02cf1ab7ea0a0847c3733`, [GitHub Actions #38544083608](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38544083608) SUCCESS: Java11 bytecode major55, 489/489 focused, full Gradle 7m46s.
- Adds EXACT source Git blobs from [Chat 1 G21.184 Draft PR #2568](https://github.com/Trexzo/SpawnPK-RE/pull/2568): `G21184CoreSocketLateEnableUnleasedOwnerTest.java` and `research/G21184_LATE_ENABLE_UNLEASED_OWNER_NEGATIVE_ACCEPTANCE.md`; source baseline certified G21.183 [Draft #2564](https://github.com/Trexzo/SpawnPK-RE/pull/2564) at `eb8af4565a30b141e5fdb1db03e78df4c27f00dd`.
- `server/build.gradle` composed from the exact **joint** base, adding G21.184 entry and a dedicated regression only; 489 → **490** unique focused tests. Chat 2 gameplay and `LocalSession.java` are retained **verbatim**, including G18.4.6 recipient-versus-lethal-attacker characterization. No production Java changes in this branch.
- The newer Chat 2 G18.4.7 integration [Draft #2567](https://github.com/Trexzo/SpawnPK-RE/pull/2567) is **not** imported here: its first exact-head [Actions #38544556396](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38544556396) FAILED at focused 277/490 in its new G18.4.7 name-reuse fixture (DMM snapshot requires exact current player). The failure was reported directly on that PR; it does not invalidate the prior joint baseline.

## G21.184 case

Opt-in experiment is FALSE when World A admits a real TCP named-account session. World A therefore holds no JVM path token, though it can mutate its state and persist a BOOTSTRAP. Toggle flag TRUE while A still online. World B now acquires a token and admits the same username in its own World; both players have independently captured account state. Disconnect A first and prove A's successful final file postimage, then B; B's later successful save overwrites A. This is a **negative** observation: no retrofit for an already-active unleased World. It does not implement a correction or authorize arbitrary dynamic toggling.

## Required certification

Fresh **exact-head** hosted Java 11 bytecode major55, dedicated G21.184 regression, 490/490 unique current-train focused, and full Gradle SUCCESS. A negative expected test PASS only demonstrates the unresolved behavior. Keep Draft/unmerged and never infer main release authority.

No full cross-process/journal/durable account fence, RAW/default writer isolation, original v308/v309 GUI test or historical SpawnPK server rule provenance. No settlement/grant/replay/cleanup/rollover/pruning/unlink/release/ACK; frozen R25 #1847 and G21.87/G21.88 untouched. Durable design [#2540](https://github.com/Trexzo/SpawnPK-RE/issues/2540) and global owner [#2559](https://github.com/Trexzo/SpawnPK-RE/issues/2559) remain OPEN. Chat 3 SpawnPK-Client untouched.
