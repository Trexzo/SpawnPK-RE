# Chat 1 + Chat 2 G21.188 / G18.5.0 integrated test prototype (495 focused)

## Exact proven parent
Canonical Chat 2 G18.5.0 [Draft #2583](https://github.com/Trexzo/SpawnPK-RE/pull/2583) SHA `db32f6afb37767412dbe3f71d3eb086dad09c358` is **hosted GREEN** at [Actions #38558811394](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38558811394), Java11 major55, **494/494** focused, complete Gradle 8m01s. This includes G21.187 forced-JVM-kill stale-BOOTSTRAP NEGATIVE, Chat 2's G18.4.7/8/9 delayed terminal owner behavior and G18.5.0 pending-settlement generation credit observation. No original SpawnPK server reward or account policy recovered by these synthetic LocalLab tests.

## Additive Chat 1 prototype
Copy exact Chat 1 [G21.188 Draft #2584](https://github.com/Trexzo/SpawnPK-RE/pull/2584) blobs: `LocalDurableAccountReviewIntentPrimitive.java`, `G21188CoreDurableReviewIntentProcessKillTest.java` and its research note. New primitive creates an exclusive `.g21188-active-review` test marker with NOFOLLOW path checks, file force and directory force, intentionally no deletion/retirement API. Fresh external JVM sees marker after the marker-creator JVM is killed, so it refuses a second marker creation. **This is a separate filesystem primitive only**, NOT connected to `LocalSession`, account load/save, World persistence, gameplay or live access control. **No change to production runtime behavior**, feature flags untouched and default OFF.

Add G21.188 to Gradle manifest (**494→495 unique**), plus its dedicated regression. Preserve Chat 2 G18.5.0 and all existing G18.4.x gameplay test/source byte-for-byte, preserve all existing G21.180–187 core and LocalSession files. No new gameplay feature/reward path.

## Fresh acceptance and limits
New exact-head hosted CI REQUIRED: Java11 major55, `G21188_DURABLE_PRIMITIVE_PASS`, `G21187_KILLED_JVM_PASS`, Chat 2 G18.4.7/8/9 and G18.5.0 markers, **495/495** focused and complete Gradle. Independent ancestors never certify the combined tree. Keep Draft/unmerged.

G21.188 force+directory marker durability through **process kill** is not evidence of power-loss durability, real account admission quarantine, crash-safe World final-save, correct intent retirement, original client behavior, or cross-process/raw writer security. `FilePlayerRepository.replaceSnapshotTemp` currently atomically moves temp account bytes without an explicit file/directory `force` chain; do NOT retire a future intent based on the existing final-save ACK alone.

Open durable design [#2540](https://github.com/Trexzo/SpawnPK-RE/issues/2540), writer ownership [#2559](https://github.com/Trexzo/SpawnPK-RE/issues/2559), and Chat 2 original kill/respawn authority [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558). No main merge, account repair/reward/settlement/grants/replay/cleanup/rollover/pruning/unlink/ACK, frozen R25 #1847 and G21.87/G21.88, Chat 3 SpawnPK-Client unchanged. Shared coordination [#2526](https://github.com/Trexzo/SpawnPK-RE/issues/2526).
