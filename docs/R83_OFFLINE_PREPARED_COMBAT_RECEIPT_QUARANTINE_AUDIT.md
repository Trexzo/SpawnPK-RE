# R83 — offline PREPARED combat-receipt witness and conservative restart quarantine

**DRAFT / STACKED ON R82 / OFFLINE ONLY / UNMERGED.**

## Source audit and why no durable completion is claimed

The source-exact `PlayerCombatResolutionService.finish` creates separate
immutable `PLAYER_KILL` and `PLAYER_DEATH` `CombatOutcome` objects,
both carrying attacker/victim strings, a World tick and a semantic source
authority. Neither fact contains a stable producer transaction ID.
R82's opt-in ordinal is generated solely inside the isolated
`R79OfflinePairedPvpOwnershipGate` callback and resets in a new World.
It cannot be used to identify a combat event across a JVM restart.

The real objective, match and Collection Log services mutate separate,
in-memory aggregates. The Collection Log has a useful atomic CAS *within
its own service*, but those three services do not implement a common
durable commit, transaction ID, prepare/commit transaction, or atomic
recovery protocol. Issuing one receipt per combat event does not make
their effects an atomic or exactly-once group.

`AtomicAccountPublisher` offers forced staged file bytes and a
**required** atomic replacement. It explicitly cautions that
parent-directory fsync and physical power-loss durability are separate.
R83 reuses this primitive only for a disposable offline witness, not
for production player accounts.

## R83 changes (all explicit test-only)

`R82OfflineSourceScopedCombatReceiptLedger` gains an optional
`installOfflinePreparedAdmissionForTest` callback, evaluated AFTER
the existing R79 exact-object/source-context proof, and AFTER the
receipt-capacity check, but BEFORE a first in-memory claim and BEFORE
the first objective/match/collection observer. A failed or ambiguous
prepared-record write refuses delivery, with no projection mutation.

`R83OfflinePreparedCombatReceiptJournal` is a separate test-only
writer/reviewer:

- The caller explicitly supplies a **synthetic UUID fixture operation
  identifier**. That UUID does **NOT** come from a real SpawnPK combat
  producer; no real source-issued durable ID was discovered. Each
  R82 source-local ordinal selects a separate canonical PREPARED file.
- A cooperative OS file lock in the **fixture-owned directory** covers
  no-overwrite checks, ambiguous staging-file review and publication.
  This lock does not protect against non-cooperative writers, and the
  existence of its lock *path* is not evidence a process holds it.
- An exact-schema properties witness includes fixture operation ID,
  R82 test source scope, its local ordinal, attacker/victim/type/context,
  World tick and semantic authority as **diagnostics only**, plus a
  SHA-256 integrity checksum. The checksum detects ordinary accidental
  corruption; it is NOT a cryptographic signature, authentication token
  or authoritative event identifier.
- `AtomicAccountPublisher.publish` writes a forced adjacent staged
  file and requires atomic replacement. Publication and a successful
  read-back of the canonical PREPARED evidence happen **before** any
  combat projection observer can mutate. There is **no** COMMITTED
  phase, no success/ack certificate and no durability claim for the
  parent directory or hardware power loss.
- On duplicate canonical evidence, malformed/changed properties,
  invalid/missing witness, abandoned `.r16-*.stage` evidence, lock
  contention, unsupported atomic move or injected interruption,
  the writer and reviewer **fail closed**. No stale staged file is
  promoted or deleted.
- Read-only `review(root, fixtureUUID, ordinal)` returns only
  `PREPARED_QUARANTINED`, `MISSING_BLOCKED` or
  `INDETERMINATE_BLOCKED`. In ALL states,
  `replayAllowed=false`, `projectionComplete=false`,
  `loginAllowed=false` and `rewardAllowed=false`. The journal
  never attempts to resume, infer a missing mutation, roll back scores
  or release player accounts.

The R79, R80 and R82 production-compatible default paths are unchanged
unless a disposable offline test explicitly installs this callback.
No live `LocalSession`, protocol packet handler, account repository or
combat projection registers it.

## Source-exact and separate-JVM regression

`R83OfflinePreparedCombatReceiptRecoveryTest` reuses the actual
R82 isolated World fixture (its previously private test fixture was
made package-visible for test reuse). Each live-in-test PvP hit is
still calculated by real `PlayerCombatResolutionService` and
delivered via the real WorldPulse, DomainEventBus and two independent
projection groups writing original objective, match and Collection Log
services.

Deterministic acceptance cases:

1. A real lethal source emission produces separately PREPARED source
   ordinals 1 and 2 BEFORE projections: objective=1,
   participant score=2, team score=3, synthetic absolute Collection
   Log count=7. A **second JVM process** reopens the same filesystem
   evidence, sees `PREPARED_QUARANTINED` and explicitly cannot
   authorize replay, complete projection, login or rewards.
2. A fresh World/producer reuses the SAME synthetic test operation ID.
   Its new source scope/ordinals are deliberately refused by the
   existing PREPARED receipts. No projection is applied; unrelated
   global World events continue. This demonstrates why an untrusted
   recycled UUID would *wrongly suppress an unrelated real event*.
3. An unrelated, freshly generated **test** operation UUID can write
   independent PREPARED evidence and perform the fixture projections.
   This is NOT evidence a live game can assign trustworthy IDs.
4. Inject a fault immediately AFTER successful PREPARED publication
   but BEFORE the first projection. Both downstream groups refuse,
   no objective/match/collection value changes, and the child JVM
   still reports the witness `PREPARED_QUARANTINED`, never complete.
5. Inject a fault BEFORE PREPARED publication. The file is missing,
   no projection mutates, and an independent JVM reports
   `MISSING_BLOCKED`, not replay permission.
6. Stop the first observer group after updating an objective, or
   make the Collection Log's caller-owned resolver fail after
   objective and match scoring. PREPARED still **does not** certify
   the incomplete projection group or authorize retry on a second
   observer group.
7. Tamper with the canonical file and leave an ambiguous staged
   replacement next to a separate valid record. Independent JVM
   reviewers report `INDETERMINATE_BLOCKED`; the stage is not
   automatically renamed, deleted or promoted.
8. Duplicate and late offline write-policy installations are
   rejected. Inherited R63–R82 test suites run without installing
   the new policy and retain their original behavior.

The dedicated R83 workflow builds Java 11-compatible server class
files under Linux and Windows Java 21, invokes the new test and
inherited regression mains, and separately runs the full repository
server build plus Windows atomic-persistence tests.

## Nonclaims and next work

- **Not cross-process exactly once.** The synthetic fixture UUID
  externally supplies the "same operation" identity. Real
  `CombatOutcome` has no such durable producer-issued ID; old and
  new Worlds cannot be safely associated without another trusted
  transaction protocol.
- **Not power-loss atomic.** A forced file and atomic rename do not
  prove directory fsync or all-data durability across hardware
  failure. Nor is a witness plus several separate gameplay stores a
  single atomic commit.
- **Not automatic recovery.** PREPARED can describe zero, some or all
  projection side effects. R83 intentionally quarantines **every**
  restart condition rather than guessing which effects completed.
- **Not live gameplay.** The journal operates only in fresh
  temporary fixture directories and never updates player
  persistence, accounts, rewards, sockets, quests or actual SpawnPK
  collection records.
- A future R84 may audit an explicit *production-source* operation
  identity and durable per-projection boundaries. Without confirmed
  source identity, safe recovery remains manual/quarantined and
  additional automatic retries are unjustified.

**R83 OFFLINE ONLY / DRAFT / UNMERGED / NO PRODUCTION ACTIVATION.**
