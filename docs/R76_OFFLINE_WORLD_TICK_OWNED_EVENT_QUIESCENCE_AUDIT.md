# R76 — WorldPulse periodic callbacks and owner-tagged event quiescence

**DRAFT / STACKED ON R75 / OFF BY DEFAULT / UNMERGED.**

## Confirmed callback paths outside R74/R75

A source inspection of `WorldPulse.pulseOnce` confirms that a logical
World tick executes in this sequence:

1. Drain `WorldCommandInbox` (R74 already fences explicitly queued
   actor commands after final-save quiescence).
2. Drain `WorldEventQueue.runDue` (callbacks are legacy arbitrary
   `Runnable` objects without an intrinsic World actor).
3. Iterate `WorldPlayer.timedEffects().tick` for each registered actor.
4. Iterate `WorldTickTarget.onWorldTick` for each registered actor,
   including the existing LocalSession per-player gameplay tick adapter.
5. Trigger `WorldPlayerPersistence.checkpointDue` (already suppressed
   after R72's final snapshot capture).

Timed-effect expiration and WorldTickTarget updates use World ownership
and the player's mutation lock, but previously lacked the optional
post-final quiescence gate that R74 added to the command inbox.
Scheduled events have no trusted owner metadata at all, so assigning
every closure a player ID or blocking unrelated global tasks would
be unsafe.

## Test-only changes

`WorldPulse.installOfflineTickAdmissionForTest` adds a hook explicitly
installed before the first tick / pulse start. The actual timed-effect
and tick-target mutation callbacks revalidate admission **inside**
`withOpenPlayerMutationOwnershipIfCurrent`, after its World ownership
check and under the real player mutation lock. If the R74 test actor
has quiesced, both operations are skipped; other actors continue
running. Without this opt-in hook, both default live execution
paths use their original direct callbacks.

`WorldEventQueue.installOfflineOwnedEventAdmissionForTest` and
`scheduleOwnedForTest` add an explicitly owner-tagged test path.
A producer supplies EntityId and World generation, which the gate
revalidates once before scheduling and again on due execution. The
execution gate calls `World.withOpenPlayerMutationOwnershipIfCurrent`,
then checks R74 quiescence under the player's real mutation lock.
A pre-quiescence event that becomes stale while queued cannot execute
a gameplay handler after the terminal boundary. Cancellation,
insertion-order scheduling and World close remain unchanged.

Critically, **legacy untagged `WorldEventQueue.schedule` events are not
covered or modified**. They lack a reliable actor binding; silently
classifying a generic Runnable as player-owned would create false
security claims. All R76 APIs are test-only, and no LocalSession
installs any of these policies.

`R76OfflineWorldTickAndEventFence` composes the test-only WorldPulse
and event admissions with a single R74 owner/generation quiescence
policy. No new live authentication or account authority is created.

## Acceptance

`R76OfflineWorldTickOwnedEventTest` uses one real isolated World with
two registered actors, a real WorldPulse, timed-effect services, two
WorldTickTarget adapters, and real WorldEventQueue objects. It drives
four deterministic ticks with `World.observePulse` and **does not
start a real game server, socket or live account**.

- Tick 1: both targets run; a tagged retiring-actor event executes;
  both fixed-duration effects remain active.
- Before tick 2 event/timed-effect/target phases, a genuine queued
  World command marks only the retiring actor quiesced.
- Tick 2: its previously scheduled tagged event is skipped, its
  timed-effect expiration does not mutate state, and its target is not
  executed. The unrelated actor's effect expires normally, its tick
  target and tagged event execute, and an untagged unrelated global
  event continues to run.
- Tick 3: attempts to schedule new retiring-actor tagged events are
  denied; unrelated tagged and global events still run.
- Tick 4: an existing retiring-actor tagged event is rejected after
  that exact World generation has been unregistered. The unrelated
  actor's periodic callbacks continue normally.
- Event cancellation semantics, duplicate installer refusal, and
  inherited R63–R75 persistence and quiescence tests are retained.

The dedicated GitHub workflow runs Linux/Windows Java 11 bytecode
validation and the above real fixture; the broader server build also
runs on the draft head. Verification requires checking *exact-head*
workflow conclusions.

## Remaining gaps / proposed R77

1. Inventory the **legacy untagged** WorldEventQueue producers, classify
   each by actual owner semantics, and migrate only those demonstrably
   player-owned to a safe tagged dispatch path. Global actions should
   remain global.
2. Trace `WorldTickTarget` internals and `LocalSession.onWorldTick`
   for callbacks that spawn further work or mutate other entities,
   including domain-event and trade handlers.
3. Evaluate owner-generation capture and post-final mutations inside
   direct `WorldNpcRegistry`, `DomainEventBus`, and other surfaces
   that do not use the above owned callback APIs.
4. No offline UUID/epoch is live authentication. R63/R64/R72 multi-file
   commits are not physically crash-atomic; forced shutdown cannot
   cancel uncooperative disk writes that already entered publication.
5. No runtime performance, FPS, server latency, or production stability
   gains are inferred from these offline safety regressions.

**R76 OFFLINE ONLY / DRAFT / UNMERGED / NO LIVE ACTIVATION.**
