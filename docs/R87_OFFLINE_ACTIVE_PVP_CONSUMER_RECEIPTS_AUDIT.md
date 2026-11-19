# R87 — offline active PvP to real projection consumers

**DRAFT / STACKED ON R86 / OFFLINE ONLY / UNMERGED**

## Source boundary

R85 confirmed that LocalSession constructs LocalPlayerInteractionHandler
with the default four-argument PlayerCombatResolutionService: its
NO_OUTCOME_OBSERVER discards semantic kill/death facts although real
lethal damage and HP packet 134 still execute. R86 introduced
an explicitly opted-in *test factory* that wires a source-proven
CombatOutcome callback into this actual active attack handler.

R87 uses that REAL active handler and source-proven DomainEventBus
rather than the older R79 separately instantiated PvP resolver.
No production LocalSession constructor, account, login or packet path
installs the new test-only integration.

## Components and exact evidence

- R86OfflineActivePvpOutcomeBridge exposes
  isVerifiedFactInCurrentListenerForTest, true only for the exact
  CombatOutcome object inside the active owned resolver's live-in-scope
  synchronous WorldPulse DomainEventBus listener. Copied attacker/victim
  strings or matching World ticks do not qualify.
- Two separate CombatOutcomeObserverGroup instances subscribe at
  NORMAL and LOW priorities. Each uses original
  CombatOutcomeObjectiveObserver, CombatOutcomeMatchObserver, and
  CombatOutcomeCollectionLogObserver instances connected to the SAME
  underlying ObjectiveProgressService, MatchSessionService and
  CollectionLogService.
- Both groups share ONE R81OfflineCombatProjectionReceiptLedger,
  configured with the current R86 source-proof predicate. An exact
  object can claim a receipt only once, before consumer side effects.
  Second-group duplicate delivery cannot double-count objective or
  match scores or rerun the Collection Log count resolver.
- The original R80OfflineCombatProjectionOwnershipFence checks both
  original World actor generations, R74 terminal quiescence and
  current WorldPulse before each observer and actual mutation.
  A mid-fanout terminal transition blocks later writes without
  rolling back earlier writes.
- Kill and death are separate immutable source facts. Bounded receipt
  capacity refuses NEW events instead of evicting old receipts.

## Real-source regressions

R87OfflineActivePvpProjectionReceiptsTest reuses the R86 original active
handler fixture (made package-visible for test reuse) and real
Player81WorldSync attack/HP presentation. It independently tests:

1. Normal lethal attack, both semantic facts through BOTH independent
   groups: objective +1, participant score +2, team score +3 and
   synthetic absolute collection count 7, resolver called once.
   Two facts each receive one first-delivery claim. Neither real
   objective reward claiming nor match completion is invoked.
2. Direct fresh CombatOutcome with identical actor/type/tick strings
   submitted to a group on a later World tick is refused for lack
   of current R86 source proof. Replaying a previously claimed
   genuine object is refused as a duplicate.
3. Victim terminal transition after objective mutation, before
   match observer: further group observers and other domain
   subscribers are suppressed. Earlier objective progress remains.
4. Victim terminal transition before the second matching match-score
   binding: the participant score remains 2; team and collection
   mutations are refused, with existing failure isolation retained.
5. Caller-owned collection resolver throws after objective and match
   changes: a later independent group cannot rerun the resolver.
6. Victim terminal transition during collection resolver: original
   post-resolver/pre-CAS R80 admission prevents a stale absolute count
   commit. Earlier objective and match writes remain in memory.
7. Receipt capacity 1 admits the kill and refuses the distinct death
   fact without evicting a prior receipt.

The fixture's objective +1, participant +2, team +3 and absolute
collection 7 are synthetic CUSTOM_LOCALLAB test bindings, **not**
recovered SpawnPK PvP reward or Collection Log policy.

## Boundaries

No live integration is enabled: LocalSession retains NO_OUTCOME_OBSERVER.
The R81 receipt is in-memory and keyed to the exact source-proven
CombatOutcome instance, not a durable logical event identifier.
There is no across-restart replay safety, source-authenticated durable
event ID, atomic multi-projection commit, automatic replay/rollback,
online login, persistent player score update, real game reward,
native SpawnPK authority or FPS/latency measurement.

The R82–R84 source-local ordinal, PREPARED and OBSERVED forensic
fixtures are **not** upgraded into real active-handler persistence
or a durable gameplay transaction by R87.

Dedicated Linux/Windows R87 CI runs actual R87 and inherited R63–R86
regressions with Java 11 bytecode. Full server build and Windows
atomic-persistence tests must pass independently on the exact
final head before marking R87 fully verified.

**R87 OFFLINE TEST ONLY / DRAFT / UNMERGED / NO LIVE ACTIVATION.**
