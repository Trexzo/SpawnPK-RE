# R95 — independent fresh-JVM restart review of R94 failure quarantine

**OFFLINE TEST ONLY / DRAFT / STACKED ON R94 PR #3139 / NO DEPLOYMENT**

## Source-confirmed restart gap

R94's sticky per-actor, per-generation `uncertain` lease bit exists
only inside the cooperating World JVM. A killed/restarted process
does not retain that in-memory bit. Restart safety must instead
inspect the surviving R63/R64 account + R89 watermark + R90 producer
records under the real R60 OS lock; no record alone grants login.

## Fresh-process regression

The R94 offline regression already creates two isolated failure
fixtures with a real World and dedicated persistence IO worker:

1. **Early IO fault**: the writer fails before R90/R89 PREPARED and
   all account, ledger, owner, watermark and producer bytes remain
   unchanged. The R94 in-memory quarantine refuses unregister.
2. **Fault after real R89 PREPARED**: R90 and R89 both publish their
   PREPARED markers while holding R60; the fault interrupts before
   R64 account save. R94 refuses unregister, keeps all evidence and
   refuses late queued saves.

R95 now closes the parent World and launches a **new Java process**
for each fixture, with an explicit `r95-review` child entrypoint.
The child has no reference to the parent's R93/R94 lease table and
performs these checks:

- Read-only `R64OfflineSessionOwnership.inspect` must report the
  expected account/owner evidence, never authorize login, modify
  account files or attempt automatic recovery.
- On the PREPARED fixture, the real R89 reopen and R90 committed
  ordinal inspection both refuse with
  `R89_PREPARED_QUARANTINED`. An explicit offline R90 restart
  attempt also fails before a new producer incarnation can publish.
- On the early-failure fixture, R89/R90 checks report the original
  COMMITTED ordinal zero; this is only a *clean on-disk observation*,
  **not proof** that the previous World actor safely retired.
- The child compares all five canonical file byte arrays before
  and after each review and emits a verifiable, phase-qualified
  `R95_FRESH_JVM_REVIEW_PASS` marker.
- The parent checks the child's actual exit status and output,
  requires successful reviews of both fixtures and then reports
  `R95_OFFLINE_FRESH_JVM_IO_RESTART_REVIEW_PASS`.

Linux/Windows CI compile Java 11 bytecode and run these reviews in
addition to R94–R63 regressions. Independent server and atomic
persistence workflows remain separate.

## What R95 does not provide

The early-failure case illustrates a limitation: absent a durable
retirement or producer-shutdown proof, a clean disk image cannot
settle the lifecycle state across a process restart. R95 does not
introduce authenticated World producer identities or claim safe
automatic takeover. The PREPARED case is quarantined, not recovered.
No actual player login, source migration, network packet handling,
PvP/loot/reward replay or live World integration is enabled.
Tests of process termination do not prove host power-loss durability,
and the five files are still not a single atomic transaction.

**Keep DRAFT / UNMERGED / NOT DEPLOYED; require exact-head CI.**
