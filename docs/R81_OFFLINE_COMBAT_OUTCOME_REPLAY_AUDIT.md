# R81 — offline at-most-once combat outcome projection delivery

**DRAFT / STACKED ON R80 PR #3113 / OFF BY DEFAULT / UNMERGED**

## Source-confirmed replay problem

R80 checks attacker/target World generations before each projection write
and rejects writes after quiescence. Objective and match projections are
**increments**, but Collection Log uses a caller-supplied absolute count.
Repeated **downstream delivery of the same verified fact object** could
double-count earlier committed objectives and scores after partial or
complete fanout.

There is no authenticated durable event UUID, per-consumer checkpoint,
multi-service transaction journal or replay recovery policy at the
recovered PvP bridge. The tuple `(attacker, victim, type, worldTick)`
is **not** a safe deduplication identity: distinct legitimate facts may
share all four fields.

The original `CombatOutcomeObserverGroup` specifically rejects
queue/retry/replay behavior as part of its isolated responsibility.
R81 leaves the class byte-for-byte unchanged from R80 and places a
separate opt-in **offline delivery wrapper** at the domain listener.

## Implementation

- `R81OfflineCombatOutcomeDeliveryGuard` wraps one already R80-gated
  `CombatOutcomeObserverGroup`. It can be installed only by explicitly
  wiring the wrapper in an offline listener. No LocalSession, network
  or production code path creates this wrapper.
- Before entering the group's first observer, the wrapper calls R80's
  same-pair World generation/quiescence gate. An off-World, stale or
  quiesced call is dropped without consuming an identity entry.
  The original R80 group and per-binding checks remain active too.
- `R81OfflineCombatOutcomeReplayFence` is a synchronized identity map
  of exact `CombatOutcome` references, not a string/tuple/field
  fingerprint. It records the first entry **before** calling the
  group. Identical object re-delivery, including recursive wrapper
  entry, is rejected before a second write.
- The first delivery remains consumed even if an observer throws,
  the owner quiesces, or an external absolute resolver fails.
  **No rollback and no retry** of an incomplete fanout is attempted.
  Earlier committed changes remain.
- R79's actual real PvP producer-to-DomainEventBus identity proof is
  required upstream. Neither R80's actor strings nor R81's ledger
  provides proof by itself.

The identity map retains strong object references for one short-lived
isolated test fixture. It is not a bounded, durable, authenticated,
distributed or deployable deduplication ledger.

## Verification scenarios

`R81OfflineCombatOutcomeReplayTest` uses the genuine R79-attested
`PlayerCombatResolutionService` lethal PvP facts, real
`DomainEventBus`, R80 World checks and actual objective, match
participant/team and Collection Log services. The listener calls the
delivery wrapper **twice with the same fact reference while inside**
the real R79-authenticated bus callback.

- Normal kill/death facts: two exact-instance duplicates refused.
  Objectives progress once, participant +2 once, team +3 once,
  Collection Log resolver once (synthetic fixture absolute count 7).
- Second objective binding throws after first commit; group keeps
  normal failure isolation. A duplicate cannot double-increment the
  already committed first objective.
- Second match binding throws after participant +2; team write is
  not committed. Duplicate cannot apply participant score again.
- Collection Log resolver throws after objectives/match commit.
  Duplicate delivery does not retry the external resolver.
- Attacker quiesces after objective. R80 stops further projections
  and subsequent delivery. First committed objective persists.
- Nested same-fact callback through the R81 wrapper is denied.
- Distinct objects with the same attacker, victim, type, source and
  tick are not falsely coalesced (identity-only unit check).
- Off-world wrapper call is denied before consuming the ledger entry,
  then a genuine R79 producer fact remains permitted.
- A separately constructed fact published directly into the domain
  bus is rejected by R79 source proof and consumes no R81 entry.
- Without explicit R81 wrapper wiring, the original R80 group path
  is unchanged and duplicating callback delivery behaves exactly
  as before. Original `CombatOutcomeObserverGroupTest` explicitly
  preserves its no-replay/async/transport contract.

Linux/Windows R81 GitHub CI compiles Java11 bytecode and runs the
R81 regression, R80 and inherited R63–R79 safety regressions and
original observer tests. Separate full Gradle server and Windows
atomic-persistence jobs must be inspected on the exact final head.

## Unresolved durability and production questions

At-most-once for **one in-memory fact object in one offline delivery
wrapper in one JVM** is not exactly-once across restarts, several
consumers or reconstructed copies of an event. This test has no real
persistent checkpoint, stable cross-process event ID, transaction
recovery or safe retry. It does not install protection into actual
PvP sessions, production score changes, rewards, persistent profiles,
client assets, network traffic or account ownership. Physical crash
atomicity and trustworthy partial-operation recovery remain open.

**R81 OFFLINE ONLY / DRAFT / UNMERGED / NO LIVE ACTIVATION.**
