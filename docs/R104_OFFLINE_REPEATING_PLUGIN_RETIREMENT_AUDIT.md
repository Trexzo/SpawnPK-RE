# R104 — offline recurring player-owned plugin tasks across final capture

**DRAFT / STACKED ON R103 #3148 / OFFLINE TEST ONLY / UNMERGED / UNDEPLOYED**

## Why R103 is incomplete for periodic work

R103 established a package-private, opt-in `PluginTaskTracker.scheduleOwnedForTest` path for a one-shot callback with explicit `EntityId` and World generation. The task is scheduled through the R76 owner-tagged WorldEventQueue and its R99 admission/dispatch policy, rechecked under actual World player-mutation ownership. After R98 final capture, callbacks already enqueued do not execute, and a rejection-aware dispatcher retires their plugin handles.

The public `PluginScheduler.scheduleRepeating` still uses the legacy untagged route. Plugin callbacks may schedule fresh tasks on every tick; protecting only their first enqueue is insufficient. Any recurring callback can be valid before R72 capture and stale afterward, even when it has already executed safely multiple times.

## Opt-in recurring owner-tagged task

`PluginTaskTracker.scheduleOwnedRepeatingForTest(
initialDelayTicks,periodTicks,ownerId,generation,Runnable)` accepts
positive initial delay and period, an explicit current actor ID, and
a positive generation. It uses R103's exact `Task` owner metadata
and existing plugin execution/lease/requeue logic without modifying
its public `PluginScheduler.scheduleRepeating` API.

Every requeue goes through R103's `scheduleOwnedWithRejectionForTest`,
which invokes the existing R99 owner/generation admission. On due
dispatch, R99 rechecks owner and R98 quiescence inside the actual
World mutation lock. A callback that is valid on earlier ticks
cannot inherit permission after final capture; a pending callback
denied at due dispatch is reported to the plugin tracker and
its handle is retired. A rejected submission does not retain
a live task in the tracker's collection.

No production plugin or World session is switched to this new API.

## Executable real-World regression

`R104OfflineRepeatingPluginRetirementTest` first runs R103's
one-shot regression in full and then an additional real isolated
World with R93, R98/R99, R101 strict event provenance, R72
terminal-capture IO, R89/R90 producer continuity, and R96
PREPARED/COMMITTED retirement.

The new case:

1. Starts two owner-tagged repeaters on the same current actor.
   Their callback admission is validated against the World actor
   lock and actual pulse context.
2. Verifies multiple healthy repeats happen; cancels one plugin
   handle, advances the real World clock, and verifies it cannot
   reschedule while the other task keeps running.
3. Uses a real World command to mark quiescence and capture an
   immutable final snapshot. Records the callback count at the
   capture boundary, rather than assuming a particular global
   timer ordering.
4. Refuses newly scheduled repeaters for the quiesced generation
   and a wrong generation, and refuses legacy untagged repeaters
   in the isolated R101 strict mode.
5. Submits the R72 final snapshot *off* the World thread and waits
   for the real R89/R90 persistent commit. Advances multiple
   ticks past final capture, awaits zero queued plugin events
   and zero active handles, and asserts **no repeat callback ran
   after the capture boundary**. The final account snapshot digest
   matches the saved capture.
6. Publishes the R96 durable retirement witness PREPARED,
   confirms early direct unregister is fenced, performs actual
   unregister and COMMITTED publication, and checks no login
   authorization or post-retirement callback is introduced.

Dedicated Linux and Windows Java11-compatible R104 jobs, the
inherited R103 and preceding offline regressions, and the
independent full server/Windows atomic persistence suites provide
source and build parity. Exact-head CI is needed before marking
this milestone verified.

## Remaining limitations

The legacy public plugin scheduler and `World.shared` remain
unchanged; this is an isolated test-only path. R103/R104 assumes
synchronous owner admission/dispatch and cannot secure plugins
that bypass the plugin tracker, mutate multiple actors without
an explicit multi-owner gate, or capture mutable player state
through unrelated untagged services. Real plugin jars have not
been migrated or observed. In-memory quiescence does not
provide automatic recovery or re-login authority; durable
witnesses do not imply power-loss transaction atomicity.

**Keep draft, unmerged, undeployed; do not assert complete
player lifecycle shutdown.**
