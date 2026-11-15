# R75 — offline quiescence beyond WorldCommandInbox

**DRAFT / STACKED ON R74 / OFF BY DEFAULT / UNMERGED**

## Verified source routing

R74 adds an optional command-admission hook in `WorldCommandInbox` and
rechecks each queued command under the real player's mutation lock. That
covers decoded client requests submitted by the existing
`LocalSession.processPendingOnWorld`, which calls
`world.submitAndWait` and routes typed requests only inside that
World command.

However, there are independent entry points:

- `WorldPulse.run` executes `WorldRealtimeQueue.runDue(now)` before
  its fast command drain. `LocalPetRealtimeScheduler` uses
  `world.scheduleRealtime` for follow and test-sequence callbacks.
  Those callback handlers run under World ownership but do not pass
  through `WorldCommandInbox`.
- `WorldPetNpcService` mutates main/mini pet actors directly via
  `ensureMain`, `ensureMini`, `moveMain`, `moveMini` and
  `removeMini`. Its internal synchronized maps do not establish a
  post-final-save authorization boundary.
- `LocalPendingRequestDispatcher.drain` is a lower-level direct
  typed-request API. `LocalSession` normally calls it via
  `world.submitAndWait`; offline fixtures can call it directly.
  R68 already provides an optional per-request admission seam.

R75 addresses these specific routes in **isolated test fixtures**.
It does not claim every pet, world-tick, domain-event or game network
callback has been brought under one authenticated policy.

## R75 implementation

`WorldRealtimeQueue.installOfflineRealtimeAdmissionForTest` supplies
an optional gate called at realtime task scheduling and again
**inside the actual World execution-ownership callback**, where the
player mutation lock is held. A callback queued before R74 quiescence
cannot run merely because it was accepted earlier. Uninstalled/live
queues still directly invoke their original callback without a wrapper.

`WorldPetNpcService.installOfflinePetMutationAdmissionForTest` checks
the exact pet owner's EntityId before direct main/mini spawn, move and
manual mini removal. A deny throws **before** mutating any canonical
world-owned pet actor. `removeMainAndMini` remains permitted so the
normal World unregister cleanup is never blocked by this gate.

`R75OfflineCrossIngressFence` composes these test-only guards with
R74's owner/generation quiescence, explicitly scopes them to a single
World actor, and also exposes an offline adapter for R68's existing
`LocalPendingRequestDispatcher.OfflineRequestAdmission`.
The adapter refuses any decoded typed request after quiescence even if
a test directly invokes the dispatcher outside `WorldCommandInbox`.
It is **not** an R64 credential check and never installs into a real
LocalSession.

## Deterministic regression

`R75OfflineRealtimePetIngressTest`:

1. Creates a real isolated World with two independent WorldPlayers,
   installs R74 and R75 on only one actor/generation, and starts the
   actual WorldPulse.
2. Creates a real world-owned main pet and schedules/executes a
   realtime callback before quiescence to establish the allowed path.
3. Schedules another real realtime callback *before* quiescence, but
   delays its execution. After the World actor quiesces, the actual
   realtime queue drains the callback under World ownership; its
   handler must **not** run.
4. Refuses new realtime scheduling and direct pet actor movement,
   replacement/spawn and manual mini removal without changing the
   original pet state.
5. Confirms unrelated player/generation realtime and pet actors
   continue normally; R74 command ingress still refuses late gameplay.
6. Unregisters the quiesced owner and confirms pet unregister cleanup
   can still remove its actors without touching the unrelated owner.

`R75OfflineDecodedPacketQuiescenceTest` reuses the full
`LocalPendingRequestDispatcherTest` constructor fixture and tests
the actual typed-request dispatcher with ISAAC-decoded v308 requests:

1. Pre-quiescence movement opcode 164 reaches the real movement
   handler and advances its accepted path count.
2. Movement opcode 164, NPC attack opcode 72, and inventory opcode
   145 are decoded *before* quiescence and held.
3. After quiescing the World actor, all three requests refuse at
   the explicitly installed dispatcher admission seam, before
   gameplay handlers and without changing movement/bank state.
4. An additional World command submission is refused by R74.
   No game socket is opened, and the default LocalSession code path
   remains unchanged.

CI runs both suites and inherited R63-R74 tests on separate Linux
and Windows runners with Java 11 bytecode verification, plus the
repository-wide build workflow. Claims of CI success require
**exact-head** result inspection.

## Remaining limitations / suggested R76

- The **existing** `LocalSession.processPendingOnWorld` runs typed
  requests through WorldCommandInbox, but its socket transport,
  server packet writer, and post-login callbacks are *not* connected
  to an authenticated R64 quiescence protocol. R75's direct-dispatch
  adapter is offline only.
- World tick targets, timed effects, WorldEventQueue, domain events,
  pet-follow presentation state and the lower-level WorldNpcRegistry
  can mutate state without going through these three hooks. They
  still need concrete source-path and owner-generation audits.
- `WorldPetNpcService` methods identify a pet by EntityId and do
  not intrinsically validate its World generation; R75 composes the
  test-only World actor/generation externally, not by changing every
  internal pet method signature.
- A forced shutdown cannot truly cancel arbitrary synchronous
  filesystem writes already underway. R74 only proved cooperative
  pre-commit abort via an explicitly installed test witness.
- R64 offline owner UUID possession is **not authentication**.
  The R63/R64/R72 multi-file commit is not physically atomic across
  power loss; ambiguous states remain quarantined, not repaired.
- No measured performance or live stability/FPS improvements are
  claimed from these offline tests.

**R75 DRAFT / UNMERGED / OFF BY DEFAULT / NO LIVE INTEGRATION.**
