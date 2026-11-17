# R82 — test-scoped source emission ordinals and shared combat projection receipts

**DRAFT / STACKED ON R81 / OFFLINE ONLY / UNMERGED.**

## What the real source DOES and DOES NOT identify

The existing `PlayerCombatResolutionService.finish` creates two
independently allocated `CombatOutcome` facts when one lethal PvP hit is
confirmed: `PLAYER_KILL` followed by `PLAYER_DEATH`. Both facts carry
attacker/victim string references, the same World tick and semantic source
authority. The immutable `CombatOutcome` API does **not** carry a
unique producer-issued transaction/event ID, persistent event ordinal, or
authenticated player generation.

A hash or composite of attacker/victim/type/tick/authority is *not* a
safe identity: real combat might produce distinct events with matching
observable fields, and caller-controlled strings cannot establish
provenance. Nor is an object identity a durable cross-JVM identity.

The existing R79 fixture already proves the **exact fact object** during
the current synchronous real DomainEventBus callback. R81 uses that
proof to prevent the same object from running one group twice. R81's
ledger is **per group**; a second independent
`CombatOutcomeObserverGroup` configured to write the same mutable
Objective/Match/Collection services could create a second receipt
ledger and double-count the same source fact.

## R82 new opt-in test mechanism

1. `R79OfflinePairedPvpOwnershipGate` now offers
   `installOfflineSourceEmissionIssuerForTest`. On the real
   `PlayerCombatResolutionService` observer callback, AFTER R79
   confirms the exact bound attacker/target generation and fact
   provenance, the test adapter issues a monotonically increasing
   **source-local emission ordinal** (1 for the kill fact, 2 for the
   death fact). An optional source-scope callback is cleared in a
   finally block at the end of the real synchronous domain publication.
   A default R79 fixture with no installation retains the original
   live-compatible dispatch behavior.
2. `R82OfflineSourceScopedCombatReceiptLedger` is one shared,
   bounded, synchronized receipt store configured for that **single
   test issuer**. It binds the exact currently emitted fact object to
   its source ordinal while that callback is executing. The R79
   thread-local proof must also be active before the first claim;
   no other code can authorize a fresh event merely by copying its
   attacker/victim/tick/source strings.
3. **Multiple independent observer groups** install the same ledger
   using R81's optional `installOfflineFirstDeliveryAdmissionForTest`
   seam. The first group claims the source ordinal before any
   projection write. Every later group refuses the already claimed
   ordinal before touching its own objective/match/collection observers.
   A partial fanout remains consumed; the ledger never retries it.
4. At capacity, new source ordinals fail closed; the ledger never
   evicts earlier consumed ordinals. The source-scoped identity
   consists of a randomly generated **test JVM scope UUID** and
   one monotonic ordinal, not a credential or a globally durable ID.
   The actual in-memory key storage is the ordinal set bound to this
   one issuer object, and its size cannot exceed the configured
   capacity.
5. The existing R80 owner/generation guard runs **before** first
   delivery, and the original direct downstream mutation guards
   still check before each applicable projection write. The real R79
   domain proof remains mandatory. An unrelated global domain event
   still uses its original dispatch path.

Production LocalSession, PvP dispatch, World ingress, player scoring,
Collection Log and network code do **not** install any R79/R82 hooks.
The normal public `CombatOutcome` API and original gameplay combat
calculation are unchanged. No live account has been accessed.

## R82 source-exact tests

`R82OfflineSourceScopedCombatReceiptTest` drives one real isolated
World, two registered World actors, lethal
`PlayerCombatResolutionService.resolveImmediateOwned`, actual
DomainEventBus priority subscriptions, and **two separately
constructed CombatOutcomeObserverGroup instances** writing into the
SAME `ObjectiveProgressService`, `MatchSessionService`, and
`CollectionLogService`.

- Normal lethal hit: both domain subscriptions observe the same
  kill and death fact instances, but the shared ledger claims each
  source ordinal only once. Objective progress is 1, participant
  score 2, team score 3 and synthetic Collection Log absolute count 7.
  The source emits two distinct ordinals even when both events share
  one tick and actor pair.
- Partial delivery: the first group updates objective progress then
  intentionally stops before match and Collection Log. The second
  independent group must NOT rerun objective or silently resume
  previously skipped match/collection work.
- Resolver failure: the first group advances objective and match
  scores, then an injected caller-owned Collection Log resolver
  failure is isolated. The second group cannot run the resolver a
  second time or replay any earlier write.
- Forged equivalent fact: publishing a new `CombatOutcome` with the
  exact same fields through DomainEventBus is rejected by R79.
  Submitting the object directly to the second group is refused
  by R82 because no current real source emission ordinal and exact
  proof exist. Replaying the old event after its source scope closes
  also cannot mutate any projection.
- Capacity 1: one source ordinal is consumed, the other is refused;
  old receipts remain present and no side effects are replayed.
- Two independent isolated World/test issuers have distinct scope
  UUIDs, even though each of their *local* ordinals starts at 1.
  Neither shared identity nor replay permissions survive JVM restart.
- A global/unrelated domain notice is still delivered normally.

Dedicated Linux and Windows workflows run the R82 test, R81's
concurrent receipt regression, all inherited R63–R80 suites, and
Java 11-compatible server bytecode verification. The full server
build / Windows atomic regressions are independent requirements.
Only the exact final-head CI results establish pass status.

## Nonclaims and next R83 target

This **does not** introduce an actual reconstructed SpawnPK combat
event ID or durable idempotency. The source-local ordinal is assigned
only by the OFFLINE R79 adapter and is NOT part of the real event
contract or stored on disk. Repeating an event after an actual crash
can create a new World, emitter and nonce, so R82 does not detect it.

An offline/shared receipt across groups is not an atomic transaction:
a prior observer's write remains committed if later projections abort.
We intentionally do not retry or roll back it. A future durability
design would first require a real producer-issued stable operation
identifier, a defined commit/ack/recovery order and per-projection
idempotency semantics; it must not treat arbitrary player/tick fields
as such an identifier.

R63/R64/R72 file transactions are still not power-loss-atomic as a
whole. The R74 forced-IO shutdown witness does not cancel arbitrary
uncooperative filesystem calls already running.

**R82 DRAFT / OFFLINE ONLY / UNMERGED / NO LIVE ACTIVATION.**
