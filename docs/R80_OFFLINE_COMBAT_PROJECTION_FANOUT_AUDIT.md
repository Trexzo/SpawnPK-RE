# R80 — offline combat projection fanout and terminal-owner admission

**DRAFT / STACKED ON R79 / OFF BY DEFAULT / UNMERGED**

## Source-confirmed problem

R79 verifies the attacker and victim World generations around the real
`PlayerCombatResolutionService.resolveImmediateOwned`, and rechecks
before each real `DomainEventBus` subscriber. But a domain subscriber
can itself be a `CombatOutcomeObserverGroup`, which synchronously
runs several independent projection observers **inside one domain bus
subscriber**. R79's per-domain-listener check does not interpose
between the group's objective, match and Collection Log consumers.

Specifically:

- `CombatOutcomeObserverGroup` originally ran all observers in order,
  deliberately isolating a failing observer so later consumers can
  still receive an already-published semantic fact.
- `CombatOutcomeObjectiveObserver` runs `objectives.advance`
  independently for each matching binding.
- `CombatOutcomeMatchObserver` writes independent participant and
  team counters for each matching binding.
- `CombatOutcomeCollectionLogObserver` gathers authoritative
  absolute kill counts through a caller-provided resolver **outside**
  the Collection Log's atomic write, then performs one atomic
  `observeKillCountsAtomically(updates)` comparison-and-set batch.
  The resolver itself may invoke other code, including test-only
  terminal/logout transitions. A pre-resolver admission is not a
  substitute for a post-resolver, pre-commit check.

Without a trusted **same-actor generation/quiescence** check at each
boundary, one legitimate PvP outcome may partially update scores
*after* the World actor has quiesced.

## Offline-only implementation

All new checks are explicit test-installation hooks. The original
uninstalled live paths execute their original callback logic.

1. `CombatOutcomeObserverGroup.installOfflineProjectionAdmissionForTest`
   adds a callback before **each** sub-observer. A denied admission
   **terminates** the remainder of that fanout, rather than throwing
   into the legacy failure handler. Nonterminal observer exceptions
   still use their existing failure-isolated reporting.
2. `CombatOutcomeObjectiveObserver.installOfflineMutationAdmissionForTest`
   adds a recheck immediately before each matching
   `ObjectiveProgressService.advance`.
3. `CombatOutcomeMatchObserver.installOfflineMutationAdmissionForTest`
   adds a recheck immediately before each matching participant/team
   `MatchSessionService.tryAdjustPresentParticipant...Score` write.
4. `CombatOutcomeCollectionLogObserver.installOfflineMutationAdmissionForTest`
   checks before each matching authoritative resolver call and once
   **after all resolver calls, immediately before the existing atomic
   Collection Log CAS batch**. An owner that becomes invalid inside
   an external count resolver cannot commit its stale update.
5. `R80OfflineCombatProjectionOwnershipFence` is a disposable
   actor-pair test adapter to the original R79 World generation
   bindings and R74 terminal quiescence. It only authorizes
   `PLAYER_PVP` facts with the explicitly bound attacker/victim
   entity strings while the real World execution context and both
   World generations remain valid and neither actor is terminal.
   **String matching is not a source proof:** R80 is installed only
   behind R79's real source-to-DomainEventBus identity attestation.

No existing production LocalSession, login, combat resolver, plugin,
score service, or network packet path installs the R80 hooks.

## Real-source regression

`R80OfflineCombatProjectionFanoutTest` uses the existing
**real `PlayerCombatResolutionService.resolveImmediateOwned`**
inside an isolated WorldPulse via R79. The actual
`DomainEventBus` delivers a `CombatOutcomeObserverGroup`
containing all three original projection service implementations.

The only Collection Log definition in this test is a synthetic
`CUSTOM_LOCALLAB` count-enabled *fixture*. The test's caller-owned
absolute count **7** is a test constant; it is **not a reconstructed
SpawnPK PvP Collection Log formula or actual player kill statistic**.

Independent cases:

- Normal lethal PvP: objective progress becomes 1/1, match participant
  score 2, match team score 3 and synthetic Collection Log absolute
  count 7. Both real `PLAYER_KILL` and `PLAYER_DEATH` facts can
  enter the group; only matching kill bindings mutate projections.
  No rewards are claimed or matches completed.
- After the objective observer finishes, a test-controlled terminal
  transition occurs. The group refuses subsequent match and collection
  consumers. Objective progress stays committed; no rollback claimed.
- After the **first objective binding** advances, the actor quiesces
  before the second binding. The new direct objective mutation check
  rejects the second; the group's subsequent consumers are stopped.
- After the **first match participant binding** updates score, the
  target quiesces before the team binding. The new direct match
  mutation check rejects the team write; collection does not run.
- Inside the Collection Log's caller-owned authoritative-count
  resolver, the attacker quiesces after an absolute count is computed.
  A second check **before CAS** refuses the entire Collection Log
  update batch; already completed objective and match scores persist.
  The group's failure handler remains failure-isolated.
- A direct off-World group invocation and a post-unregister
  stale-generation invocation cannot write any projection, even if
  their combat subject strings match. The upstream R79 source proof
  remains mandatory for proper ownership of new facts.
- An unrelated global domain notice remains deliverable after
  test quiescence. Inherited original observer behavior and
  R63–R79 safety checks run unchanged on separate Linux/Windows
  runners with Java 11 bytecode validation.

## Boundaries

This is **not** an all-or-nothing multi-projection transaction.

When one observer has already committed objective progress or
participant scores, later logout makes the remainder fail closed,
but it does not roll back the first observer's effects. A true
cross-service atomic commit would require an explicit durable
transaction protocol, stable operation IDs, compensation or
idempotent replay definitions—none can safely be inferred here.

The test-only Collection Log gate can reject quiescence arising
during resolver execution, but it does not make an arbitrary
external resolver side-effect-free. Its CAS still supplies the
existing stale-absolute-count protection.

No authenticated live session integration, game-network protection,
real player rewards, production match scoring rules, or native
SpawnPK Collection Log ownership is claimed.

R63/R64/R72 file commits remain non-atomic across complete power
loss. R74 forced-shutdown witnesses do not cancel arbitrary
uncooperative filesystem writes. No measured CPU/FPS/performance
improvement follows from this offline safety suite.

**R80 OFFLINE ONLY / DRAFT / UNMERGED / NO LIVE ACTIVATION.**
