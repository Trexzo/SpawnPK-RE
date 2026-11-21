# R105 — same-player generation rollover and stale plugin callback fence

**DRAFT / STACKED ON R104 #3149 / OFFLINE TEST ONLY / UNMERGED / UNDEPLOYED**

## Why this regression matters

The actor's `WorldPlayer` reference and `EntityId` can be reused in
a test World across unregister/re-register, while `PlayerRegistry`
advances its generation on both transitions. A callback checking only
the actor object or EntityId would incorrectly authorize old work after
re-registration. Both R103 one-shot and R104 periodic plugin callbacks
must be bound to **the exact original generation**, not simply to a
currently registered player with the same name or ID.

R99's existing World event gate already rechecks the player registry
and R98 final-capture state under the same actor mutation lock as the
World command dispatcher. R105 validates those invariants using the
real `World`, `PlayerRegistry`, `WorldPlayer`,
`PluginTaskTracker`, `PluginCallbackScope` and R76/R99 dispatch.

## Executable controls

The new `R105OfflinePluginGenerationRolloverTest` inherits the full
R103/R104 real-World final capture and durable retirement tests before
exercising two independent isolated Worlds:

**Rollover before the scheduled event is dispatched:**
- Register a `WorldPlayer` and enqueue tagged old-generation
  one-shot and repeating plugin tasks before `World.start()`.
- Unregister the actor, then re-register the **same Java object** under
  the same username and EntityId. Require that the generation advances,
  the registry resolves the exact same object, and old-generation
  ownership fails while new-generation ownership succeeds.
- Reject new scheduling requests explicitly tagged with the stale
  generation, both one-shot and repeating. Reject unclassified
  public/legacy scheduling through the opt-in R101 strict World test.
- Admit current-generation one-shot/repeating callbacks and one
  explicitly reviewed **non-player** diagnostic event.
- Start the real World pulse. Validate new-owner callbacks on the
  World actor mutation lock and allow several successful repeats.
  Cancel the valid repeater. After the old tasks' due ticks, assert
  zero old-generation callbacks, exactly one new-generation one-shot,
  the global diagnostic executes, cancellation remains effective,
  and every stale plugin task handle is retired, with zero queue
  entries remaining.

**Rollover while an old generation has been repeating successfully:**
- Start the World and run a tagged old-generation plugin repeater for
  at least four real ticks under the current World actor lock.
- Unregister the player using the real `World.unregisterPlayer`
  lifecycle and mutation locks; capture the callback count at this
  ownership boundary; then re-register the **same WorldPlayer object**
  with a new generation.
- Reject further explicit stale-generation submissions, admit a
  new-generation one-shot callback, and advance enough World ticks
  that the previously queued old-generation repeater must reach
  dispatch.
- Prove the old repeater does not run again or self-reschedule, its
  active handle retires, the new generation remains owned and
  executes its valid task on the same actor lock, and the queue is
  empty.

Both scenarios use the R103/R104 **test-only** owned plugin API
and R99 cooperative admission. No production scheduler or player
registration code is changed.

## Correct interpretation and limits

R105 is an **offline in-memory actor-generation ownership test**.
An in-process unregister/register is not a successful socket-based
client relogin, authenticated session transfer, server restart, or
durable recovery. In particular, the rollover scenarios intentionally
do not attach the R93/R96 retirement witness, because those test
gates would correctly block premature unregister before a successful
final durable save. Thus these tests show stale callback
non-execution, **not** that this synthetic re-registration sequence is
safe or authorized in live game sessions.

Earlier R103/R104 inherited tests separately validate final R72
capture, R89/R90 persistence, and R96's committed witness. These
must not be conflated into a proof of authorized login takeover.

R105's dedicated workflow builds Java 11-compatible classes and
executes the tests on both Linux and Windows. The independent
server build/Windows atomic persistence workflow must also be
checked on the exact R105 commit before claiming full verification.
No public `PluginScheduler` API, external plugin JAR, production
`World.shared`, `WorldPluginManager`, `LocalSession` or socket
flow is modified by R105.

**Keep DRAFT / UNMERGED / NOT DEPLOYED.**
