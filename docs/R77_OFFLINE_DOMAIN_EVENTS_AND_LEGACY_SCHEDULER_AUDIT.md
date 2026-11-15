# R77 — offline owner-scoped domain publication and legacy event-source audit

**DRAFT / STACKED ON R76 / OFF BY DEFAULT / UNMERGED.**

## R76 boundary and discovered risk

R76 introduced explicitly owner-tagged, generation-checked test-only
`WorldEventQueue.scheduleOwnedForTest` events and WorldPulse periodic
callback fencing. But the existing low-level
`WorldEventQueue.schedule(long,Runnable)` intentionally has no player
identity; it still runs arbitrary world-scoped callbacks. R76 correctly
did not guess that every untagged closure belongs to a departing player.

An additional real execution path is `spk.event.DomainEventBus`.
`DomainEventBus.publish` requires an authoritative World execution
context and dispatches listeners synchronously in priority order.
**World execution context alone is not an R64 owner/generation or
post-final logout authorization check.** Existing domain event types,
including `CombatOutcome`, carry semantic information but do not
supply R64 owner UUID and World generation as an authenticated
session-bound identity. Consequently, R77 must not silently classify
all domain events or combat outcomes as belonging to the current
player merely by their attacker name.

## Test-only implementation

`DomainEventBus.installOfflinePublicationAdmissionForTest` is an
optional injection seam. When **absent**, the existing direct listener
invocation is preserved. When explicitly installed in an isolated
World, the guard runs immediately **before each matching listener**
rather than once before the whole publication. This matters if a
higher-priority handler itself triggers terminal quiescence; the next
listener must not use the earlier approval to mutate after the
quiescence boundary.

The core event package remains transport-agnostic; the hook operates
only on `DomainEventBus.Event` and a checked callback. No World,
EntityId, login token, opcode, socket or local-network implementation
is imported into `spk.event`. The public event API boundary test is
retained in CI.

`R77OfflineOwnedDomainPublicationFence` is an **offline-only**
adapter in `spk.local`. A purpose-built offline test event
implements `OfflineOwnedEvent`, supplying an explicit World
EntityId and generation. The adapter resolves that current owner,
enters `World.withOpenPlayerMutationOwnershipIfCurrent`, and holds
the real mutation lock during one subscriber callback while
rechecking R74 quiescence. The following are denied with checked
exceptions before any listener side effect:

- missing or invalid owner metadata;
- an unregistered or superseded World generation;
- an explicitly quiesced World actor; and
- a generation lost between initial lookup and locked callback.

Non-owned events are treated as **unclassified/global** and continue
their current delivery path. This is intentional: the event bus has
no trustworthy mechanism to derive player ownership from arbitrary
event names, listener closures, attacker aliases or packet values.
No real `CombatOutcome`, `LocalSession` or socket installs the
offline adapter. In particular, these tests do not authorize live
account login or grant R64 credentials.

## Deterministic regression and source inventory

`R77OfflineDomainPublicationQuiescenceTest` drives a real isolated
WorldPulse and the actual DomainEventBus subscriptions and ordered
listeners:

1. Tick 1: two different World actor publications are accepted,
   and a global/unclassified event is delivered as before.
2. A legacy `WorldEventQueue.schedule` callback is queued before
   quiescence; the actor quiesces via a real World command before its
   tick-2 execution. Domain publication rejects the stale owner's
   callback without executing its listener. A separate World actor
   and global listener remain unaffected.
3. A high-priority listener on the other actor itself marks that
   actor quiesced. Per-listener admission must reject the **next**
   normal-priority listener; checking only at publication start
   would incorrectly allow it.
4. A publication for an unregistered generation is rejected, while
   global events remain available.
5. Existing DomainEventBus priority/cancellation, API-boundary,
   World-context, R63–R76 safety regression tests run unchanged.

`R77OfflineEventProducerSourceAuditTest` is a **read-only** scan of
the checked-out `server/src/spk` Java tree. It records candidate
calls to legacy `events.schedule`/ `world.events().schedule`,
explicit `scheduleOwnedForTest`, and World domain publications.
It distinguishes production files from test fixtures but labels
production scheduled sites **unclassified**, not owner-approved.
The source inventory intentionally does not rewrite existing
untagged producers, guess their owner, or execute any game server.

The dedicated R77 GitHub workflow runs all these checks under
Linux and Windows Java 21 using Java 11-compatible output.
The repository-wide server build is an independent required gate.
Only exact-head results establish validation.

## Limits and next work

- `WorldEventQueue.schedule` continues running untagged callbacks.
  If an untagged closure directly modifies a retired WorldPlayer,
  the R77 domain hook cannot guard a mutation that bypasses
  `DomainEventBus.publish`. Source inventory candidates must be
  reviewed individually before converting a genuine player-owned
  callback to an owner-tagged API.
- `CombatOutcome` has semantic attacker references but does not
  itself carry a verified player generation. R77 does not try
  to authorize it from string equality or make unrelated global
  combat outcomes disappear.
- Domain publication is synchronous, so a listener that begins
  while legitimately admitted may itself perform a quiescence
  transition. R77 checks before subsequent listener invocations,
  not between every line within a currently executing listener.
- Runtime callback families that bypass command, realtime,
  World tick and domain event channels need further source-specific
  review (for example nested callbacks in gameplay services and
  direct WorldNpcRegistry writes).
- R64 offline UUIDs are not login credentials. The R63/R64/R72
  account-ledger-owner-marker protocol is not physically
  power-loss atomic. R74's cooperative forced-shutdown witness
  does not cancel arbitrary in-progress disk system calls.
- There are no runtime performance, FPS, server latency or live
  player stability claims from these offline tests.

**R77 DRAFT / OFFLINE ONLY / UNMERGED / NO PRODUCTION ACTIVATION.**
