# R74 — Offline command ingress quiescence and forced-IO shutdown

**DRAFT / STACKED ON R73 / OFF BY DEFAULT / UNMERGED**

## Threats to R73's logout proof

R73 uses a World actor callback for the final R64 release and unregister,
and waits for the real IO worker. Two gaps remain:

1. Commands can be **enqueued before final capture** but *dispatched after*
   quiescence. Checking only World membership or accepting a command on its
   original enqueue date does not prove that an action can still run safely.
   Producer submissions after final capture also need an admission fence.
2. An in-flight writer may be blocked when `World.close` exhausts its
   graceful IO wait. The existing `shutdownNow` rejection settles a future
   but **does not cancel arbitrary non-cooperative blocking IO**. Treating
   an exceptional future as proof of filesystem rollback is unsafe.

## R74 test-only implementation

### The actual WorldCommandInbox

`WorldCommandInbox` accepts an optional
`OfflineCommandAdmission`. With no test-installed hook the old command
queue and execution logic continue unchanged.

When a test installs `R74OfflineCommandIngressFence`, the inbox checks
permission both:

- at producer `submit` **before** putting the command in the real bounded
  World queue; and
- inside the `ExecutionOwnership.run` callback, while the actual World
  actor's player mutation lock is held, **immediately before** the
  command's gameplay handler runs.

An isolated World actor calls `quiesceInsideWorldActor` after capturing
its R72 final snapshot. Ordinary commands targeting that player/generation
are refused, including previously accepted but not yet executed commands.
A distinct test-only `TrustedFinalRelease` action is admitted only for
the exact quiesced WorldPlayer and owner generation.

R74 adds an overloaded `R73OfflineFinalLogoutCoordinator.finish` that
requires the gate to be quiesced before it submits the trusted release
callback. The original R73 signature still works unchanged when no R74
gate is installed. The trusted marker does **not** authenticate accounts:
the callback still validates the exact R72 durable final snapshot and
active R64 owner under R60 before release/unregister.

### Forced-shutdown witness

`WorldPlayerPersistence` recognizes an optional
`OfflineForcedShutdownWitness` in an explicitly installed test
admission hook. Only when the existing `World.close` IO path crosses
from its graceful wait into forced shutdown does it notify the witness
*before* calling `shutdownNow`. The same notification occurs on an
interrupted shutdown.

The test-owned wrapper blocks **before** invoking the R72 coordinated
file writer, deliberately ignores thread interruption, and waits on a
test-controlled latch. Once the actual forced shutdown begins, the
witness sets an abort flag. When the blocked worker eventually resumes,
it must refuse **before** the R60 lock or FilePlayerRepository path.
The real in-flight save future fails; R64 remains ACTIVE; and account,
R63, R64 and R72 watermark records stay byte-identical.

**This is a cooperative test gate, NOT a general forced filesystem write
cancellation mechanism.** A writer already inside a syscall, a writer
that bypasses the hook, or a non-cooperative writer that ignores the
witness can still mutate after the future was failed. Production
reliability requires a separate crash-consistent design.

## Deterministic regression

`R74OfflineIngressAndShutdownTest` creates disposable accounts, a real
isolated World, real command inbox, real IO executor and R60/R63/R64/R72
root directories:

- Capture final save inside a World actor. Inside the *same* actor
  callback, enqueue another ordinary gameplay command **before**
  marking the actor quiesced. The command's future must fail when
  the real dispatcher checks the gate before the gameplay handler.
  An independently submitted command **after** quiescence must fail
  at producer admission. Neither handler may mutate inventory.
- With a quiesced actor, R73's final save, IO drain, marker check,
  R64 RELEASED publication and actor unregister must still succeed
  using the designated trusted release callback.
- In another World, hold the actual IO worker inside an intentionally
  uncooperative pre-commit test hook. Close World on a separate thread,
  wait for the real five-second graceful IO window to expire, and
  observe the forced shutdown witness. Unblock the test-owned worker.
  Its final future must be exceptional and no account, ledger, owner
  or watermark bytes may have changed.

The Linux and Windows workflow runs the new R74 scenarios plus inherited
R63–R73 regression suites and Java 11 bytecode verification. Full server
build and Windows atomic persistence remain separate required gates.

## Limits and future work

- The test gate is **not installed into live LocalSession or decoded network
  dispatch**. Existing socket, pet, realtime and trade callback ingress
  remain outside the proof. Some of those paths are not routed through
  WorldCommandInbox at all.
- R74 does not physically stop a writer that has already begun
  uninterruptible filesystem publication. Its simulated non-cooperative
  hook waits **before** the real account mutation, and only the test
  wrapper obeys the forced-shutdown abort flag.
- `World.close` itself does not durably release R64. A forced shutdown
  with a failed final save remains an uncertain active owner and must
  not be marked as successfully logged out.
- R72 account, ledger, owner and marker files are still not one
  power-loss-atomic transaction. Ambiguous intermediate states are
  quarantined, not implicitly repaired.
- R64's UUID and offline epoch are **not credential-based authentication**.
  No live account authorization or owner-transfer protocol is implied.
- No performance, latency, FPS or real player stability claims are made
  from the offline tests.

**R74 DRAFT / OFFLINE ONLY / NO LIVE INTEGRATION.**
