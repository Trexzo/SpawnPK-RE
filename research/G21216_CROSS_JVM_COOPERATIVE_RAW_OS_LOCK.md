# G21.216 — cooperating RAW writer shares G21.185 OS session lock across JVMs

**Source:** G21.215 [Draft #2676](https://github.com/Trexzo/SpawnPK-RE/pull/2676) exact `22bb79561a5bee77a38a7e41d745434a87b6b512` is a test-only NEGATIVE (531 focused) demonstrating that a real *second JVM* can successfully RAW-save a stale snapshot even when the first JVM owns the G21.185 stable `.session.lck`. The parent negative verifies the other JVM cannot acquire a second normal session. Its hosted exact-head CI must be independently checked. Last certified base G21.214 [#2675](https://github.com/Trexzo/SpawnPK-RE/pull/2675), exact `8ca798a616588bb80c31542d598b7a131cf6a968`, Actions #38750698902 SUCCESS **530 focused Java11/full Gradle**.

## Narrow fix

The same opt-in `LocalJvmAccountSessionLease.reserveRawWrite` participating in FilePlayerRepository.save, non-primary LocalAccountProfiles.save, primary AccountStore.save and standalone non-World StrictDurablePlayerSnapshotWriter now claims the **existing stable sibling cross-JVM session OS FileLock** (`LocalCrossJvmAccountSessionLock.acquire`) when BOTH `spk.local.experimentalRawWriteJvmLeaseGate=true` and `spk.local.experimentalCrossJvmAccountSessionLock=true`, additionally requiring `spk.local.experimentalJvmAccountSessionLease=true`. Deny with `G21.216 CROSS_JVM_RAW_ACCOUNT_WRITE_DENIED` if an opposing JVM's session or RAW writer already owns this OS lock. No file/temp mutation should be attempted before a successful reservation. The lock stays held through complete RAW serialization, replace and cleanup; close releases the OS lock **before** removing the same-JVM RAW reservation from the existing map. Any OS unlock failure retains a negative in-memory reservation rather than silently granting a successor. No new OS lock filename/domain/unlink, no external process protocol, and no default enabling.

Only the already participating opt-in RAW writer routes use this. G21.185 session lock admission is otherwise unchanged. Optional same-JVM and cross-JVM flags must be set **prior** to all session/writer admission, not toggled live.

## Test and historical negative continuity

The exact parent #2676 retains real two-JVM **all-flags-ON RAW bypass evidence**. On this descendant the inherited G21215 test is explicitly changed to switch the child-only *cross-JVM RAW gate OFF* just around its RAW write, preserving the historically unsafe opt-out path as a test-only negative (NEVER safe to toggle in live sessions).

NEW G21216 positive forks actual distinct child JVMs twice while all three flags ON:
1. Parent holds a registered WorldPlayer original JVM token + G21.185 stable OS session lock, writes newer account extension through actual captured-owner queued World SaveTask; child attempts `FilePlayerRepository.save` and must receive G21.216 denial with unchanged raw temp/account bytes and no child reservation leak.
2. Parent TEST-ONLY unregisters/releases, then holds a RAW reservation on the same account/OS sibling lock; a new child JVM attempts the regular opt-in account session and must be denied by G21.185. Reservation then releases with no leak and disk remains unchanged.

Marker `G21216_TWO_JVM_COOPERATIVE_RAW_SESSION_LOCK_PASS`; focused **531→532**, hosted exact-head Java11 major55/full Gradle mandatory. The new test uses inherited runtime classpath and a real Java child process; no mocked cross-JVM results.

## Explicit NOT-YET-COVERED authority

- This is **cooperating** opt-in process exclusion only. Any manual/raw/external writer ignoring the lock, stale pre-opt-in real sessions, dynamic property flips, symlinked ancestors, lock-file replacement/unlink, ABA object identity, NFS/unsupported advisory locking, process death or forced restart can violate global guarantees.
- OS FileLock process lifetime is NOT a durable owner/generation epoch or proof that the last write completed/forced data. No durable negative intent, cross-restart original owner acknowledgment, physical-crash consistency or authenticated positive recovery.
- No G21.188 ACTIVE review marker retirement, same-user clean re-admission, Mailbox grant/settlement/replay, or per-consumer outbox authority is established.

Remain DRAFT/unmerged, preserve #2540/#2559/#2587 OPEN.
