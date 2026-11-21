# R103 — offline owner-tagged plugin task retirement

**DRAFT / STACKED ON R102 #3147 / OFFLINE TEST ONLY / UNMERGED / UNDEPLOYED**

## Verified source gap

R102's alias-aware audit identified a real event scheduling sink in
`PluginTaskTracker.scheduleAt`: `queue.schedule(tick,task)` forwards
plugin-defined opaque Runnable actions into the untagged World queue.
PluginTaskTracker already provides enable/disable, runtime admission,
callback-scope lease, task cancellation and failure isolation, but
none of these guarantees that an arbitrary plugin callback owns a
particular WorldPlayer or generation. R101's strict test-only event
provenance rule correctly refuses this unclassified event.

## R103 opt-in owner-tagged path

Without changing the public `spk.plugin.api.PluginScheduler` interface
or the existing `schedule` and `scheduleRepeating` behavior, R103
adds a package-private `PluginTaskTracker.scheduleOwnedForTest(
delayTicks,EntityId,WorldGeneration,Runnable)` method.

- Admission explicitly requires a non-null actor ID and positive
  generation. A currently registered, not-quiesced owner must be
  admitted by the preinstalled R76/R99 tagged event gate.
- The new task retains exact ID and generation through
  `PluginTaskTracker.scheduleAt`, which calls R103's
  `WorldEventQueue.scheduleOwnedWithRejectionForTest`.
  The ordinary/public plugin path **still calls its legacy
  untagged** `queue.schedule`.
- The R103 queue helper checks the R76 admission gate before
  enqueuing and calls its actual dispatch policy when due.
  R99 verifies current World membership and R98 final-capture
  quiescence **inside the same player mutation critical section**.
  If that synchronous dispatch declines to invoke the callback,
  the helper notifies the task tracker to retire the waiting task.
  This prevents a rejected callback from leaving a false active
  plugin task handle with no queued event.
- A successfully dispatched plugin action still enters the
  **existing** PluginTaskTracker execution path, including
  PluginCallbackScope's world-execution lease and the task's
  runtime/closing checks, and is retired by the normal one-shot
  completion path.

The rejection-aware dispatch hook assumes a **synchronous** R76
policy, as supplied by R99. It is exclusively test-only; it must
not be promoted to arbitrary asynchronous dispatch policies
without a separately proven acknowledgment model.

## Executable regression

The R103 Linux/Windows Java 11 regression copies the existing
R99/R101 isolated-World terminal-save fixture without changing
the inherited tests. The positive and negative controls use
a real `PluginTaskTracker` and an activated
`PluginCallbackScope` alongside R93, R98, R99 and the R101
strict scheduled-event mode.

- A plugin task with exact owner ID/generation is enqueued
  inside the real World actor **before** the same actor performs
  the R72 immutable terminal save. When its tick comes due
  afterward, R99 denies the callback and R103 retires the
  plugin task handle; the callback's player mutation counter
  stays zero.
- A newly requested tagged plugin task is refused at admission
  after quiescence; the legacy/public untagged schedule remains
  refused by the opt-in R101 strict policy, rather than silently
  acquiring actor ownership.
- The final account snapshot is committed via off-World-thread
  R89/R90 IO and remains bytewise/canonically consistent with
  the original capture after queued callbacks are drained.
  The R96 witness transitions PREPARED -> real World unregister
  -> COMMITTED, without login authorization.
- A second unquiesced isolated World verifies that a tagged
  plugin callback executes **exactly once** on WorldPulse while
  holding the real player mutation lock and under the active
  PluginCallbackScope lease, and that the task handle becomes
  inactive. Wrong-generation admission and legacy untagged
  scheduling are separately rejected.

Dedicated Linux/Windows jobs run the R103 test. Inherited
R101/R99 and independent full-server/Windows atomic persistence
CI provide broader parity safeguards. Exact-head CI is required
before claiming verified status.

## Boundaries

This code is **not** wired to production `WorldPluginManager`,
`LocalSession`, `World.shared`, sockets or the public plugin API.
No actual third-party plugin has been migrated; every existing
public plugin task still uses the untagged route in ordinary
production. Plugin tasks without explicit owner/generation,
callbacks that mutate multiple players, plugins that bypass
PluginScheduler, and direct/scheduled external mutation remain
unclassified. The new R103 path is a narrow cooperative
offline proof, not a blanket guarantee of global player
quiescence, owner-safe logout, re-login authority or recovery.

No production deployment, merge, schema modification or
automatic replay is authorized.

**DRAFT / UNMERGED / UNDEPLOYED.**
