# R101 — explicit provenance for scheduled World events

**DRAFT / OFFLINE TEST ONLY / STACKED ON R100 #3145 / UNMERGED / UNDEPLOYED**

## Motivation

R99 proves that a known current actor/generation cannot receive
post-terminal-capture work through R74 commands, R75 realtime,
R76 tagged scheduled events or R76 periodic ticks. However,
`WorldEventQueue.schedule(tick, Runnable)` accepts an arbitrary
closure without exposing any actor identity. A pre-existing event
can capture a player and mutate it after R72 final capture, even
when R99's owner-tagged event gate is installed. Treating an
untagged closure as actor-free is unsafe.

## Conservative test-only enforcement

`WorldEventQueue.installOfflineExplicitEventProvenanceForTest`
is an opt-in strict mode for a disposable World. It requires that
the existing R76 owned-event policy is already installed and
that the queue has never been used. Default/live World code
retains the existing event path when this hook is absent.

In strict mode:

- Ordinary legacy `schedule(tick, Runnable)` throws
  `R101_UNTAGGED_EVENT_DENIED` and enqueues nothing.
- `scheduleOwnedForTest(tick, EntityId, generation, task)`
  keeps R76's exact enqueue and dispatch owner checks.
  Its internal queueing bypasses only the *syntax* ban on
  untagged events, never the owner policy.
- `scheduleExplicitNonPlayerForTest(tick, reviewedPurpose, task)`
  is a **test-only** explicit declaration for a task that
  reviewers have determined does not use mutable player state.
  Missing/blank purpose is refused. It retains standard queue
  ordering and handle semantics.

The non-player purpose is **not** a security credential or
automatic proof that the closure does not capture a player.
A malicious or careless caller can misclassify a player task;
manual source review remains necessary. The strict mode does
not silently modify or migrate legacy production callbacks.

## Executable checks

`R101OfflineExplicitEventProvenanceTest` shows the original
gap using a disposable WorldEventQueue: an installed
owner-tagged policy does not intercept a legacy untagged event.
It then installs strict mode on a fresh queue and verifies:

1. Untagged scheduling is rejected before enqueue.
2. Blank-purpose non-player scheduling is rejected.
3. Valid current owner-tagged events execute in order.
4. A tag accepted at enqueue is checked again at dispatch;
   after a simulated owner quiescence it cannot execute.
5. An explicitly declared fixture-global event still executes.
6. Refusals leave queue length unchanged.

The integrated regression calls
`R99OfflineCrossQueueRetirementFenceTest r101-strict`, adding
strict event mode before the isolated World starts, alongside
actual R98/R99 command/realtime/event/tick gates and R72/R89/R90
final persistence. It confirms that an untagged event is
refused both before and after final capture, R99 owner-tagged
events are still dispatched through the real World mutation
lock and refused after quiescence, a reviewed non-player
diagnostic event runs, and the R96 retirement witness still
commits after actual unregister.

## Source call-site inventory

`server/tools/audit_world_event_provenance.py` is a read-only
Python3 audit executed by the dedicated Linux workflow. It scans
Java files under `server/src`, masks source strings, character
literals, block/line comments and text blocks while retaining
line positions, and emits JSON-line source locations for
syntactically identifiable calls to
`events().schedule`, `events.schedule` and related
owner-tagged/explicit-nonplayer methods.

Each result is labeled `LEGACY_UNTAGGED`,
`OWNER_GENERATION_EXPLICIT`, or
`NONPLAYER_CLAIM_EXPLICIT` with a confidence/receiver
category and whether the file resembles a test fixture.
The scanner includes a self-test that refuses to count
literal/comment-only calls or unrelated realtime scheduling.

**This is a candidate inventory, NOT an exhaustive Java type
or closure-capture analysis.** Alias names, interface calls,
generated sources, reflection and lambdas' side effects may
be missed. Source findings must be reviewed before migrating
actual production call sites. CI prints its counts and
locations; no existing event call site is automatically changed.

## CI and remaining limitations

Focused R101 Linux/Windows jobs compile Java11-compatible
bytecode and run the strict event tests. Linux also runs the
read-only source inventory. Independently inherited Gradle
server/Windows atomic-persistence workflows remain in place.

No real login, LocalSession, World.shared default behavior,
network packet path, plugin scheduling policy, non-cooperating
filesystem writer, durable owner transfer or automatic
recovery is changed. There is no proof of physical power-loss
durability, cross-file atomicity or exactly-once gameplay
rewards. This provides a conservative test-only migration
gate, not a finished inventory of all player callbacks.

**Keep draft, unmerged and undeployed until exact-head CI
passes.**
