# R89 — durable capture watermark: crash-safe refusal prototype

**DRAFT / STACKED ON R88 PR #3133 / OFFLINE ONLY / NO PRODUCTION WIRING**

## Finding from the real R71/R64 source

R71's `highestAcceptedCaptureOrdinal` is scoped to an in-memory
`R71OfflineOrderedPersistenceAdmission` instance. An owner-consistent
account write can therefore be followed by JVM termination and a fresh
R71 instance whose high-water mark starts at zero. R64 protects a stale
*account-generation/owner token*, but does not by itself establish the
provenance or ordering of World-thread capture ordinals once a new
writer is deliberately given a current account token.

Naively appending a separate persisted ordinal **after** an R64 account
save would introduce a lost-watermark window if the process dies
between account publication and ordinal publication.

## Offline R89 admission protocol

`R89OfflineDurableCaptureAdmission` is an entirely separate, optional
test implementation of
`WorldPlayerPersistence.OfflineOrderedSaveAdmission`. No live
`World`, `LocalSession`, owner login, packet dispatcher or account
publisher installs it. R71 remains unchanged for its own regressions.

R89 adds a test-owned canonical
`sessions/opensrc.r89-watermark.properties` file. Every record stores:

- Exact R64 current owner UUID and epoch, plus R63 account generation,
  snapshot SHA-256 and R59 journal revision;
- Last **committed** World-thread capture ordinal;
- `COMMITTED` or `PREPARED` phase and, when prepared, pending capture
  ordinal and pending snapshot hash;
- SHA-256 record checksum for unintentional corruption detection,
  not authentication.

All operations use **the same real R60 OS file lock** as R63/R64. A
capture first reopens and strictly validates the R64/R63/R59 owner
and current `COMMITTED` watermark record under that lock. Stale
ordinals, mismatched owner/generation, corrupt data, abandoned
atomic-publication stages or an outstanding `PREPARED` record are
refused before any account mutation.

A new capture uses this sequence under that single lock:

1. Atomically publish a **PREPARED** watermark with the existing
   committed ordinal and the exact incoming capture ordinal/hash.
2. For changed snapshot data, call the actual
   `R64.saveOwnedUnderLock`, publishing the real offline account,
   generation ledger and owner file in their established ordering.
   For an identical snapshot, perform no account mutation.
3. Atomically publish a **COMMITTED** watermark bound to the
   resulting R64 token and the newly accepted capture ordinal.
4. Update the in-memory token/ordinal and acknowledge the caller.

Every exception after preparing poisons the current writer handle.
A fresh writer can reopen **only** if the disk watermark is
`COMMITTED` *and* the current R64/R63 owner and account metadata
match. A `PREPARED` record remains quarantined, even when the
underlying R64 account/ledger/owner files happen to be consistent.
No automatic cleanup, adoption, guessing, replay or owner recovery
is permitted.

This is a conservative fail-closed *intent marker*, **not an atomic
transaction across four account files**.

## Cross-JVM and real-world snapshot tests

`R89OfflineDurableCaptureWatermarkTest` creates actual disposable
`PlayerSnapshotCodec` account records and uses a real
`World.isolatedForTest` actor and its
`WorldPlayerPersistence` IO worker:

- World-thread captures ordinals 1 and 2; the newer snapshot
  commits first; older capture refuses without changing any
  account/ledger/owner/watermark bytes.
- A fresh Java admission instance reopens the committed ordinal 2.
  A new isolated World that resets its local ordinal to 1 is
  **denied**, demonstrating that persisting the highwater alone
  does not implement producer-ordinal resume or authenticate an
  old capture. A newer ordinal 3 can be explicitly supplied in
  a test, then is also durable.
- Identical snapshot capture ordinals are persisted even when
  the account bytes remain identical. After reopening, replay of
  the old ordinal is refused.
- R64 explicit ownership takeover makes the old R89 gate stale
  and the new owner mismatched. A **test-only explicit rebind**
  is required; it checks the predecessor's clean `COMMITTED`
  watermark, exact account token and one-step epoch advancement.
  It then resets ordinals for the successor **only in this test
  fixture**.
- A real child JVM is forcibly terminated while holding the R60
  lock at each boundary:
  - `AFTER_PREPARE_BEFORE_ACCOUNT`: old account untouched,
    watermark `PREPARED`, fresh gate refuses;
  - `AFTER_ACCOUNT_BEFORE_WATERMARK`: R63/R64 account may be
    committed, watermark still `PREPARED`, fresh gate refuses;
  - `AFTER_WATERMARK_BEFORE_ACK`: watermark and R63/R64 account
    committed, explicit reopen sees the highwater and refuses a
    duplicate, while a genuinely newer ordinal may commit.
- A staged watermark file matching the actual
  `AtomicAccountPublisher` R51 pattern and a checksummed
  record mutation both cause strict refusal with no automatic
  evidence deletion.

Linux/Windows dedicated R89 CI also run R88–R63 regressions
and Java 11 bytecode checks. Independent full Linux server
integration and Windows atomic-persistence CI must pass before
this experimental round is considered verified.

## Critical non-goals and remaining gaps

- The added watermark is a **fourth independently atomically
  replaced file**. Preparing it first safely quarantines ambiguous
  partially committed states, but **cannot prove exactly once**,
  guarantee availability, or make multi-file publication atomic.
- A `PREPARED` record is intentionally unresolved: there is **no
  repair protocol**, no authenticated recovery authority, no
  publication replay and no automatic owner token refresh.
- R89's explicit initial bootstrap is permitted only for a new
  disposable test account. An existing production account cannot
  securely adopt highwater zero without authenticated migration
  and prior capture-provenance checks.
- Owner rebind assumes an explicitly authorized offline R64
  takeover. It does not authenticate owner UUIDs or bind a
  restarted World actor's capture sequence. A high numeric
  ordinal is not proof that a snapshot came from the correct
  producer. Existing non-cooperating R71/raw R64 writers do not
  know R89 and can bypass its separate watermark.
- Neither `AtomicAccountPublisher` file force nor atomic rename
  proves full host-power-loss durability or directory-fsync
  semantics. The test deliberately kills child **processes**.
- Tests do not grant real player login or mutate real accounts,
  World/gameplay instances, PvP, reward delivery, trade, network
  dispatch or combat event processing.

**R89 DRAFT / UNMERGED / NOT DEPLOYED / OFFLINE ONLY.**
