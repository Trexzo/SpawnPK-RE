# R81 — offline first-delivery receipts for combat projection fanout

**DRAFT / STACKED ON R80 / OFF BY DEFAULT / UNMERGED**

## Source-confirmed gap

The real `PlayerCombatResolutionService.resolveImmediateOwned` emits
immutable `CombatOutcome` facts synchronously to `DomainEventBus`.
Different subscribers can legitimately point to the *same*
`CombatOutcomeObserverGroup`, and its objective and match observers
perform additive updates:

- `CombatOutcomeObjectiveObserver` advances progress independently for
  every delivered `PLAYER_KILL` fact.
- `CombatOutcomeMatchObserver` adds participant/team score deltas for
  each matching fact.
- `CombatOutcomeCollectionLogObserver` uses a caller-owned absolute
  resolver and CAS batch, so it does not own an invented +1 rule, but an
  erroneously replayed delivery can call external resolver code again.
- R80's terminal/owner fences prevent *later unauthorized writes* but
  not duplicate delivery of the SAME still-authorized fact. If one group
  pass partially commits, naively retrying it starts from observer 0
  and can double-advance earlier objective or match projections.

`CombatOutcome` itself has **no authenticated producer-issued logical
event ID, source ordinal, replay key or persistence receipt**.
Constructing a dedup identity from attacker/victim strings + tick/type
would conflate distinct possible facts and would trust caller-controlled
strings. R81 intentionally does not invent such an identity.

## Explicit test-only implementation

`CombatOutcomeObserverGroup.installOfflineFirstDeliveryAdmissionForTest`
is an OPTIONAL hook, checked at observer index 0 **after R80's
current World owner/generation check and before the first observer
mutation**. It is not installed in production. Normal group behavior,
including legacy observer failure isolation, is unchanged without it.

`R81OfflineCombatProjectionReceiptLedger` is an in-memory, bounded,
synchronized `IdentityHashMap` keyed by the **exact immutable
`CombatOutcome` object instance** that R79's real PvP producer proved
during its DomainEventBus dispatch. Its `tryBegin(fact)` claim
happens before any objective/match/Collection Log consumer:

1. New, admitted exact fact: record **once**, then allow the existing
   observer group to continue.
2. Identical Java fact object submitted again (second domain
   subscriber, direct duplicate or reentrant entry): reject before
   any projection side effect.
3. Earlier observer threw after mutating state, R80 denied a later
   observer, or the Collection Log resolver failed: receipt remains
   **consumed** and any replay is rejected. There is **no retry,
   compensation or claimed rollback**.
4. Full receipt capacity: **reject new facts**, never evict an old
   receipt and accidentally reopen its replay. This is intentionally
   fail closed and may drop an otherwise valid new event.
5. Concurrent attempts to claim the same object are serialized:
   precisely one wins. Data structures retain a bounded number of
   strong object references for the lifetime of that fixture.

The ledger intentionally cannot identify a freshly reconstructed
CombatOutcome as a replay of an older object, even if every field is
identical. In the isolated source-exact R79 route, copying fields to
a new object does **not** confer the producer's thread-local identity
proof: direct DomainEventBus submission is rejected before reaching
the ledger. This is not a replacement for real producer-issued stable
receipt IDs if cross-object or cross-process deduplication is required.

There is **no disk ledger, socket integration, session login,
durable exactly-once guarantee, cross-JVM replay protection or
production hook activation**.

## Real-source regression

`R81OfflineCombatProjectionReplayTest` drives the real R79
`resolveImmediateOwned` PvP death, actual WorldPulse and DomainEventBus
with **two distinct domain subscriptions to one projection group**.
Without the optional receipt check the second subscriber can call the
same additive observers a second time.

The group uses the real R80 owner check and the actual
`ObjectiveProgressService`, `MatchSessionService`, and
`CollectionLogService` through their original projection observers.
The test Collection Log absolute value **7** is an artificial caller-
owned fixture, **not a recovered SpawnPK PvP kill-count policy**.

Independent scenarios:

- Normal real lethal kill: objective +1, participant kills +2, team
  kills +3, and synthetic absolute Collection Log 7. Two domain
  subscribers see each of the `PLAYER_KILL` and `PLAYER_DEATH`
  immutable facts, but the projection group processes each fact
  instance just once. A separate global domain notice still delivers.
- The exact kill fact is submitted directly twice again on a later
  World tick. Receipt refuses both replays and all projections and
  resolver-call counts remain unchanged.
- A NEW immutable fact with identical attacker, victim, tick,
  context and authority is directly published to the real World
  domain bus; R79's producer proof rejects it.
- A controlled interruption after objective projection, before match
  scoring: second domain delivery cannot replay the already committed
  objective or silently complete the previously skipped match/collection
  work. The same holds after match score commit, before Collection Log.
- Collection Log's caller-owned count resolver fails after objective
  and match scores have already been applied. Legacy observer failure
  isolation still reports this failure. Duplicate delivery does NOT
  call the resolver a second time, nor update a Collection Log.
- Capacity 1 admits the first real fact, refuses the second distinct
  real fact, and retains the original receipt; it never evicts
  receipts to allow an old replay.
- Eight concurrent attempts to claim the SAME object allow exactly
  one. A distinct object with matching string fields is treated as
  distinct by the ledger, demonstrating its explicit limitation.
- Inherited R63–R80, original consumer, real PvP and public
  DomainEventBus API tests are checked independently on Linux and
  Windows with Java 11 bytecode.

## Explicit limitations and next work

**First delivery is not exactly-once projection completion.**
A real process crash after one observer's write but before another
requires durable, transactionally coordinated source-operation IDs,
per-projection idempotency receipts, and recovery policy. This R81
offline in-memory ledger neither survives process restart nor
reconstructs incomplete projections. It never retries a partial
fanout because doing so could double-count earlier writes.

A newly created CombatOutcome—even with identical attacker/victim,
type and tick—is not proven the same *logical* combat event.
This cannot be solved safely by hashing public string fields.
A later R82 step could audit trusted source-issued event identity
and per-projection claim/commit semantics, but must not invent an
unknown live server protocol or claim atomic multi-file durability.

R80 mutation admissibility and R79 authenticated-in-test source proof
remain prerequisites. No application/runtime code automatically
installs the new hook. R64 offline UUIDs are not login credentials,
the R63/R64/R72 multi-file protocol remains non-atomic across power
loss, and R74 cannot cancel arbitrary disk writes already in flight.

No live game stability, latency, FPS or real-world rewards claims.

**R81 OFFLINE ONLY / DRAFT / UNMERGED / NO LIVE ACTIVATION.**
