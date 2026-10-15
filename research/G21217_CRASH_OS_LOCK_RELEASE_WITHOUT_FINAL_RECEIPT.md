# G21.217 — OS session lock is released by abrupt process death without final-save proof

**Ancestor:** [G21.216/G18.7.5 cumulative Draft #2680](https://github.com/Trexzo/SpawnPK-RE/pull/2680), corrected head `817a4269504df9231b3b9d5e4f6009e27c35b5b9`, **533 focused**, exact-head hosted CI in progress when branched. Its G21.216 optional cooperating cross-JVM RAW writer OS lock does not create crash-durable owner identity. Last independently hosted green core G21.215 [#2676](https://github.com/Trexzo/SpawnPK-RE/pull/2676) (531/531) demonstrates why RAW OS coordination is needed.

## Actual hard-process-death regression

A separate Java child JVM enables *all three* experimental session/JVM/RAW lock properties, registers a genuine WorldPlayer under a persistent named account, acquires/binds the real JVM token and stable G21.185 OS `.session.lck`. The child publishes **one completed and verified older World SaveTicket**, then changes a live World-only extension to a **newer unsaved value** and signals readiness through a TEST-ONLY side-channel file, not an account ACK. It deliberately never schedules a final save or releases the session token.

The parent JVM independently attempts an opt-in same-account session while the child remains alive; the real cross-JVM OS lock denies it. Parent then calls `destroyForcibly()` on the child — an abrupt process death, not a cooperative shutdown — and tests that, despite absent original-final-save acknowledgment/durable epoch, a new `LocalJvmAccountSessionLease.acquire` **CAN NOW OBTAIN the same OS lock**. Disk still shows the last saved older postimage. The lock file is still present: the lock *ownership* vanished with the dead process. Parent releases its test-only newly acquired token without opening a production session.

Marker `G21217_DEAD_JVM_OS_LOCK_RELEASE_WITHOUT_FINAL_RECEIPT_PASS` documents **a negative crash-admission gap**, not automatic failure of POSIX locks or a safe recovery path. It does not prove newly unsaved volatile state should be restored; it establishes that a lock release alone cannot authenticate prior owner final-save completion or grant recovery/ACTIVE marker retirement. The readiness file is not a durable account commit receipt, and the test calls NO mailbox reward or gameplay grant.

**Scope:** one self-contained TEST-ONLY Java class, one Gradle focused entry and dedicated task, this research note; **533→534** focused. Zero production Java, account default, original-client, main or frozen R25 changes. Requires fresh exact-head hosted Java11 major55/full Gradle, 534/534 focused and marker. Preserve Draft/unmerged.

## Required design before positive relog

Persistent epoch or comparable fenced owner generation bound to exact normalized file and every participating writer; durable negative termination/intent before an uncertain final save; explicit confirmed postimage + original owner ACK, restart recovery with real crash/late-worker cutpoints; lock-file path/inode substitution, uncooperative external writers, older default-OFF sessions and cross-JVM final save race handling. Even if all these are designed, a green G21.217 proves only the missing durable authority, **not** a correct recovery implementation. Keep #2540/#2559/ACTIVE review open.
