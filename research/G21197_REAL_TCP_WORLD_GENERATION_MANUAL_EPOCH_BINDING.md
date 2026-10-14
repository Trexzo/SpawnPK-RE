# G21.197 — actual TCP World generation, manually bound to UNUSED epoch prototype

## Parent and tested purpose

Exact certified parent: [G21.196 + G18.5.8 #2616](https://github.com/Trexzo/SpawnPK-RE/pull/2616), SHA `d3ea1398d0d671c02a37ca5614d78127b7d204da`, [Actions #38596218922](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38596218922) SUCCESS; 503 focused, Java 11 bytecode verification and full Gradle. Parent contains Chat1 G21.195 cross-process ACTIVE-negative and the standalone G21.196 durable reservation/binding design prototype plus Chat2 G18.5.8 source.

**G21.197 asks one precise question:** can TEST CODE reserve the standalone epoch before a REAL synthetic 317/ISAAC TCP account login, observe a genuine `WorldPlayer.generation()` assigned by `world.registerPlayerAndStart`, and explicitly write the corresponding forced prototype binding afterward, without modifying actual admission/persistence or allowing G21.190 ACTIVE marker retirement?

## Exact test sequence

1. Create a new isolated named-account temporary filesystem root and opt in cooperating JVM lease, cross-JVM OS lock, review admission veto, one-shot pre-registration ACTIVE intent and forced real World save **only during the test**. Create the account directory. No real account content yet.
2. From test code only, invoke `LocalDurableOwnerEpochPrototype.reserveBeforeWorldRegistration` *before* `OneSession.login(USER)`, assert reservation is unbound, has no account content, and no generation-binding file exists. This DOES NOT invoke or modify the production LocalSession initializer.
3. Perform actual localhost synthetic 317/ISAAC login; observe current registered WorldPlayer and its positive `generation()`, verify an existing one-shot forced ACTIVE review marker and live JVM account lease. Test code explicitly invokes `bindAfterWorldRegistration(reservation, observedGeneration)`. Inspect reservation digest and generation equality.
4. Change the actual World-owned snapshot extension, close the TCP socket cleanly, assert real forced World-owned final-save/readback completed and repository persisted the new value; verify healthy lease release.
5. The complete prototype record pair still has no admission authority. Confirm G21.190 ACTIVE marker remains, create a separate new World/repository and attempt another genuine socket login. It must be refused without World registration or new session lease; verify marker/account/reservation/binding bytes unchanged.

**No production owner-epoch integration:** The real generation is passed **manually by the test harness** to a standalone helper. The primitive does not independently authenticate that value, and no production World/LocalSession, FilePlayerRepository, G21.188 ACTIVE sidecar, or session token refers to it. A matching digest/generation is not a durable authenticated owner-epoch receipt. Even an otherwise healthy, forced postimage cannot justify marker removal. There are no positive recovery, repair, retirement, grant/replay or cleanup methods.

New `G21197CoreSocketObservedGenerationPrototypeBindingTest` plus Gradle task `g21197CoreSocketObservedGenerationPrototypeRegression`. Focused manifest **503→504 unique**; no existing production sources changed, Chat2 G18.5.8 test source byte-identical, no changes to default settings/client/main. Require fresh exact-head hosted Java11 major55, `G21197_REAL_TCP_GENERATION_PROTOTYPE_PASS`, 504/504 focused and full Gradle. Draft until green. Important missing proof: external-JVM restart with *real* bound generation, power-loss cutpoints, uncooperative writer fencing, authenticated owner assignment, forced epoch-matched final save receipt and fail-closed marker-retirement protocol.

Continue design issues #2540/#2559; preserve Chat2 #2587 for separate PvM per-consumer outbox and #2558 original kill attribution.