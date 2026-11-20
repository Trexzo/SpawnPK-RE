# R81 — offline at-most-once combat outcome projection fanout

**DRAFT / STACKED ON R80 PR #3113 / OFF BY DEFAULT / UNMERGED**

## Source-confirmed replay problem

R80 checks attacker/target World generations before each projection write,
and safely rejects subsequent writes after owner quiescence. However the
projection bridge's objective and match writes are **increments**, while
Collection Log consumes a caller-owned absolute count. Re-delivery of the
*same verified* `CombatOutcome` to an `CombatOutcomeObserverGroup` would
repeat earlier committed objective and match updates. A partial first delivery
can occur when a later binding or resolver throws (legacy group failure
isolation) or an owner becomes terminal between projections.

No durable event UUID, consumer checkpoint, multi-service commit journal or
transaction coordinator exists at this recovered boundary. The tuple
`(attacker, victim, type, worldTick)` is **not** a safe event ID: two
independent legitimate facts might share those fields. R81 does not create,
infer or persist a production event identity.

## Implementation

- `CombatOutcomeObserverGroup` accepts a **separate offline-only** replay
  admission installed via `installOfflineReplayAdmissionForTest`. Installation
  requires R80's offline owner-admission hook already installed; duplicate
  replacement is rejected. Uninstalled groups keep their previous behavior.
- Immediately **after R80's owner preflight before observer zero** and before
  the first projection write, the group calls the replay admission once per
  incoming fact. If a previous delivery entered, it stops the entire fanout.
- `R81OfflineCombatOutcomeReplayFence` uses Java reference identity, not
  attacker/victim/source strings or field equality. A synchronized
  `IdentityHashMap` admits the first instance once. Duplicate reference
  delivery (including recursive group entry) is rejected before any write.
- The first delivery is recorded **before** any observer executes. If
  an observer later fails, a resolver throws, or the actor becomes terminal,
  R81 **does not retry unfinished projections**, roll back earlier writes,
  or convert an error into a fresh event. Existing R80 quiescence and R79
  producer proof remain necessary.
- R81's ledger holds strong object references and is intentionally scoped to
  a short-lived, isolated test fixture. It is **not** a durable, bounded,
  authenticated, distributed or deployable deduplication ledger.

## Verification scenarios

`R81OfflineCombatOutcomeReplayTest` uses the **real R79-attested
PlayerCombatResolutionService lethal PvP outcome**, real
`DomainEventBus`, the R80 World owner gate, and real objective,
participant/team score, and Collection Log services.

The listener calls the group **twice with the same object reference while
inside the real R79 source-attested callback**. This models duplicate
downstream delivery without forging a new source fact.

- Normal kill/death pair: two exact-instance duplicates rejected. Both
  objectives advance once, participant score +2 once, team score +3 once,
  Collection Log resolver once (synthetic absolute fixture count 7).
- A second objective binding throws after the first has committed: later
  match/Collection Log observers still run under existing failure isolation,
  but repeated exact fact cannot double-count the first objective.
- A second match binding throws after participant score commits: duplicate
  cannot increase participant score again; first successful objective and
  Collection Log updates remain committed.
- Collection Log absolute resolver throws after objective and match
  commits: repeated fact cannot retry external resolver or double-apply
  increments.
- Owner quiesces after objective: R80 stops later writes, including repeated
  invocation; the first committed objective remains, no false rollback.
- Recursive delivery into the group during its first observer is denied.
- Two separate objects with exactly the same fields are not coalesced by
  the identity ledger (unit-level identity check only, not raw publication).
- A rejected R80 owner preflight does not burn the replay entry.
- Replay hook replacement or installation without R80 hook is refused.
- A group without R81 installed still follows the unchanged original
  delivery path in a fully isolated fixture; no production behavior change.

Run both Linux and Windows R81 CI with Java11 bytecode verification, R80
and inherited R63–R79 regressions, and all original observer tests. A
separate full Gradle server build and Windows atomic-persistence run
must be checked before claiming full cross-platform verification.

## Boundaries and next steps

At-most-once for **one in-memory instance, in one group, in one JVM**
does not imply exactly-once across restarts, multiple consumer groups,
or future independently reconstructed copies of an event. The test
does not install protection into real LocalSession/PvP, real player
rewards, profiles, network sockets or persistent data. Physical crash
atomicity, durable replay and safe partial-operation recovery remain
unresolved, and would require authenticated stable event IDs plus
per-consumer transactional checkpoints (and a designed recovery policy).

**R81 OFFLINE ONLY / DRAFT / UNMERGED / NO LIVE ACTIVATION.**
