# Cumulative Chat1 G21.278 + Chat2 G19.5.2: observable post-settlement failures

**Exact integration parent:** [Chat1 + Chat2 #2814](https://github.com/Trexzo/SpawnPK-RE/pull/2814) at `b5810e93ea4868ba18b8c5e5a43e048992a51a61` with 671 candidate focused regressions. This parent retains G21.277/278 account-ownership and foreign-JVM ACTIVE marker fences and G1947–50 shared NPC hitmask handling.

**Gameplay donor:** [Chat2 #2815](https://github.com/Trexzo/SpawnPK-RE/pull/2815) at `eb79a733f208871dcf1e3b543d5093d04f046451`. The core integration's `MonsterSpawnerPvmRuntime` and `LocalCanonicalNpcAttackPvmRuntimeBridgeTest` baseline blobs were byte-for-byte identical to the donor preimages. The modified runtime, fixture, both focused regression classes and gameplay note were imported with **identical Git object hashes**. The merged `server/build.gradle` differs from core only by G1951/G1952 and two JavaExec tasks.

## Source behavior

After canonical NPC ground-drop settlement and exact NPC retirement, an installed terminal observer may return or throw. Current runtime intentionally contains observer `Throwable`; a failed G1/G4/G14/G15/G16/G18 observer could have partially applied rewards, but prior source logged the failure with no runtime-readable outcome.

New `MonsterSpawnerPvmRuntime.terminalObserverDiagnostics()` provides a bounded, immutable observation of which callbacks RETURNED or THREW, using exact npcId/deathTick/recipientRef/spawn-generation and failure class, plus saturating counters/128-entry ring. The record is not a durable outbox, never claims per-consumer credit, and is never a replay trigger.

## Hosted acceptance

Exact-head **673/673**, G1951 and G1952 markers, Java11 major55 and full Gradle required. The parent and donor green runs do not certify this merged head. No account-security source files changed; `main` remains untouched.

Original SpawnPK kill-credit attribution [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558) remains unknown; retry-safe/durable per-consumer G1/G4/G14/G15/G16/G18 apply/ACK is unresolved [#2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587). Explicitly **do not close** those issues based on an observational diagnostic.
