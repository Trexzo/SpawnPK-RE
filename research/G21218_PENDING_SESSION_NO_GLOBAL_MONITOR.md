# G21.218 — move OS session FileLock I/O outside the global JVM account monitor

## Provenance and current certified boundary

Based on [G21.217 Draft #2681](https://github.com/Trexzo/SpawnPK-RE/pull/2681), exact `d1baa0054b66faa0a5f2736e3217d69099161245`, hosted [Actions #38758517074](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38758517074) **SUCCESS**: 534/534 focused, Java11 major55/full Gradle. G21.217's real hard-killed World JVM proves OS lock release after crash cannot attest final write, and is explicitly not a recovery fix.

G21.216 previously moved **RAW OS reservation** I/O outside the JVM-wide `LocalJvmAccountSessionLease.active` monitor to avoid unrelated account contention. However the ordinary `LocalJvmAccountSessionLease.acquire` and `Token.release` still invoked `LocalCrossJvmAccountSessionLock.acquire/close` (FileChannel/tryLock/release) *inside* that same global monitor. A slow/blocked filesystem operation on account A could stall entirely unrelated account B and a RAW writer B. This is also a latent lock inversion hazard.

## Production refactor

- Add `pendingSessionAdmissions`, a private normalized-Path-keyed in-JVM set. Each opted-in session claims this reservation atomically against existing `active`, `pendingSessionAdmissions` and `rawWriters`, then **releases** the JVM map monitor BEFORE invoking OS FileLock I/O. Only after actual OS acquisition does it atomically convert the pending entry into a committed Token. Same-path second sessions and participating RAW writers see the pending state and reject. Other account paths progress independently.
- If OS lock acquisition fails, test-only admission hook throws, or another error occurs, clear the pending reservation without ever installing a current owner Token. If closing a partially obtained OS lock fails, retain pending negative state rather than misrepresent lock release as safe admission. Properties remain DEFAULT OFF and must be configured prior to account admission.
- `Token.release` retains its own per-Token synchronized identity monitor and keeps `active[path]` populated while releasing the OS FileLock **outside** JVM-wide map monitor, then removes exact Token only after successful close. On uncertain OS release, retain token in map and throw, preserving fail-closed behavior.
- RAW reservation acquisition also checks pending session entries, preventing same-file RAW from racing the OS lock interval. This changes neither original client/server protocol nor World save/receipt provenance, final-save failure handling, mailbox PREPARED/terminal fences or optional lock path. No new authority for epoch/restart.
- A package-private deterministic test-only hook `pendingSessionAdmissionProbeForTest` runs synchronously AFTER the pending map entry is installed and AFTER the monitor is released, BEFORE filesystem OS acquisition. Production hook is null; it does not grant/modify account state, perform implicit release, or persist epoch metadata.

## Two independent focused regressions, 534→536

1. `G21218CorePendingSessionAdmissionNoGlobalMonitorTest`: pause actual first A lease after its pending claim with CountDownLatch; confirm same-file competing session AND cooperating RAW denied, while unrelated B lease and B RAW reserve/close finish **before** A resumes; commit A Token, release normally, verify no map leakage. Any hold of global monitor across the hook deadlocks/fails bounded test.
2. `G21218CorePendingSessionFailureRollbackTest`: hold the actual OS `.session.lck` with a separate FileChannel, trigger real `acquire` denial and ensure pending map fully rolled back; verify foreign OS lock also denies RAW before account creation; force deterministic hook exception before OS and confirm rollback; a subsequent valid session acquisition succeeds after lock release/hook reset.

Acceptance: exact-head hosted Java11 major55, full Gradle, **536/536 unique focused**, markers `G21218_PENDING_SESSION_ADMISSION_NO_GLOBAL_MONITOR_PASS` and `G21218_PENDING_SESSION_OS_FAILURE_ROLLBACK_PASS`. Draft/unmerged.

## Still unresolved

This refactor does **not** prevent an uncooperative external writer, older already-admitted default-OFF World, runtime flag flips, lock file path/inode substitution, forced process termination, OS advisory lock implementation anomalies or late save after cross-process owner replacement. The G21.217 crash gap persists: an OS FileLock disappearing on death is not a durable final-save receipt or authenticated epoch. No positive reconnect, ACTIVE marker retirement, grant/replay, manual cleanup or recovered credits. Keep #2540, #2559 and #2587 OPEN.
