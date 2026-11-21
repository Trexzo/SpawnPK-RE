# R106 — different WorldPlayer, same username and numeric generation

**DRAFT / STACKED ON R105 #3150 / OFFLINE TEST ONLY / UNMERGED / UNDEPLOYED**

## Source risk

R105 established that a `WorldPlayer` object registered again under
a new generation must not inherit callbacks tagged with its prior
generation. R106 checks a different case: two **different**
`WorldPlayer` objects registered sequentially for the **same
username** can have the **same numeric generation** (typically 1),
although each object has a different immutable `EntityId`.

A callback identified only by username or generation would
authorize work for the wrong actor. The R76/R99 opt-in queue
policy uses **both** the expected `EntityId` and
`PlayerRegistry.owns(actor,generation)` under the actual
World player mutation lock; the R103/R104 plugin helper
must preserve those exact values.

## Real isolated-World fixtures

The `R106OfflinePluginActorReplacementTest` Java 11 entrypoint
executes all R103–R105 regression suites first. It then
exercises two additional independent, test-only Worlds:

### Replacement before queued event dispatch

1. Register original actor A, schedule owner-tagged one-shot
   and repeating plugin work for A, before starting WorldPulse.
2. Try to simultaneously register a distinct actor B with the
   same username; require `DUPLICATE_LOGIN` and that A remains
   registered. Unregister A, register B.
3. Require `A != B`, `A.id != B.id`, **A's expected
   generation equals B's expected generation**, and the
   current username registry resolves only B.
4. Refuse newly submitted plugin tasks for A's ID even if
   the supplied generation equals B's numeric generation.
   Refuse legacy untagged scheduling only in R101's strict
   isolated-World mode.
5. Admit B's exact tagged one-shot and recurring tasks plus
   an explicitly reviewed player-free diagnostic event. Run
   real World ticks, cancel B's repeating task, then drain
   old queued events and all plugin handles.
6. Verify A's callback count is zero, B's valid callbacks
   ran only under B's mutation lock, the diagnostic ran once,
   all previously scheduled A tasks were retired and did
   not self-reschedule, and B remains registered.

### Replacement after recurring work has started

1. Allow a tagged actor-A repeating plugin to run at least
   four times on the real World pulse under A's mutation lock.
2. Unregister A under the real World lifecycle+player locks
   and capture the callback count. Register distinct actor
   B with the same username and the **same numeric generation**.
3. Refuse any new tagged work against A's old EntityId.
   Admit one task with B's exact EntityId/generation and
   verify it runs under B's player lock.
4. Wait for A's already queued repeating event to reach
   actual due dispatch. Require A's callback counter does
   not change after unregister, its handle retires,
   no queued event remains, and B stays current.

## Interpretation and boundaries

Both scenarios test the real `PlayerRegistry`, `World`,
`WorldEventQueue`, `PluginTaskTracker`,
`PluginCallbackScope` and R99 owner admission/dispatch.
They do **not** modify the public plugin API, production
`WorldPluginManager` or live socket/client path.

These synthetic in-memory object replacements intentionally
omit the R93/R96 durable retirement preconditions: a direct
unregister/register is **not** proof that a login is safe,
persistently fenced or authenticated. R105's inherited
regressions separately cover durable R72/R89/R90 final
capture and R96 retirement witness. R106 establishes only
that **stale plugin callbacks do not regain ownership
because a different actor reused the same username and
numeric generation**.

Dedicated Linux/Windows Java 11 regression jobs and
independent Gradle server/Windows atomic persistence
checks must pass at the exact R106 commit before the
milestone is fully verified.

**DRAFT / UNMERGED / UNDEPLOYED.**
