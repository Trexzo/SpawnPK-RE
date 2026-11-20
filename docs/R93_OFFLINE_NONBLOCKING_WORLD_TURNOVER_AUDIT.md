# R93 — cooperative offline nonblocking World turnover during durable saves

**EXPERIMENTAL / OFFLINE-ONLY / DRAFT / STACKED ON R92 PR #3137 / NO DEPLOYMENT**

## Why this exists

R92's strict same-World player-generation fence holds the World
lifecycle lock for the entire R60/R63/R64/R89/R90 filesystem
transaction. That is a sound cooperative offline exclusion proof, but
slow or stalled disk IO would also block the World execution thread,
login, tick scheduling and unregister. It is not acceptable as a
latency-sensitive production feature.

R93 models a more useful contract without pretending that a short
preflight ownership check alone can protect a later disk commit.

## Test-only contract

- A stamped immutable World snapshot carries its actual source
  `WorldPlayer` reference and generation, as in R92.
- The R93 IO gate calls the already existing
  `World.withOpenPlayerOwnershipIfCurrent` to atomically verify the
  player membership and acquire an **in-memory lease**. This short
  critical section does not execute disk IO.
- `World.unregisterPlayer` has one new nullable, test-installed
  `OfflineUnregisterAdmission` hook. Under the same lifecycle lock,
  it immediately marks that actor generation as **retiring**.
- With any outstanding leases, the unregister call returns **false**
  without removing the actor or blocking the World thread. Its caller
  MUST retry after the write has completed.
- The already accepted save finishes through unchanged
  R89/R90/R64/R63 durable admission, outside the World lifecycle
  lock. The lease is released in a `finally` block even if IO fails.
- Once retirement was requested, a subsequent queued save from the
  same actor/generation is refused before any R90/R89 PREPARED
  record or account mutation. After leases drain, unregister can
  succeed and only then can the replacement actor register.
- The original `World.unregisterPlayer`, normal R91 installation,
  optional R92 blocking lease, and actual production sessions retain
  their behavior unless the isolated test specifically installs R93.

This is **not** a wait-free durable transaction. If a write never
completes, unregister deliberately remains pending until a separate
shutdown/failure policy resolves the operation. No in-flight write is
assumed rolled back merely because the caller wants to retire.

## Executable regression

`R91OfflineWorldCaptureProvenanceTest` now also exercises
`R93_OFFLINE_NONBLOCKING_WORLD_TURNOVER_PASS`:

1. In a real isolated World, capture and enqueue a stamped save.
2. At the dedicated IO worker, acquire the actual source-generation
   lease, then block immediately before R89/R90 publication.
3. Submit an owned World action while IO remains blocked: it runs,
   proving the lifecycle lock is **not** held during the test IO stall.
4. Request unregister: it returns pending immediately, retains the
   current actor, marks it retiring, and leaves canonical files
   unchanged.
5. Capture and queue an additional snapshot from that still-owned
   but already-retiring actor. Release the first save: it commits
   normally. The later save is refused before canonical disk changes.
6. Retry unregister: it now succeeds. Register a fresh player with
   the same account and commit a new World snapshot in the same
   R90 producer-incarnation/ordinal sequence.
7. Reopen the durable producer record and assert highwater; compare
   canonical account/ledger/owner/watermark/producer bytes around
   the refused write.

Linux and Windows CI compile Java 11-compatible bytecode and run
this scenario with inherited R92/R91/R90–R63, other local World and
full Gradle/atomic-persistence coverage.

## Boundaries and missing real integration

This works only when **every World unregister and every account
writer** cooperates with the optional test gate, and when the caller
actually retries a pending unregister. Production LocalSession does
not use the retry contract and it would be unsafe to enable this
without first integrating quiescence, final-save, close, timeout,
reconnect and lifecycle handling.

R93 does **not** authenticate the stamped World actor against hostile
in-process code, prove physical power-loss durability, make the
multi-file R63/R64/R89/R90 commit atomic, recover PREPARED states,
provide cross-JVM ownership, or prove PvP/reward exactly-once delivery.
A direct writer outside the gate bypasses it.

**DRAFT, UNMERGED, UNDEPLOYED until exact-head CI is verified.**
