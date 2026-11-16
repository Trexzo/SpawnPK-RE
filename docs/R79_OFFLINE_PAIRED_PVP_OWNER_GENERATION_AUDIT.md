# R79 — offline dual-actor PvP generation and outcome-dispatch safety

**DRAFT / STACKED ON R78 / OFF BY DEFAULT / UNMERGED**

## Source-confirmed gap

The existing `PlayerCombatResolutionService.resolveImmediateOwned` has
valuable attacker and target generation checks. It calls `prepare` to
calculate weapon/timing/hook/damage, then uses nested
`World.withOpenPlayerOwnership` checks around the target HP mutation.
However:

- The ownership check in `withOpenPlayerOwnership` releases the
  player's **mutation lock** before executing the supplied action. The
  World **lifecycle** lock remains held, so normal World membership
  changes from a separate thread serialize, but the method is not a
  retained two-actor mutation-lock transaction.
- The source performs `finish` and emits `PLAYER_KILL` /
  `PLAYER_DEATH` facts **after** its nested owned-damage block ends.
- Neither actor's R74 final-save quiescence is consulted by the normal
  PvP resolver. A retired actor's reference is not itself permission
  to apply another hit or award downstream observer results.
- The immutable `CombatOutcome` exposes attacker/victim string IDs,
  not the verified pair of World generations. Copying those strings
  is not source authorization.

R79 does **not** replace this production route or pretend that R64's
offline session UUID authenticates real client/network login.

## Code added (offline test only)

`World.withOfflineDualPlayerMutationOwnershipForTest` runs a supplied
synchronous callback under **one** World lifecycle critical section,
with both WorldPlayers' mutation locks held in **ascending canonical
EntityId** order. Both exact player generations are rechecked after
the locks are acquired. It returns false on stale/closed ownership;
identical attacker/target identities are refused. There is no
installation into production gameplay. The normal single-owner World
API and networking behavior remain unchanged.

`R79OfflinePairedPvpOwnershipGate` is a test-only adapter to the
**real `PlayerCombatResolutionService.resolveImmediateOwned`** method.
The adapter binds one explicit attacker, one explicit target, their
original World generations and the R74 offline terminal fence.

It enforces:
1. A real WorldPulse execution context and both current generations,
   with neither actor terminal-quiesced, before entering the paired
   ownership section.
2. Rechecks inside the retained paired actor locks, then runs the
   original service's `resolveImmediateOwned`.
3. Wraps the actual `CombatDamageRules.calculate` result: after the
   damage rule returns, the adapter rechecks both generations and
   quiescence **before** the real PlayerLifecycleService HP mutation.
   This catches reentrant ownership changes during hook or damage
   calculation.
4. Uses the service's real `CombatOutcomeObserver` to forward its
   exact `PLAYER_KILL` and `PLAYER_DEATH` facts synchronously into
   the actual World DomainEventBus. A short-lived thread-local proof
   binds each *specific immutable fact instance* to its actual
   producer callback.
5. The bus's R77 optional per-listener admission rechecks both
   generations and final-save quiescence before **each** matching
   combat listener. Same-string forged facts and nested unsupported
   combat publication are refused. Unrelated global event types
   retain their original dispatch.

None of those optional guard hooks is activated by `LocalSession`,
the production PvP resolver, or a socket.

## Deterministic regression

`R79OfflinePairedPvpOwnershipTest` creates disposable isolated Worlds,
actual WorldPlayers and a deterministic real WorldPulse tick. It tests:

- A normal lethal `resolveImmediateOwned` applies the expected HP
  damage and emits exactly one `PLAYER_KILL` and one `PLAYER_DEATH`
  into real high/normal DomainEventBus listeners.
- An otherwise identical fabricated `CombatOutcome` (same attacker,
  victim, context and source string) is denied without a real
  producer's short-lived proof.
- A damage rule unregisters the **target** after preparing its
  calculation: the paired test rejects before any HP/death mutation
  or outcome. A separate scenario does the same for the **attacker**.
- A damage rule quiesces the attacker during the calculation: the
  adapter detects the terminal state immediately afterward, before
  applying HP damage.
- A previously quiesced target is refused before the calculation.
- A high-priority outcome listener emits a forged fact (denied) then
  quiesces the target. The lower-priority listener is refused and the
  subsequent death outcome is not delivered. An unrelated global
  event still publishes.
- Off-World invocation is denied, even if the caller holds both
  WorldPlayer references. Inherited tests verify the original
  attacker/target stale-generation behavior and all R63–R78 safety
  regressions under both Linux and Windows.

## Important limits

The last high-priority listener test is intentionally **nontransactional**:
by the time a listener observes a real lethal outcome, target HP has
already changed. Quiescing inside that listener prevents later
delivery, **not** rollback of HP, death lifecycle, committed rewards
or already delivered facts. If the server requires an all-or-nothing
two-fact publication protocol, it must define and implement that
separately.

The paired World helper uses reentrant Java monitors. Code executing
inside a guard can itself call `World.unregisterPlayer` reentrantly
from the same World actor; therefore ownership is explicitly
revalidated after all caller-controlled damage calculations and
before each outcome callback. Separate-thread membership changes
serialize through the shared lifecycle lock.

This proof covers the explicitly guarded test adapter only; it does
not cover unrelated direct `PlayerLifecycleService.applyDamage`,
WorldNpcRegistry mutations, plugin callbacks, actual network ingress,
or the existing unmodified live PvP resolver.

The R63/R64/R72 persistence protocol is still not a single
power-loss-atomic account+ledger+owner+marker transaction. R74's
cooperative shutdown witness cannot physically cancel already
in-flight uninterruptible filesystem writes.

No FPS/CPU/network latency or live-player stability claims follow
from these deterministic offline tests.

**R79 DRAFT / OFFLINE ONLY / UNMERGED / NO LIVE ACTIVATION.**
