# Chat 1 + Chat 2: G21.184 and G18.4.7 combined, 491 focused tests

## Exact already-hosted-green inputs

1. Chat 1+2 [Draft #2570](https://github.com/Trexzo/SpawnPK-RE/pull/2570), exact head `f374b76cf963bcf0617370eff7ca7371b62376d4`, [Actions #38545651914](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38545651914) **SUCCESS** Java 11 major55, **490/490 focused**, full Gradle 7m56s. This includes G21.184's negative two-World late-feature-enable evidence, inherited opt-in session lease and Chat 2's G18.4.6 game tests.
2. Chat 2 [Draft #2569](https://github.com/Trexzo/SpawnPK-RE/pull/2569), exact head `09ee75d612afb9dfd16548c4694e41586ed74ab7`, [Actions #38545508000](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38545508000) **SUCCESS** Java 11 major55, **490/490 focused**, full Gradle 7m55s, and `G1847_NAME_REUSE_TERMINAL_ATTRIBUTION_CHARACTERIZATION_PASS`. This branch has the exact corrected G18.4.7 test and documentation, without production gameplay changes.

## Fresh additive composition

Base this branch **exactly** on G21.184 #2570. Copy Chat 2's **exact existing Git blobs** for `G1847MonsterSpawnerNameReuseTerminalAttributionTest.java`, its original evidence doc and its G21.183-focused handoff note. Do not overwrite Chat 1's World/LocalSession/lease code or Chat 2's production gameplay. In `server/build.gradle`, add the one G18.4.7 class to the G21.184 490-unique manifest and a dedicated `g1847MonsterSpawnerNameReuseAttributionRegression` task; expected **491 unique** current-train focused classes. Preserve all existing G21.180–G21.184 and G18.4.x tasks and tests verbatim. No production Java file is changed by this integration branch.

The older broad G18.4.7 alternative #2567 failed its first CI run. #2569 supersedes that broken fixture with corrected source and independent hosted-green evidence. Do not merge both paths.

## Meaning of negative results

- **G21.184:** An unleased World A admitted when experimental JVM account path lease is OFF remains untracked if the property is turned ON while A is still online. World B may acquire a token and admit same account; two successful final saves can overwrite A's postimage. This is negative evidence; **no mitigation** is implemented here.
- **G18.4.7:** Actual LocalLab Monster Spawner terminal observer still credits current username generation on name reuse, even though immutable NPC spawn-origin belongs to an older owner. A generation-pinned opt-in API rejects stale A. This documents present `CUSTOM_LOCALLAB` behavior but does not recover original SpawnPK server kill-credit policy, prove an exploit or authorize live reward changes.

## Required certification

The two separate passing inputs cannot certify the NEW combined tree. Require fresh **exact-head** GitHub Actions: Java 11 major55, G21.184 + G18.4.7 markers, **491/491 focused tests** and full Gradle SUCCESS. Keep Draft/unmerged until all pass and do not auto-promote.

No cross-process or crash-durable account fencing, RAW/legacy writer exclusion, automatic state recovery, original client GUI parity, historical reward-policy authority, settlement/grant/replay/cleanup/rollover/pruning/unlink/session release/ACK. Keep design [#2540](https://github.com/Trexzo/SpawnPK-RE/issues/2540) and [#2559](https://github.com/Trexzo/SpawnPK-RE/issues/2559) open and frozen R25 #1847/G21.87/G21.88 unchanged. Shared owner coordination [#2526](https://github.com/Trexzo/SpawnPK-RE/issues/2526), Chat 3 SpawnPK-Client untouched.
