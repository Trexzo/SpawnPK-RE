# R83 — offline PvP event identity and per-consumer checkpoint feasibility

**DRAFT / STACKED ON R82 PR #3127 / OFFLINE-ONLY / NO LIVE WIRING**

## Source-confirmed identity gap

`CombatOutcome` contains `attacker`, `victim`, `type`, `context`,
`worldTick`, and `sourceAuthority`, but **no stable, authenticated,
durable event ID**. The `PlayerCombatResolutionService` creates
separate kill and death facts for one lethal hit. R79 authenticates
the precise source-created object *only during synchronous event-bus
delivery*, not during cross-process replay. R81 tracks Java object
identity inside one short-lived wrapper; this cannot survive restart.

A candidate composite key of actor refs, event type, context, world
tick and authority is **not safe**. Separate legitimate PvP outcomes
can share those fields, and strings are spoofable outside R79's source
proof. A local event sequence assigned downstream is unique only to
that observer instance; it is not a producer-side durable identity.
The R83 test demonstrates this collision and sequence reset.

## Checkpoint observation experiment

New **offline-only** Java classes:

- `R83OfflineCombatCheckpointAudit` stores a short-lived
  `IdentityHashMap<CombatOutcome, EventRecord>` with separate
  `OBJECTIVE`, `MATCH`, `COLLECTION` observation phases:
  `ABSENT`, `ENTERED`, `RETURNED`, `UNCERTAIN`.
- `R83OfflineCombatCheckpointObserver` wraps the existing, real
  observer callbacks. R80 first revalidates the World actor;
  immediately before a matching callback, the audit records
  `ENTERED`. Only a normal return records `RETURNED`. A
  `RuntimeException` marks the consumer `UNCERTAIN`, because
  writes might already have committed.
- The diagnostic sequence counts the first observed event instances
  in this one fixture. It cannot be used for production replay.
  A second entry of the same fact/consumer pair is rejected;
  a phase cannot be changed from `UNCERTAIN` to `RETURNED`.
- `recoveryDecision` always returns
  `DENY_AUTOMATIC_REPLAY` irrespective of phase.
  Neither `RETURNED` nor `UNCERTAIN` implies a transactional or
  durable checkpoint. Even a successful return may be lost by a
  crash before a journal flush. A recorded `ENTERED` could mean
  anything between no write and a completed mutation.

**This is observation, not event sourcing, a recovery journal, or an
at-least-once consumer. No production observer, group, service or
DomainEventBus source was modified.**

## Real source and negative controls

`R83OfflineCombatCheckpointAuditTest` executes genuine
`R79OfflinePairedPvpOwnershipGate.resolveForTest` lethal PvP,
the actual WorldPulse + DomainEventBus and its exact-object
proof, R81 at-most-once delivery, R80 dual-generation owner
admission, and the original objective, match and Collection Log
services (a synthetic caller-owned absolute count of 7).

The test independently checks:

1. Normal callback return for all three projections. Each outcome
   is only admitted once despite two deliveries of its exact object;
   no additional objective progress, scores, or resolver calls.
2. First objective binding increments before the second binding
   throws. Objective status is `UNCERTAIN`, match/Collection Log
   have returned; the first objective mutation is **not** rolled back
   or replayed.
3. Match score commits and a test wrapper then throws. The match
   phase remains `UNCERTAIN` despite the visible committed +2 score.
4. Collection Log absolute-count CAS commits and a wrapper then
   throws. Collection phase remains `UNCERTAIN` even though the
   observed absolute count is now 7.
5. An attacker quiesces after the objective callback. R80 stops
   later consumers, leaving their phases `ABSENT`. The earlier
   objective remains committed.
6. Distinct event objects with **identical** type/attacker/victim/
   tick/authority produce the same candidate key, but different
   local diagnostic sequence values. A fresh audit restarts sequence.
7. Repeating a consumer entry, reverting `UNCERTAIN` to
   `RETURNED`, or using raw matching-string facts as R79 source
   proof is refused. A forged publication creates no checkpoint.

The isolated fixture never touches a network socket, player account,
existing persistence file, rewards, claim APIs, or real server PvP.

## What remains before production-safe replay is even designable

An authenticated producer-generated event ID must be assigned
*before* publication and durably tied to the state transition.
There would need to be explicit per-consumer IDs, versioned
projection rules, and a persistence protocol that atomically
commits a mutation with its checkpoint or uses a correct outbox/
inbox approach. One must decide how to handle exceptions and
partial writes, crash-ordering, world owner transfer, pruned
checkpoint state, multiple JVMs, and terminal logout before
supporting any automatic retry. None of these mechanisms is
asserted to exist at this recovered boundary.

This R83 work is an **investigation/test harness** documenting why
existing fields and one-process callbacks cannot justify recovery.

**R83 OFFLINE ONLY / DRAFT / UNMERGED / NO LIVE ACTIVATION.**
