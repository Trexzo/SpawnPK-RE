# R19 — fail-closed Main startup rollback after World starts

**Scope:** localhost-only server startup failure handling. This does not
launch a graphical v308 or v312 client, claim original SpawnPK gameplay
fidelity, or modify live user account files.

## Failure fixed

R18 required both localhost sockets to bind before creating the World.
However, earlier `Main.serveBoundSockets` still started `World` and then
performed logging, shutdown hook construction and acceptance-only stdin
configuration before entering its shutdown `finally` block. An exception
between `World.start()` and that block could bypass normal resource
finalization.

## Implementation

- `LocalLabTestStdinShutdown.validateConfiguration()` rejects malformed
  test-only configuration before any account-directory lock, listener or
  World is created. Its semantics match the existing R14 private stdin
  acceptance gate; it never reads stdin or starts a thread.
- `Main` constructs the shutdown coordinator and registers its JVM hook
  before starting `World`. The entire World startup, logging, optional
  private stdin watcher and accept loop execute under one `try/finally`.
- If hook registration fails, the coordinator's existing shutdown path
  still executes; if startup fails after hook registration, the existing
  `MainShutdownFinalizer` closes owned resources and unregisters the hook.
- A deliberately opt-in test-only Java property
  `spk.local.acceptance.injectFailureAfterWorldStart=true` causes a
  controlled throw immediately after `World.start()`. It exposes no
  network control, login command or gameplay behavior.

## Real JVM verification

`MainStartupRollbackTest` uses a separate Java process, a temporary
`user.home` and two disposable account files. It verifies malformed
private-stdin flags and tokens fail **before the R15 lease** and without
any readiness announcement or listener. It then launches another actual
Main JVM with the R19 test-only failure property and proves:

1. Main acquires the R15 lease and starts World.
2. The failure is injected at the post-World-start boundary.
3. `V5123_PERSISTENCE_SHUTDOWN_CLEAN` is logged by the existing World
   shutdown coordinator.
4. Both localhost ports 43594/43595 are available again.
5. The original bytes of both primary/secondary files are unchanged.
6. The parent reacquires the OS account-directory lease.

The test refuses to start if either loopback port is occupied before it
begins. It may forcibly terminate only an exact child that this test
created **if that child exceeds its bounded deadline**.

## Acceptance status

Independent authorized Windows verification:
`R19_REAL_MAIN_POST_WORLD_FAILURE_ROLLBACK_PASS` on dedicated Java 11
against R18-built server classes plus the exact R19 Main and test sources
compiled with `javac --release 11`. Exit code 0.

Linux and Windows GitHub Actions jobs must pass on the final R19 PR head
before merge. Existing R18 end-to-end tests of successful normal startup,
two-port admission, R14 clean exit and server PvM are retained.

## Explicit limitations

This is not a guarantee against JVM-terminating fatal errors or abrupt
power failure. Resource cleanup cannot be guaranteed after
`Runtime.halt`, OS kill, power loss or certain process-wide memory
exhaustion conditions. It does not certify original client GUI login,
network isolation, NFS/cross-host account locking, real SpawnPK PvM
formulas, or durability against storage-hardware faults.
