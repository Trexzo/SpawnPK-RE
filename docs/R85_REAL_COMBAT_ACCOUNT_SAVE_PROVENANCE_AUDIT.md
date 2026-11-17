# R85 — actual combat producer to account persistence boundary

**DRAFT / STACKED ON R84 / OFFLINE ONLY / UNMERGED**

## Source-confirmed routes

The checked-in LocalLab server contains two distinct semantic-combat
publishers:

1. `LocalWorldTickCoordinator` configures a
   `LocalLabPvmKillOutcomeProducer` with a real
   `world.domainEvents().publish(outcome)` observer.
   Only an already-confirmed **removed NPC death** can create its
   opt-in `NPC_KILL` fact in this route.
2. `PlayerCombatResolutionService.finish` creates a separate
   `PLAYER_KILL` and `PLAYER_DEATH` fact after lethal PvP damage.
   The direct resolver calls the supplied `CombatOutcomeObserver`;
   R79/R82 tests use a real resolver with explicitly installed
   isolated World ownership and source-proof adapters. **This does
   not establish that LocalSession wires the same PvP observer
   into live network gameplay.**

A separate **actual account persistence path** exists:

`LocalSession` -> `WorldPlayerPersistence.captureAndSave` or
`captureDeferredFinalSave` -> `PlayerSnapshotCodec.capture`
on the World execution context -> single bounded persistence IO
worker -> `FilePlayerRepository.save` ->
`AtomicAccountPublisher.publish`, with forced staged bytes and
required atomic replacement.

The established `PlayerSnapshotSchemaV1.capture` encodes bank,
equipment, movement, pet, skills, appearance and other gameplay
fields. `PlayerSnapshotCodec.capture` also preserves explicitly
provided `PlayerSnapshotExtensionState` values under
`extension.*`. This extension mechanism is a storage namespace;
**its presence does not establish a producer-issued combat ID or
atomic commit of other services.**

Objective, match and Collection Log projections are separate
`ObjectiveProgressService`, `MatchSessionService`, and
`CollectionLogService` instances. A `CollectionLogRepository`
interface exists, but its contract supplies no common durable
transaction with account or objective/match scores.

There is still no source-confirmed immutable `CombatOutcome`
logical operation ID, authenticated cross-restart event provenance
or per-projection shared commit/acknowledgment protocol. A source
tick, attacker name or R82 test-local ordinal cannot substitute.

## R85 actual runtime-boundary regression

`R85OfflineCombatAccountPersistenceBoundaryTest` reuses R82's
**actual** `PlayerCombatResolutionService.resolveImmediateOwned`,
real WorldPulse and DomainEventBus, real objective/match/collection
consumers and verified source-ordinal receipts. The R82 test
fixture gains an optional constructor accepting a caller-supplied
`PlayerRepository` (all existing R82-R84 tests keep their original
construction).

The R85 fixture uses a temporary
`FilePlayerRepository` with a **test-controlled isolated directory**,
never a LocalAccountProfiles production account path.

1. Complete lethal PvP results in actual objective +1,
   participant +2, team +3, synthetic Collection Log absolute 7.
2. On the next real WorldPulse tick, call the actual
   `world.persistence().captureAndSave` with the original attacker
   and exact registered World generation. Verify the World capture
   ordinal, await the real single-worker save completion and queue
   drain **off** the World actor, and load from the actual repository.
3. Compare the real saved `PlayerSnapshot` to the independent
   already-updated projection services: the account capture
   contains no automatic objective/match/collection score or
   semantic-combat receipt records. This is a concrete assertion
   about the fixture's real captured bytes, **not** a universal
   guarantee against all future extension schemas.
4. On a subsequent WorldPulse tick, explicitly add a harmless,
   test-owned `extension.r85audit.note=TEST_ONLY_NOT_A_COMMIT`
   field using the genuine PlayerSnapshotExtensionState
   namespace API. Capture/save again; the extension round-trips
   through the worker and actual file codec. **It is not a
   transaction ID or commit certificate.**
5. Construct a **new isolated World** with a new real persistence
   worker but the same temp file repository. Load and
   `PlayerSnapshotCodec.applyValidated` into a fresh WorldPlayer.
   The explicitly injected test extension restores. Fresh
   objective/match/collection services have no recovered projection
   state merely because account fields restored.
6. Confirm the canonical temp account file exists and the ordered
   immutable World capture ordinal advanced. Neither consumer
   replay nor login, rewards, live sessions or real profile
   persistence is attempted.

This is the existing account publisher and worker execution path,
not a separate imitation of the codec. It is also NOT proof that
the real PvP producer is registered by a LocalSession.

## R85 complete-tree lexical route inventory

`R85OfflineCombatPersistenceRouteInventoryTest` scans every checked-out
`src/spk/**/*.java` source file, with exact, named source anchors
for PvP/PvM emitters, domain publication, World-thread account
capture, codecs, file publisher and the three consumer mutations.

It prints a source-file candidate inventory for
`PlayerCombatResolutionService`, `CombatOutcomeObserverGroup`,
account capture and snapshot extension references. Such **lexical
references are NOT a call graph**; test-only fixture adapters may
reside under the regular source directory, and comments may refer
to classes without making them active runtime dependencies.

This audit detects source-route drift and directs later manual
integration review, but does not claim complete dynamic control
flow, runtime reachability, authenticated source IDs or actual
production wiring.

## Next integration requirements and limitations

Before a real restart-safe combat transaction could be designed,
the project would need all of the following facts established from
real runtime/server code:

- An authenticated, **producer-issued, restart-stable logical combat
  event ID** that distinguishes repeated legitimate hits and binds
  both actor generations and source authority; R82's UUID and
  ordinal are intentionally insufficient.
- Explicit, source-backed objective/match/collection projection
  bindings in the intended live gameplay and persistent-account
  lifecycle; none are enabled by this offline change.
- Per-projection idempotency/commit/ack contracts and a defined
  atomic-or-compensating recovery policy across independently
  mutable state and any on-disk writes.
- A deliberate protocol for restart ambiguities, failed or
  missing evidence and partially executed observers; R83/R84
  correctly quarantine rather than automatically retry.
- Real login/network acceptance and persistence migration tests
  before production activation.

R85 has **no production account writes or player effects**. Its test
account is placed in a new temp directory; the file atomic
publisher still does not prove parent-directory fsync, hardware
power-loss durability or cross-service all-or-nothing commit.
No CPU/FPS/network-latency performance claim is made.

The R85 workflow compiles the Java 11 server target and runs new
tests plus inherited R63–R84 regressions on independent Linux and
Windows jobs. A separate full server build and Windows
atomic-persistence workflow must also pass on the **exact** PR head.

**R85 OFFLINE ONLY / DRAFT / UNMERGED / NO LIVE ACTIVATION.**
