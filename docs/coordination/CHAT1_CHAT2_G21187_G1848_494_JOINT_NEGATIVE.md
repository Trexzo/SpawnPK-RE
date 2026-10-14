# Chat 1/2 G21.187 + G18.4.8 integration (494 focused)

## Proven parent
- Latest joint certified [#2578](https://github.com/Trexzo/SpawnPK-RE/pull/2578), exact `78cda6d8dfb5cb6553b8d3a04e3745ae833f2ed0`; hosted [Actions #38553606089](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38553606089) SUCCESS: Java11 bytecode major55, **493/493** focused, full Gradle 7m57s. This includes actual cross-JVM TCP G21.186 healthy-session ownership plus Chat 2's G18.4.7/G18.4.8 negative gameplay evidence.
- Adds *only* exact G21.187 test/research blobs from [core Draft #2579](https://github.com/Trexzo/SpawnPK-RE/pull/2579) source head `e76e670a2b1850335e886ee0468136b046ad69f4`; one manifest/task increment **493→494 unique**. **No production source changes** and Chat 2's `LocalSession.java` and G18.4.7/G18.4.8 test unchanged.

## G21.187 negative witness
- Disposable, private first-time account. Actual synthetic LocalSession TCP login in an externally launched holder JVM. Initial BOOTSTRAP save completes on disk; holder mutates World-owned extension KILLED_UNSAVED but does **not** logout/finish final save. Other JVM's actual competing TCP login is denied by the experimental cooperative account file lock.
- Parent force-kills ONLY holder test subprocess before normal logout. OS releases the temporary process lock; account bytes remain exactly BOOTSTRAP.
- Fresh external JVM real TCP login **succeeds** into stale BOOTSTRAP, fails to rehydrate the killed mutation and can final-save a new state. This is **negative evidence of missing crash-durable admission**, *not a fix*. It does not prove every power-loss cutpoint; process kill only.
- Both opt-in flags remain DEFAULT OFF outside the private fixture. No raw writer exclusion, cross-host lock guarantee, adversarial path remap/unlink protection, durable intent journal, automatic recovery, historical SpawnPK GUI/client or original reward attribution.
- Chat 2's separate [G18.4.9 Draft #2577](https://github.com/Trexzo/SpawnPK-RE/pull/2577) first CI failed on new no-owner delayed-respawn control and is **not imported** into this cumulative branch until independently green. No gameplay production code touched.

## Independent verification
New exact-head hosted CI required: Java11 major55, G21.187 negative marker, existing G21.185/186 and G18.4.7/8 markers, **494/494 focused**, full Gradle SUCCESS. Draft/unmerged; do not count a passing negative test as durable safety.

Follow-up core [#2540](https://github.com/Trexzo/SpawnPK-RE/issues/2540) and [#2559](https://github.com/Trexzo/SpawnPK-RE/issues/2559) remain OPEN. Frozen R25 #1847/G21.87/G21.88; no settlement/grants/replay/cleanup/rollover/pruning/unlink/durable session release/ACK. Chat 3 client repo untouched, shared handoff [#2526](https://github.com/Trexzo/SpawnPK-RE/issues/2526).
