# R17 — cross-JVM crashed-owner account recovery acceptance

**Scope:** local file persistence safety and restartability, not original
graphical SpawnPK client gameplay or production account recovery.

R15 enforces an exclusive OS file lock for both `opensrc` and `src`
accounts throughout a LocalLab process lifecycle. R16 requires unique,
force-written adjacent staging files and an atomic account rename. R17
binds these two mechanisms in a single deterministic **three-JVM test**.

## Timeline

1. Test coordinator initializes two independent fixture properties files in
   a newly created temporary account directory.
2. A separately launched *crash-owner* JVM acquires the real R15 directory
   lease, writes and force()'s an R16 stage for `opensrc`, and pauses
   **before** atomic replacement. A unique ready marker proves it reached
   that boundary.
3. The coordinator independently checks the R15 lease rejects ownership
   for **both** primary and secondary profile paths while the crash-owner
   process still holds it; both committed fixture files remain unchanged.
4. Coordinator terminates **only that exact spawned crash-owner child**,
   deliberately simulating abrupt process death, without asking it to run
   the normal shutdown coordinator.
5. After OS-reported termination, both prior profiles must match their
   exact original bytes, while the abandoned, already-forced stage exists.
   The coordinator proves R15 lock can be acquired and released again.
6. An independently launched *restart-owner* JVM takes the same directory
   lock, force-stages and atomically publishes updated properties for both
   `opensrc` and `src`; the coordinator checks both new values and
   verifies the previous abandoned staging file is not reused.
7. Test releases children and cleans only its own temporary fixture.

CI runs this fixture on Linux and Windows against Java 11-compatible compiled
classes. The user-facing R16 synthetic PvM/XP/bank two-process test is separate.

## Unproven

The test does **not** prove kernel crash/power-loss durability, a parent
directory fsync barrier, NFS/shared-device locks, malicious symlink-race
resistance, original v308 client GUI compatibility, or correctness of real
SpawnPK economy/combat. Abandoned stages are kept for examination and
explicitly deleted only as part of this disposable test fixture teardown.

No user v312 sessions or account profiles are opened, killed, or changed.
