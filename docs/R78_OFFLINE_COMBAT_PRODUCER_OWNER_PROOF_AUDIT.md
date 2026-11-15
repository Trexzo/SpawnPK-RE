# R78 — Offline real PvM outcome provenance and owner-generation admission

**DRAFT / STACKED ON R77 / OFF BY DEFAULT / UNMERGED.**

## Verified real source route

Review of `LocalWorldTickCoordinator` established that its opt-in
`LocalLabPvmKillOutcomeProducer` emits a confirmed, removed-NPC
`CombatOutcome` through its `CombatOutcomeObserver` bridge, which
synchronously calls `World.domainEvents().publish(outcome)`. The real
`DomainEventBus` then invokes high-, normal- and low-priority listeners.

The real `CombatOutcome` has immutable attacker/victim *strings*,
semantic type/context, tick and authority; it does **not** carry an
authenticated WorldPlayer generation or a proof of the source that created
the fact. Inferring authorization from a string that happens to equal a
player ID would authorize fabricated or stale events. R77 intentionally
did not classify CombatOutcome as an owner-bearing event merely by its
attacker string.

The actor's authoritative World tick is already reached through
`WorldTickTarget`, which R76 can fence *in offline tests*. However, a
direct/off-path caller can invoke the producer or a domain publication
without going through WorldTickTarget. R78 addresses these source-specific
testable boundaries rather than claiming universal world authorization.

## R78 changes (opt-in test only)

`LocalLabPvmKillOutcomeProducer.installOfflineDeathAdmissionForTest`
accepts a callback for isolated fixture use. A proven confirmed NPC death
now checks that callback **before** consuming the dead scene's dedup entry.
A denied owner, stale generation or off-world call therefore cannot burn
the scene or reach observers. Without the test-only callback the existing
live producer and failure-isolated observer path are unchanged.

`R78OfflineVerifiedCombatFactBridge` binds one explicitly selected
WorldPlayer and generation, installs the producer's offline admission
callback, and installs a domain-bus listener guard. It deliberately
does **not** alter `CombatOutcome` or its event/public API. The test
bridge:

1. Checks the exact bound WorldPlayer and real World execution context
   before `LocalLabPvmKillOutcomeProducer` publishes a death.
2. Checks current World generation and R74 quiescence via
   `World.withOpenPlayerMutationOwnershipIfCurrent` under the actual
   player mutation lock.
3. Requires the expected `NPC_KILL`/`NPC_PVM` and the known opt-in
   source authority on the fact emitted from that producer.
4. Places a **thread-local identity proof for that exact immutable
   CombatOutcome instance**, only for the synchronous duration of the
   producer's observer -> domain bus call. The proof is always
   cleared in a `finally` block.
5. Validates the proof and real owner/generation **before each matching
   CombatOutcome listener**. A later listener cannot run using earlier
   approval if a higher-priority listener quiesced the actor.
6. Rejects direct fabricated facts with identical attacker/source
   strings and nested combat publications without a new producer proof.
   Noncombat global events continue unmodified.

The bridge is entirely offline/test-only; `LocalSession` and
`LocalWorldTickCoordinator` **do not install it**. R64's offline
UUID/epoch is not a live credential. The bridge's World owner/generation
checks are a test acceptance mechanism, not a server login protocol.

## Deterministic real-source regression

`R78OfflineVerifiedCombatOutcomeTest` enables the existing
`spk.local.r6MortalFixture` flag inside its isolated JVM and uses
the **real** `LocalLabPvmKillOutcomeProducer`, genuine lethal
`NpcEntity` fixtures, the actual WorldPulse compatibility tick and
real DomainEventBus priority callbacks.

- Tick 1: a verified removed-NPC death yields a combat fact delivered
  to high and normal subscribers. A separately constructed fact with
  the *same attacker ID and authority* is refused by the bus. A global
  notice still delivers.
- Tick 2: a different real removed-NPC death reaches the high-priority
  listener, which attempts a nested forged CombatOutcome publication
  (denied) and then marks the actor quiesced. A normal-priority
  subscriber must not run afterward. The original producer continues
  its established observer-failure-isolated semantics; the fact is
  already consumed and is not retried.
- Tick 3: an **untagged legacy scheduled callback** that was queued
  before final quiescence tries to produce another confirmed death.
  The new producer preflight refuses it before adding a dedup scene or
  sending any combat fact. Global events still deliver.
- An off-world direct producer invocation is refused, even with the
  correct WorldPlayer reference.
- Tick 4: after owner unregister, another attempted producer invocation
  fails on stale World generation. Unrelated World actor remains
  registered. No socket, account or game process is launched.

The R78 workflow independently tests Linux and Windows Java 11
bytecode, this real producer bridge, the inherited
`LocalLabPvmKillOutcomeProducerTest` and combat-observer suites, R77
domain bus boundaries, and inherited R63–R76 safety regressions. The
full server/Windows atomic build is a separate required CI gate.
**Only exact-head successes authorize a verified status.**

## Limits / follow-up

- The live `LocalWorldTickCoordinator` uses the original producer
  without this offline bridge. R78 does not authorize or block live
  player combat, account writes, socket callbacks or session termination.
- Another real source route is
  `PlayerCombatResolutionService`, which emits PvP kill/death
  outcomes and may interact with multiple WorldPlayers. It requires
  a separate attacker-plus-victim generation and lock-order audit;
  R78 **does not** claim coverage.
- The existing `CombatOutcomeObserverGroup` is synchronous and
  intentionally isolates observer failures. An observer can
  synchronously mutate an objective, match or collection log;
  any live integration must preserve this isolation while refusing
  stale owner-derived facts **before** mutable observers.
- A direct domain event from a third-party module that bypasses
  the test bridge has no source proof; the R78 test gate refuses
  it instead of guessing ownership from attacker text.
- The existing R63/R64/R72 file protocol is not a single
  power-loss-atomic transaction. No claim of protection against
  already executing non-cooperative disk calls or measured
  server latency/FPS gains is implied.

**R78 DRAFT / OFFLINE ONLY / UNMERGED / NO PRODUCTION ACTIVATION.**
