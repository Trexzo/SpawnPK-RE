# G21.218 — session admission no longer performs OS I/O under global JVM lease monitor

Parent certified G21.217 [#2681](https://github.com/Trexzo/SpawnPK-RE/pull/2681), exact `d1baa0054b66faa0a5f2736e3217d69099161245`, hosted Actions #38758517074 SUCCESS, 534/534 focused Java11/full Gradle.

G21.216 had corrected RAW reservation OS I/O outside `synchronized(active)`, but `LocalJvmAccountSessionLease.acquire` still held the **global account map monitor** while calling `LocalCrossJvmAccountSessionLock.acquire`: directory, FileChannel.open and OS tryLock. A stalled account-A filesystem could block unrelated account-B admission.

G21.218 adds an exact-path **pending session admission** reservation under `active`, then performs OS work without that monitor, finalizes only if the pending identity is still current, removes pending on success or failure, and retains existing local RAW/session denials for the same path. RAW reservations now reject a pending session. Different account paths remain independent. A test-only hook after reservation and before OS acquisition gives deterministic concurrent behavior and injected failure coverage; default hook null has no normal runtime impact.

Regression holds account-A pending OS acquisition via a bounded latch, proves account-B can acquire independently, a second A session and direct RAW reservation are refused, then A is admitted after release. Injected failure must unwind pending state and allow retry. `535` focused / Java11 major55/full Gradle required. No durable authenticated epoch, crash intent, path substitution protection, previously unleased owners, live opt-in toggles, ACTIVE retirement or reward authority is implied.

Potential follow-up: cancellation/interrupt handling, unusual OS lock close failures, and explicit same-process stress across many account identities. Keep Draft/unmerged.
