# R107 — owner-tagged plugin callbacks and cross-actor mutation limits

**DRAFT / STACKED ON VERIFIED R106 #3151 / OFFLINE TEST ONLY / UNMERGED / UNDEPLOYED**

## Finding: tagged event ownership is not mutation isolation

R103–R106 established a real R76/R99 owner-tagged `WorldEventQueue`
path for isolated test-only plugin callbacks. The R99 dispatch policy
validates the tagged `EntityId` and player generation under its
player mutation lock, then invokes the plugin's `Runnable`.

**That lock is not an arbitrary plugin sandbox.** The callback can
capture some *other* `WorldPlayer` before it is scheduled. It can
call `other.bank().spawnItem(...)` directly without entering
`World.withOpenPlayerMutationOwnershipIfCurrent(other,...)` or
holding the other player's mutation lock. Neither the originating
actor tag nor `PluginCallbackScope` can derive the referenced
actor(s) from an opaque Java closure.

R107's isolated-World test intentionally reproduces this bypass as
a **negative control**. It counts one direct mutation of actor B
performed by an actor-A-tagged callback under *only A's* World lock.
The test uses synthetic local bank state and never invokes
`World.shared`, LocalSession, sockets or a real external plugin.
The negative-control output is evidence of a **remaining gap**, not
evidence of isolation.

## Narrow fail-closed improvement for cooperative mutation callers

R107 adds a package-private `R107OfflinePluginOwnerCall` context,
set only while R103/R104 **test-only owner-tagged plugin callback**
actions execute inside the real `PluginTaskTracker` and
`PluginCallbackScope`. It carries the WorldEventQueue instance,
exact `EntityId` and generation as thread-local provenance, and
clears/restores the marker with `finally`. A nested attempt to
switch to another tagged actor is rejected.

When this marker is present,
`World.withOpenPlayerMutationOwnershipIfCurrent(actor,generation,...)`
rejects a different queue, `EntityId` or generation before
entering the World lifecycle/player mutation critical section.
The method's pre-existing locking, current-owner check and
behavior are unchanged when no tagged test callback is active.
The checked same-actor path continues to work normally. A separate
valid B-tagged plugin callback can mutate B under its own
mutation lock. An unrelated explicit non-player event sees no
retained plugin owner context.

**This does not protect direct calls to `other.bank()` or other
unmediated mutable state references.** Neither a `ThreadLocal`
nor a tag on a `Runnable` can enforce all read/write effects
of arbitrary Java code. This R107 improvement is a cooperative
check on the existing World-owned mutation helper only.

## Executable regression

`R107OfflinePluginCrossActorMutationTest` first executes R106
(which inherits R103–R105), then creates one real isolated World
with two simultaneously registered actors A and B.

- A-tagged callback enters while the World holds A's mutation
  lock. Its request through the checked World helper to mutate
  A succeeds.
- A's attempts to mutate B through that World helper, to use A
  with the wrong generation, or to nest a different actor tag
  are rejected; no checked cross-actor mutation is performed.
- A **deliberately bypasses** the helper by mutating a previously
  captured B.bank() reference. Its counter must record that
  direct cross-actor mutation really happened without B's lock.
  The test thus prevents a misleading "all plugin mutations safe"
  success claim.
- B's own tagged callback mutates B through the checked World
  helper under B's mutation lock. The explicit non-player
  diagnostic runs with no plugin owner context.
- After both plugin handles retire, an outside-tagged-callback
  invocation of the original World helper still succeeds under
  its usual World ownership lock, showing no global behavior
  change.

The dedicated workflow runs Java 11-compatible Linux and Windows
regressions. The independent Gradle full server and Windows
atomic-persistence suites must also pass against the exact R107
commit before this audit is labeled fully verified.

## Remaining work for actual isolation

Before claiming a live server feature, production plugin-facing
mutable-state APIs would have to be mapped and refactored to use
owner-bound capabilities with explicit authorization for
multi-actor operations. Direct raw `WorldPlayer` / state
references, static state, custom plugin classes, and native/JVM
reflection routes cannot be safely covered by this narrow hook.

The in-memory R107 test does **not** assert that existing
`PluginScheduler` clients or real external plugins are sandboxed.
No public plugin API, production session World wiring or auth
lifecycle is changed; no merge/deploy is authorized.

**DRAFT / UNMERGED / UNDEPLOYED.**
