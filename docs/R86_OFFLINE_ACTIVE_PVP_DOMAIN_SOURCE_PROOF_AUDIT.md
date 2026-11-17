# R86 — real active PvP handler to source-proven domain outcomes (offline only)

**DRAFT / STACKED ON R85 / NO LIVE ACTIVATION / UNMERGED**

## R85 source-confirmed gap

The actual `LocalSession` constructs a
`LocalPlayerInteractionHandler` for player Attack/Follow/Trade.
On a valid Attack, `tickAttack` resolves a real
`PlayerCombatResolutionService.resolveImmediateOwned` hit and emits
Player81WorldSync presentation and the target's HP update packet.

That *active* handler historically constructs the **four-argument**
`PlayerCombatResolutionService`, which chooses
`NO_OUTCOME_OBSERVER = outcome -> {}`. The resolver still allocates
`PLAYER_KILL` and `PLAYER_DEATH` on a lethal hit, but neither
semantic fact is forwarded to the real `DomainEventBus` on this
default active interaction path. R85 proved this with a real
Player81WorldSync Attack tick and a domain/objective observer that
received zero semantic facts. R79–R84 used another explicitly
source-wired **offline** resolver and did not fix the real active
interaction handler.

## R86 opt-in test integration

1. `LocalPlayerInteractionHandler` retains its existing constructors
   and their **four-argument default no-op semantic observer**.
   It now also exposes `forOfflineOutcomeTest`, the *only* code path
   in this PR that supplies a
   `R86OfflineActivePvpOutcomeBridge` to the actual
   five-argument `PlayerCombatResolutionService` constructor.
   No `LocalSession` constructor, network handler or production
   listener calls the test factory.
2. The test factory's `tickAttack` branch invokes the original
   `resolveImmediateOwned` inside the existing
   `World.withOfflineDualPlayerMutationOwnershipForTest`
   lock. Its World lifecycle + deterministic two-player mutation
   locks remain held through damage and synchronous semantic
   publication. The default live-compatible attack follows the
   original direct resolver call and is **not** redirected.
3. `R86OfflineActivePvpOutcomeBridge` binds one exact registered
   attacker generation and one exact registered target generation.
   The handler opens a temporary, ThreadLocal real-resolver scope
   only while executing the genuine owned PvP resolver under the
   above two-player lock. A bare direct source callback or copied
   CombatOutcome cannot create a valid live-in-scope proof outside
   this controlled test path.
4. The bridge validates the genuine resolver's immutable
   `CombatOutcome`: exact actor refs, `PLAYER_PVP`,
   `PLAYER_KILL` or `PLAYER_DEATH`, and original
   `PlayerLifecycleService.AUTHORITY`. It then installs exact
   *object identity* proof only for the synchronous
   `world.domainEvents().publish(fact)` call.
5. The R77 test-only `DomainEventBus` publication seam checks
   before **each** matching combat listener:
   same exact fact instance, no nested listener publication, live
   World execution context, current attacker/target generations and
   both actors not terminal under the R74 test fence.
   A previous listener can quiesce the target, in which case the
   next listener is refused. Unrelated global domain events pass
   without this PvP scope check.

This is an actual active handler and resolver callback, not an
imitation of combat damage. The source proof is **test-owned only**,
not authentication of real online player logins or original
SpawnPK protocol semantics.

## Source-exact regression

`R86OfflineActivePvpDomainBridgeTest` creates an isolated World,
real Player81WorldSync contexts and packet writers, and uses the
same movement adjacency, accepted C2S-style player action, weapon
and actual 20-tick lethal attack used by R85's active PvP test.

- **Positive:** active Attack resolves lethal canonical damage,
  remote HP packet 134 and cleared targeting. The actual resolver
  emits distinct `PLAYER_KILL` and `PLAYER_DEATH` instances
  through the real DomainEventBus. Both HIGH and NORMAL listeners
  observe each; a real objective observer advances only once on
  `PLAYER_KILL`. The World can still publish a global notice.
- **Nested and forged publications:** inside the HIGH kill
  callback, republishing the same genuine instance is refused as
  nested. Publishing another CombatOutcome with all source/tick
  strings copied is refused as unverified. A later tick's fake
  identical event also fails, and direct invocation of the bridge
  outside the verified resolver scope fails.
- **Mid-listener logout:** the HIGH kill listener quiesces the
  target. The normal listener is denied BEFORE its objective
  mutation. The resolver attempts its second death fact, but the
  bridge refuses it because the terminal actor has already
  quiesced. The original lethal HP/packet result survives, and
  unrelated global events remain deliverable. Already written
  earlier listeners would not be rolled back.
- **Misuse controls:** repeated domain gate installation, duplicate
  victim binding, and stale victim generation are refused.
- **Inherited R85:** the active `LocalSession`-shaped default
  handler's test still proves no semantic event is published
  without explicit opt-in. Its real standalone PvP lifecycle
  integration and all R63–R85 tests run on Linux/Windows.

The new dedicated workflow verifies Java11 server bytecode and
both Windows/Linux behavioral suites; the independent full server
build and Windows atomic-persistence checks must also pass on the
exact final PR head.

## Explicit limits

- **No live integration.** The real `LocalSession` continues to
  construct the original no-op observer handler. No production
  domains, combat scores, rewards, persistence or user accounts are
  modified by this draft. The R86 opt-in path must never be treated
  as a production feature flag.
- **No durable source identity.** The exact instance and ThreadLocal
  callback scope cannot identify logical combat events across
  different objects, retries, account transfers, or JVM restarts.
  R82's ordinals, R83 PREPARED and R84 OBSERVED files remain
  isolated diagnostic fixtures, not durable real combat IDs.
- **No complete cross-projection transaction.** Objective/match/
  collection consumers and account snapshots still lack a
  shared atomic authenticated commit. A listener failure can
  leave preceding listeners' writes intact; safe restart replay
  is not established.
- **Not an end-to-end network acceptance test.** Real local
  Player81WorldSync contexts are exercised, but there is no
  installed production packet/connection hook, hardware client,
  real user login, or reconstructed SpawnPK reward authority.
- No FPS/latency improvement, physical power-loss durability or
  actual client–server correctness claim follows from offline
  regression success.

**R86 OFFLINE ONLY / DRAFT / UNMERGED / NO LIVE ACTIVATION.**
