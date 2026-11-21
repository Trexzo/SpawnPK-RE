# R102 — WorldEventQueue alias and opaque plugin-callback audit

**OFFLINE / SOURCE-READ-ONLY AUDIT / STACKED ON R101 #3146 / DRAFT / UNMERGED / NOT DEPLOYED**

## Finding: R101 missed a real untagged production queue sink

The R101 lexical scan saw 1,218 Java source files and 52 syntactically
recognized calls: 41 untagged calls (all in tests), 10 tagged calls, and
one explicit non-player call. Its reported zero non-test untagged calls
was NOT evidence that no production path could schedule untagged work.

In the same source tree:

1. `World` owns `private final WorldEventQueue events` and passes it to
   `new WorldPluginManager(..., events, ...)`.
2. `WorldPluginManager` retains the `WorldEventQueue worldEvents`
   field, then constructs `new PluginTaskTracker(clock, worldEvents,
   worldOpen, callbackLoader, callbackScope, ...)`.
3. `PluginTaskTracker` accepts a **typed** `WorldEventQueue queue`
   constructor argument and calls `queue.schedule(tick, task)` from its
   `scheduleAt` helper. R101's restricted receiver-name patterns
   missed this genuine production call.
4. `PluginTaskTracker.Task.run` calls `owner.execute(this)`.
   `execute` checks the task's plugin runtime/closing state and
   invokes the opaque plugin-defined `Runnable action` through
   `callbackScope.callUnchecked(...)`.

There are meaningful plugin lifecycle safeguards, including task
cancellation, callback-scope admission and runtime shutdown checks.
**They do not by themselves establish a WorldPlayer owner/generation
for every arbitrary plugin Runnable**. Neither R99's tagged event
admission nor R101's strict *test-only* provenance rule can silently
treat this task as player-free. We have not established that any
particular plugin mutates a stale player.

## New tool: conservative alias-aware inventory

`server/tools/audit_world_event_aliases.py` imports the proven R101
comment/string masking and direct-accessor scanner. It scans the
same Java tree, adds recognition of typed `WorldEventQueue`
receivers, limited `var` initializers/assignments and explicit
receiver calls, records method references, and reports constructor
forwarding of queue variables. It labels candidate sink calls as
tagged, untagged or explicit non-player; callbacks get a heuristic
review flag (`POSSIBLE_PLAYER_CAPTURE`, `OPAQUE_CALLBACK_REVIEW`,
or `UNKNOWN_CALLBACK_REVIEW`).

The scanner reports file and source line, typed versus inferred
receiver evidence, and whether a match is inside a test file. CI
requires the `PluginTaskTracker.java` `queue.schedule` sink to
be present. If it disappears due to refactoring, CI fails until
the source/data-flow risk is explicitly re-reviewed rather than
silently reverting to a false zero.

The scanner deliberately does **not** resolve lexical scopes,
overloaded methods, variable shadowing, inherited generic types,
reflection, arbitrary aliases across fields or heap objects,
method summaries, closure captures or the actual side effects
of plugin Runnable objects. It may over-report candidates due
to symbol reuse or under-report indirect scheduling through
wrappers. Its results are review prompts, not Java compiler proofs.

## Regression and security boundaries

The R102 synthetic self-test checks typed receivers, `var` aliases,
player-reference heuristics, opaque callbacks, method references,
constructor forwarding, comment/string exclusion, and
`other.schedule` exclusion. Linux and Windows GitHub CI execute
both the R101 scanner self-test and the R102 scanner self-test,
then run the full R102 inventory against the checked-out source.
The mandatory production plugin sink is checked on both systems.

**No live `PluginTaskTracker`, `WorldEventQueue`, player login,
World.shared, network, plugin runtime, or persistence code is changed
by this PR.** There is no automatic migration to R101's strict
mode: doing so before plugin task ownership is classified would
break the existing plugin scheduler.

## Next safe design question

Inspect the plugin-facing `PluginScheduler` APIs and individual
registered `Runnable` callbacks. For tasks that *actually*
depend on a WorldPlayer, a future opt-in API can require the
actor ID/generation at admission and revalidate it inside the
real World mutation lock at dispatch. For proven player-free
plugin tasks, a narrowly reviewed non-player scheduling path
may be appropriate. Do not infer player identity from a
Runnable's enclosing Java object.

The user-visible R101 source count should continue to be cited
as only the narrow direct-receiver inventory, **not** the full
source-wide ownership classification.

**Keep DRAFT / UNMERGED / UNDEPLOYED until exact-head CI
results are reviewed.**
