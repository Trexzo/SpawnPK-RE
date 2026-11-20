# R99 — offline final-capture cross-queue owner quiescence

**DRAFT / STACKED ON R98 #3143 / OFFLINE TEST ONLY / UNMERGED / UNDEPLOYED**

## Why R98 was not enough

R98 uses R74's existing WorldCommandInbox admission hook to
quiesce current World-owned commands at the exact R72 terminal
capture. It checks pending and newly submitted commands, confirms
the final R89/R90 producer IO completion, and composes with the
R96 PREPARED/COMMITTED retirement witness. It does not stop an
already-scheduled realtime callback, an owner-tagged World event,
or periodic WorldPulse player work that executes after final capture.

The source already provides three opt-in hooks with both admission
and dispatch checks where ownership metadata is available:
`WorldRealtimeQueue.OfflineRealtimeAdmission` (R75),
`WorldEventQueue.OfflineOwnedEventAdmission` (R76),
and `WorldPulse.OfflineTickAdmission` (R76).

## R99 test-only composition

`R99OfflineCrossQueueRetirementFence` references the SAME R98
`quiescedForTest(WorldPlayer,generation)` bit, rather than
creating a second retirement flag. It installs existing gates only
on an isolated World before start, with no queued realtime/event
work. Existing hooks reject duplicate policy installation.

- Realtime scheduling checks the actor/generation before enqueue.
  At due dispatch the actual World execution-ownership callback
  holds the real player mutation lock, where the R99 policy is
  rechecked. A task queued before terminal capture does not
  inherit permission to execute afterward.
- `scheduleOwnedForTest` explicitly supplies EntityId and World
  generation. R99 confirms current World membership before
  scheduling and again during event dispatch, where it invokes
  `World.withOpenPlayerMutationOwnershipIfCurrent` to recheck
  quiescence under the actual actor mutation lock.
- The opt-in WorldPulse tick gate rechecks the same state under
  mutation ownership before timed-effect and tick-target callbacks.
- The existing R98 command gate, R72 terminal capture, R93/R94
  leased save admission, R89/R90 durable source, and R96
  retirement-witness requirements remain unchanged.

R99 does not alter the legacy realtime, event or tick callback
implementation if no offline policy is installed.

## Executable regression

`R99OfflineCrossQueueRetirementFenceTest` runs the full R98
offline command/final-capture cases and additionally creates:

1. A genuine isolated World with a R90 producer, R93/R94 leased
   persistence, R98 terminal-capture policy and R99 queue gates.
   Inside one World command, it schedules a future realtime
   item-spawn and owner-tagged World event **before** quiescing
   the actor, then captures the real R72 final immutable snapshot.
2. Both post-capture scheduling attempts must be refused at
   admission with R75/R76 markers. The pre-capture callbacks
   must later reach their due times yet **not** mutate the player:
   the test waits for actual clock progress and queues to drain.
   It also checks rejection counters for the due-dispatch
   rechecks, including periodic tick callback refusal.
3. Final snapshot must complete off the World thread, commit
   R89/R90 capture ordinal 1, and equal the canonical account
   digest after both queued callbacks were refused. The R96
   retirement witness then publishes PREPARED and COMMITTED
   only after actual World unregister, with no login authority.
4. A separate unquiesced World runs valid realtime and owner-
   tagged event callbacks. Both must execute once without any
   R99 denial, proving the policy is not an indiscriminate
   global disable of timers/events.

R99's dedicated Linux/Windows Java 11-compatible workflow is
focused on the cross-queue test; separately triggered inherited
R98/R97/R96 and server/atomic-persistence workflows remain
independent.

## Unresolved and out of scope

An **untagged** `WorldEventQueue.schedule` callback carries no
reliable player identity: it is NOT fenced by this owner-tagged
event policy and must not be silently assumed safe. Similarly,
arbitrary direct player-state mutation, unowned plugin callbacks,
network socket handlers, trade/pair side effects, external filesystem
writers and non-cooperating JVMs remain outside these hooks.

Realtime and tagged-event admission is in-memory and cooperative.
This is not a global player-lifecycle quiescence proof or a safe
production login/owner-takeover policy. R96/R97 disk witnesses
do not grant automatic recovery, host power-loss durability,
multi-file atomic transactions or exactly-once rewards. No
production LocalSession or World.shared path installs R99.

**Keep DRAFT / UNMERGED / NOT DEPLOYED. Exact-head Linux and
Windows CI required before claiming verification.**
