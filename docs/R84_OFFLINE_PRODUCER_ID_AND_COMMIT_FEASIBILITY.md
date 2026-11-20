# R84 — producer event identity and atomic checkpoint ordering feasibility

**OFFLINE EXPERIMENT / DRAFT / STACKED ON R83 / NO LIVE ADAPTER**

## Existing source boundary (verified against R83 head)

`PlayerCombatResolutionService.resolveImmediateOwned` computes damage,
applies `PlayerLifecycleService.applyDamage` under actor ownership,
then calls `finish`. On a newly lethal result, `finish` constructs and
sends two distinct `CombatOutcome` objects: `PLAYER_KILL` and
`PLAYER_DEATH`. Neither fact contains an event ID; the two publications
occur **after the HP mutation**. Publication callbacks catch/log runtime
exceptions without reverting a completed HP transition.

`R79OfflinePairedPvpOwnershipGate` verifies the exact fact instance
during the synchronous WorldPulse callback and validates both actor
generations; this is not cross-restart evidence. R81 and R83 record
only process-local fact object identity or callback phases. The
existing R64/R69-R72 account persistence experiments use explicit
ownership, write fences and failure quarantine, but **do not implement
an atomic combat-HP plus projection-checkpoint transaction**.

Therefore an event ID attached in a downstream observer **cannot**
make the already-applied HP mutation and later projections atomic.
An event identity trusted across JVMs must originate in the
authoritative pre-mutation operation, be included in the same durable
write as that operation, and be protected by owner-epoch fencing. It
must identify distinct kill/death facts for a single lethal operation
without using an ambiguous actor/tick composite key.

## R84 disposable model

`R84OfflineAtomicCombatRecord` is deliberately **not connected** to
the actual resolver, game World, real account files, or event bus.
It models only *one* lethal event in a new disposable fixture
directory, with a UUID-like producer-stream identifier, owner epoch,
sequence number and distinct role suffixes `KILL`/`DEATH`.

A single on-disk text record contains:

- format version, producer stream, expected owner epoch and sequence
- simulated 10HP -> 0HP state and paired event identities
- objective increment and checkpoint; match increment and checkpoint
- caller-owned absolute Collection Log count and checkpoint
- SHA-256 text digest that detects torn or altered records (not
  authentication against an attacker who can rewrite the file)

Every modeled update uses **one** UTF-8 snapshot written to a staged
file, `FileChannel.force(true)`, and an `ATOMIC_MOVE` replacement on
the same filesystem. There is deliberately **no non-atomic fallback**.
A failure before the move leaves the previous canonical record;
a failure after the move but before acknowledgment may leave the new
record. Either failure poisons the current instance, requiring an
explicit reopen and validation before further model operations.
Missing, corrupted, incompatible or wrong-epoch files are refused.

Neither `force(true)` nor atomic rename alone proves storage
controller, filesystem directory, or **power-loss durability**.
No actual process is killed in the regression. The failure points
are injected exceptions; no service restart, cross-JVM IPC or
production transaction is proven. The use of a UUID-like stream
is an identity-design exercise, **not a real authentication
mechanism or an assertion of zero collision probability**.

## Tested failure matrix

`R84OfflineAtomicCombatRecordTest` on Windows and Linux verifies:

1. Producer source model creates two distinguishable IDs before
   its simulated HP commit. Both remain byte-identical across reopen.
2. An injected crash before source replacement leaves HP=10 with
   no event identities; a crash after replacement but before ack
   leaves HP=0 and both IDs. Repeating the lethal source operation
   on a reloaded committed record is refused.
3. Each of objective, match and collection performs value and
   checkpoint publication in the **same record**. Fault injection
   before replacement preserves neither; after replacement preserves
   both, and a later duplicate operation does not increment again.
4. A failed write forbids reuse of the same object until the
   canonical record is reopened and checked.
5. Wrong owner epoch, second initialization, malformed/truncated
   canonical file and missing file fail closed without auto-repair.
6. **Critical negative control:** two independently opened writers
   that share the same nominal epoch can each atomically replace the
   file. A stale second writer can erase the first writer's already
   committed checkpoint. `ATOMIC_MOVE` is not compare-and-swap,
   and owner epoch as a stored field is insufficient without a real
   OS/process lock, freshness preconditions and an authoritative
   owner lease.
7. Prior real-R79/R80/R81/R83 offline combat source and projection
   regressions remain in the dedicated CI suites.

This model does **not** automatically retry any actual game
operation or grant real observer delivery.

## Required design work before any deployment

- Define a World-authoritative combat transaction boundary and
  producer-sequence allocation **before HP mutation**. This requires
  changing live resolver and persistence contracts; R84 does not.
- Tie the producer's event, changed HP, both outcome roles and
  owner generation/epoch to one journal commit or audited outbox.
  A cross-account victim/attacker mutation can demand a
  coordinated multi-record transaction; a single account snapshot
  cannot guarantee that by itself.
- Validate source authentication, schema, IDs and version across
  restarts and inter-JVM takeover; preserve the R79 authority rules.
- Add a monotonic commit generation/fencing token under a real
  OS file lock, with stale-writer and owner-takeover regressions.
  Explicitly refuse partial or orphaned batches.
- Plan actual crash, process-kill, atomic-rename and fsync tests on
  supported platforms; establish handling for directory sync,
  failures, corruption, disk-full, and cleanup of stage artifacts.
- Implement a **separate** idempotent transaction for each consumer
  with stable projection rule version and checkpoint written together
  with the mutation. R83 proved callback `RETURNED` is not a
  checkpoint. Migration of existing records requires its own
  compatibility design.
- Decide who may redispatch a committed event after restart and
  whether event delivery can happen after actor logout, player
  replacement or match completion. R84 supplies **no** authorization
  to replay, restart, unquiesce actors or claim rewards.

**R84 is a proof-of-constraints and simulated crash-ordering test, not
a complete outbox/inbox system, not a durable event log, and not
production exactly-once or at-least-once delivery.**

**DRAFT / UNMERGED / NO PRODUCTION SESSION, ACCOUNT, OR WORLD CHANGES.**
