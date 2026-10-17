# Chat 1 G21.202 + Chat 2 G18.6.3 — immutable captured lease and valid G4 collision (509 focused)

## Source pins

**Certified common parent:** [G21.201 + G18.6.2 #2631](https://github.com/Trexzo/SpawnPK-RE/pull/2631), exact `2dfaed122b33b88e25730514338153a3ec5e9968`, [Actions #38712878786](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38712878786) SUCCESS (508 focused / Java11/full Gradle).

**Chat1 new core:** [G21.202 Draft #2634](https://github.com/Trexzo/SpawnPK-RE/pull/2634), exact SHA `2cb2e88e49c792164dfdc80223222357704a3565`, Actions #38714524386 pending at assembly. JVM-only immutable `SaveCaptureWitness` stamps original in-process Token identity at ordinary/final World snapshot capture, carries it through queued SaveTask, and checks same object still owns account path before file publication. Previously genuine unleased late-toggle G21.184 retained, unknown/manual capturers use existing fallback, and checkpoint/strict/mailbox remain out of scope. Core focused count 508→509. No durable epoch or marker retirement.

**Chat2 exact donor:** [G18.6.3 combined on corrected G21.201 #2633](https://github.com/Trexzo/SpawnPK-RE/pull/2633) exact SHA `eea38ae19763cfb56bf7ed8c8c15b63bbcefeeda`, own Actions #38714176668 pending when imported. Existing G1847 installed observer test **byte-identical Git blob `eb9b2dcec90555515c74265e6f6441a185075408`**, original G18.6.3 coordination note **blob `ba4f66c16fef87abd5ff07b2af43156caf3775de`**, and existing Chat2 G21.201/G18.6.3 handoff note **blob `b0e041de8adb911b546b80c22365369be367a529`**. Donor G18.6.3 standalone #2632 SHA `aba5c639ad8bc05f3cf1395d1d1bff7d4acd569f`, hosted Actions #38712623688 SUCCESS. Do not re-import older G18.6.2 tests.

## Orthogonal negative results

G21.202 real TCP socket test requires the asynchronous final save worker to perform its own original-capture-token check *after* manual probe count, forces actual World mutation and final account save, and rejects captured witness reuse after original token release. This is not cross-JVM owner authority; missing/replaced/released JVM token remains a fail-closed negative for a previously leased capture. Manual foreign WorldPlayer / wrong captured generation probes are not forged FIFO injections.

G18.6.3 test models LocalLab G4 World/name state collision: old A's valid persisted ACTIVE snapshot, task completed under same-name B, and new C receives only test-owned in-memory restored A namespace. The installed observer's actual G1 delayed respawn and next NPC death credit G1/DMM but G4 hydration refuses the valid stale snapshot with `PERSISTED_STATE_COLLIDES_WITH_LIVE_RUNTIME`. Not a real TCP/account-file recovery, replay/repair, external exploit or original SpawnPK Slayer reward policy.

## CI gate and no-merge boundary

Compared to G21.202 #2634 exactly **four** additional files: existing Chat2 G1847 test overlay, TWO original Chat2 coordination docs, and this new Chat1/2 integration handoff. ZERO extra core production/Gradle changes, focused manifest remains **509 unique**. Require fresh exact-head `G21202_ORIGINAL_LEASE_CAPTURE_WITNESS_PASS`, `G1863_REHYDRATED_ACTIVE_G4_COLLIDES_WITH_COMPLETED_WORLD_TASK_PASS`, `G21184_LATE_ENABLE_PASS`, 509/509 focused, Java 11 major55 and full Gradle. Both component and combined PR CI must pass independently. Stay Draft/unmerged; no main/client, default flags, automatic ACTIVE deletion/recovery, grants, replay, raw-writer or physical power-loss claims. Chat1 issues #2540/#2559 and Chat2 outbox #2587 remain OPEN.