# Chat 1 integration repair: G2046 test subprocess source classification — 2026-10-17

## Actual failure

Chat 2 [#2940](https://github.com/Trexzo/SpawnPK-RE/pull/2940) exact `06dc4eb43a73824cf31f84aa5ebe509a7ca87d47`, hosted run [#39032567792](https://github.com/Trexzo/SpawnPK-RE/actions/runs/39032567792), **FAILED at :compileJava** (no 860/860 tests executed). `server/src/spk/local/G2046DiskJournalChildWorker.java` referenced `G2017TerminalAttemptFenceRealDeathTest.Fixture` four times, but the Gradle main source set excludes `**/*Test.java` while including the worker because its file name ends in `ChildWorker.java`, not `Test.java`. Test fixture is located under the same flat source directory but classified into test source set. The Java code is not inherently invalid; its current Gradle production classification is.

Cumulative [#2941](https://github.com/Trexzo/SpawnPK-RE/pull/2941) exact `5555b0941097098bea3f61667543dbd57dc6b7fc` carries the same test-only helper and may have inherited this compilation blocker; do NOT claim its hosted 863 pass until the full exact-head log passes.

## Minimal integration source-set repair

`server/build.gradle` only: add a precise `exclude '**/G2046DiskJournalChildWorker.java'` to main and `include '**/G2046DiskJournalChildWorker.java'` to test while preserving the existing `**/*Test.java` classification for all other flat-tree classes. This compiles the subprocess class against G2017's genuine settled NPC fixture in the test source set and keeps it **out of the production JAR**.

Add an explicit `verifyJava11Bytecode` integrity check requiring the worker's .class to exist in `classes/java/test` and NOT in `classes/java/main`, with `G2046_JOURNAL_WORKER_TEST_SOURCESET_PASS`. No runtime main implementation, journal source, test class, evidence oracle, Gradle task manifest or 863 focused count changes. The six subprocess tests still exercise REAL separately launched JVMs through the test runtime classpath; their crash-before/after-move proofs must be confirmed by hosted full Gradle.

## CI / safety gates

Exact-head Linux **863/863**, all G2046–51 subprocess markers, Java11 major55/full Gradle and worker-isolation marker required; Windows targeted source-only smoke separate. Hostile lock-path inode replacement remains a deliberately proven negative (not a trusted owner epoch). No authenticated cross-JVM authority, consumer grant ACK, replay, directory fsync or original-client GUI/179 acceptance. main remains unchanged and all PRs drafts.
