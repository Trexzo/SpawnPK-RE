# R88 — actual active PvP, independent projection writes and a fresh-JVM account restart

**OFFLINE ONLY / DRAFT / STACKED ON R87 / UNMERGED.**

## Source audit: durable logical operation ID is absent

Actual active PvP is handled by `LocalPlayerInteractionHandler.tickAttack` using
`PlayerCombatResolutionService.resolveImmediateOwned`. R86 supplies an
opt-in exact-object producer observer under an isolated World actor
ownership scope. R87 connects that test-only producer to actual objective,
match and Collection Log observers behind R80 generation/terminal checks
and a shared R81 bounded first-delivery receipt ledger.

The six instance fields of the real immutable `CombatOutcome` are
`attacker`, `victim`, `type`, `context`, `worldTick` and
`sourceAuthority`. No restart-stable producer-issued logical event ID
exists there. A meaningful combat may generate both a kill and a death
fact on the same tick. A copied fact with the same six fields is a
distinct Java object, and the R86 bridge's current-scope proof is not
persisted or transferable to another JVM.

The actual `WorldPlayerPersistence.captureAndSave` path is:
World tick/owner lock -> `PlayerSnapshotCodec.capture` -> immutable
snapshot with game fields and explicit `extension.*` values -> bounded
I/O worker -> `FilePlayerRepository.save` ->
`AtomicAccountPublisher` staged/forced bytes and required atomic
rename. The account snapshot does **not automatically include**
`ObjectiveProgressService`, `MatchSessionService` or
`CollectionLogService` state or their commit receipts. They are
independent mutable aggregates and are **not** a joint transaction.
The Collection Log's internal atomic CAS does not change that.

## R88 implementation and evidence

The original R86 and R87 test fixtures now accept an optional
test-owned `PlayerRepository`, without changing their existing
constructors or any `LocalSession` wiring. This allows R88 to run
the **real active handler** and the **real World-owned persistence
worker in the same isolated World**. All files are placed in fresh
temporary test directories. No production account directories,
live sockets, clients, rewards or gameplay policies are accessed.

`R88OfflineActivePvpRestartBoundaryTest` runs five deterministic cases:

1. A confirmed lethal active attack and normal objective +1, match
   participant +2/team +3 and synthetic caller-owned Collection Log
   absolute 7. The source's kill/death objects are distinct.
2. A victim terminal transition immediately after objective +1,
   before match/collection updates.
3. A victim terminal transition after participant +2, before the
   second match binding and Collection Log.
4. A caller-owned Collection Log resolver failure **after** objective
   and both match counters have changed.
5. A capacity-one R81 receipt ledger that accepts the kill but refuses
   a second distinct death source fact, without evicting prior receipts.

All five start at the actual `LocalPlayerInteractionHandler` and
`Player81WorldSync` with genuine HP 134 and death semantics, and
retain R80/R81 receipt and ownership protection.

For each scenario, the test schedules **two real World tick captures**
from the original attacker with exact World generation, awaits the
real bounded I/O worker and reloads the canonical account file.
The first snapshot contains ordinary gameplay state but no automatic
combat projection or event receipt. Before the second capture, the
test explicitly places
`extension.r88audit.claim=COMMITTED_BY_CALLER_NOT_AUTHENTICATED`
into the existing extension namespace. It round-trips through the
real codec to illustrate that arbitrary stored extension text is
**not** an authenticated producer operation ID, per-consumer commit
receipt or replay authorization.

The parent then closes the original World and starts a **new Java
process**, not just another object in the same JVM. The child creates
a new isolated World and persistence worker, loads and validates the
real account snapshot and confirms that fresh Objective, Match and
Collection Log services have no automatically restored projections.
The child cannot import the original R86 ThreadLocal proof, R81
identity receipts or original aggregate commit state. It reports
`R88_SEPARATE_JVM_ACCOUNT_RELOAD_QUARANTINED` with
`automaticReplayAuthorized=false`, `rollbackAuthorized=false`,
and `rewardAuthorized=false`, regardless of the previous scenario
or the untrusted extension's claimed status.

The test also asserts the current six-field `CombatOutcome` schema
by Java reflection: if a future producer adds new identity fields,
the source audit must be revisited rather than silently interpreting
a new field as trustworthy.

## What these tests DO NOT prove

- They do **not** simulate storage hardware power failure or establish
  directory-fsync durability. An atomic file rename is not a
  cross-service atomic commit.
- They do **not** say that all projections are lost in a real deployed
  server: the source has separate projection services, and a future
  repository might persist them independently. This experiment
  demonstrates the specific captured account snapshot does not
  include or authenticate their previous writes.
- They do **not** implement an automatic recovery manager or durable
  per-consumer receipts. There is insufficient source-backed
  operation identity to do that safely. The correct recovery
  assessment is **indeterminate/quarantined**, not retry or rollback.
- The objective/match values and Collection Log absolute 7 are
  explicit CUSTOM_LOCALLAB fixture policy, not recovered SpawnPK
  combat/scoring rules.
- There is no production LocalSession registration of the R86
  source bridge. Real live PvP still uses the default no-op semantic
  observer and receives no new projection/reward behavior.

## What would be needed for a safe R89 durable integration

1. Source-issued, restart-stable logical operation identifiers from
   an **authoritative** producer, explicitly binding attacker and
   target account generations and distinguishing legitimate repeated
   events and kill/death facts.
2. Authenticated persistence of those identifiers in the same
   accepted operation lifecycle, with definite committed or
   uncommitted states after a crash.
3. Per-projection idempotency records that are **atomic with each
   corresponding objective/match/collection write**, rather than
   separate best-effort witness files.
4. A defined, tested recovery protocol for partial fanout,
   cross-service ordering, process interruption and source callback
   exceptions, including which data is authoritative.
5. Dedicated live-source integration reviews and explicit approval
   before installing anything into LocalSession or actual players.

The R88 workflow runs this source-exact separate-JVM test and the
inherited R63–R87 regression set under Linux/Windows Java 11
bytecode. The full Gradle server build and Windows atomic-persistence
job must separately pass on the **exact final PR head**.

## Windows build-bootstrap reliability

The first exact-head R88 Windows and full Windows atomic-persistence jobs
failed **before Java compilation** when `server/gradle/bootstrap-gradle.ps1`
invoked PowerShell 5.1 `Expand-Archive`. The runner's Expand-Archive
implementation attempted a three-argument `ZipFile.ExtractToDirectory`
overload unavailable in that loaded .NET Framework assembly. Both jobs
reported `MethodCountCouldNotFindBest`, not a code or gameplay failure.

R88 replaces only that extraction call with the two-argument
`System.IO.Compression.ZipFile.ExtractToDirectory(zip, freshTempPath)`.
This is supported in Windows PowerShell 5.1 and newer .NET runtimes.
The pinned Gradle 8.10.2 URL and SHA-256 verification remain unchanged
and occur **before** extracting. Extraction still targets a new GUID
temporary directory; archive layout is validated and the expanded folder
is moved to the original cache location. No bypass of an invalid
hash, unsupported atomic publication or normal error handling is added.

Only a passing exact-head Windows workflow establishes the repaired
bootstrap was successful on the runner. All gameplay remains offline.

**R88 OFFLINE ONLY / DRAFT / UNMERGED / NO LIVE ACTIVATION.**
