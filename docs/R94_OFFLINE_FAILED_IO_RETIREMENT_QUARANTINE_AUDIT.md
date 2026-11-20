# R94 — failed-IO retirement quarantine for the cooperative World lease

**OFFLINE TEST ONLY / DRAFT / STACKED ON R93 #3138 / NO LIVE WIRING**

## Why R93 is not sufficient

R93 prevents a cooperating actor from unregistering while a durable
save lease is still active, then lets the caller explicitly retry
unregister after all leases drain. But a count of zero only establishes
the writer is no longer running: it says nothing about whether R89,
R90 and R64 have completed their multi-file durable protocol.

An IOException can occur before the write, after R90 PREPARED,
after R89 PREPARED, after an account change or between markers.
Releasing the lease on any of those failures must not silently
authorize player retirement or a new login/owner.

## R94 opt-in fail-closed contract

- The normal R93 `installForTest` path is preserved unchanged.
- `installFailureQuarantineForTest` opts into a sticky per-WorldPlayer
  identity and generation `uncertain` bit in its existing lease state.
- A writer that does not return normally sets `uncertain` in the
  lease-finally section while releasing its active count.
- The existing test-only `World.unregisterPlayer` gate marks the
  actor retiring; the R94 variant returns false if any save is still
  active **or** if the generation is uncertain. It never sleeps or
  holds the World lifecycle lock across IO.
- A subsequent capture is still possible under the real World actor,
  but its IO admission is rejected before PREPARED or account mutation
  by `R94_IO_UNCERTAIN_QUARANTINED`.
- No code clears the uncertainty bit or automatically retries a
  failed write. It is deliberately conservative even when the initial
  error occurred before any canonical mutation.

## Real executable fault evidence

The existing `R91OfflineWorldCaptureProvenanceTest` regression also
runs two isolated R94 fixtures:

1. A hook throws before R89 admission. The writer future fails,
   all five canonical account/ledger/R64/R89/R90 record bytes are
   unchanged, the lease drains and unregister is **still refused**.
2. A test-only fault is installed on the actual single IO worker
   thread and throws at `AFTER_PREPARE_BEFORE_ACCOUNT` inside R89,
   while R60 is held. Both R90 and R89 PREPARED records exist; the
   account, R63 ledger and R64 owner are unchanged. The failed save
   releases its lease yet unregister and late account writes are
   refused. Both PREPARED records remain available for diagnosis.

Linux/Windows R94 workflow compiles Java 11-compatible bytecode and
runs the entire R94–R63 inherited regression sequence. Independent
full-server Gradle and Windows atomic-persistence checks still apply.

## Explicitly unresolved

This models only *cooperating* offline World unregister and IO
admission, not production shutdown, forced close or live re-login.
There is no automatic recovery, proof of physical power-loss
durability, atomic multi-file transaction, guaranteed cancellation of
an unresponsive IO worker, hostile-JVM authentication or exactly-once
PvP/rewards. A direct filesystem writer can bypass these hooks.

The failure bit is in-memory; a process restart must treat R89/R90
PREPARED or other ambiguous disk evidence as quarantined under
the existing read-only checks, not infer success from the former
lease. R94 does not add a durable lease/retirement record.

**Keep draft, unmerged and undeployed until exact-head CI succeeds.**
