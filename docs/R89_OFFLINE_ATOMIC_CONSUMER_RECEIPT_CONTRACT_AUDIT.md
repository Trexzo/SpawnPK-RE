# R89 — per-consumer atomic receipt contract, using a genuine test-proven active PvP source

**DRAFT / STACKED ON R88 / OFFLINE ONLY / UNMERGED / NO PRODUCTION ACTIVATION.**

## Why a production durable identity still does not exist

R88 checked the **actual** immutable `CombatOutcome` source schema:
attacker, victim, type, context, World tick and authority. There is
no authenticated restart-stable operation ID or durable lifecycle
identifier. R86's original active handler emits a genuine outcome
only through an explicit offline test factory. R87's exact-object
in-memory R81 ledger prevents duplicate fanout during the same JVM
but does not recover after restart. R88's real World-owned account
save/reload does not commit the separate objective, match or
Collection Log services as a transaction.

**Do not manufacture a durable key from public actor names, World
ticks or payload hashes.** Two genuine events can have identical
public fields; an attacker may also supply equivalent data. A fresh
random UUID generated after reopening a JVM cannot be asserted to
refer to the same physical combat operation.

## Reference issuer: explicit synthetic contract ONLY

`R89OfflineVerifiedEmissionTokenIssuer` is an offline bounded issuer
bound to the existing R86 exact-object DomainEventBus proof.

- It can issue a token only inside the **actual currently active
  R86 source-proven listener**, never from an external caller's
  copied CombatOutcome.
- It binds one synthetic UUID token to one exact Java event instance
  via an IdentityHashMap. Repeated issue requests for the **same**
  verified source instance return the **same** token, not an
  independent UUID. Kill and death emitted by one genuine lethal hit
  get separate tokens.
- A token must be accompanied by the exact active fact and the
  original issuer to authorize a test consumer write. After the
  source callback ends or World exits, replay/forgery is refused.
- Its UUID is a **test-generated stand-in for a missing real
  producer-issued logical ID**. It is not persisted as an
  authenticated source record or independently verifiable in a new
  JVM. Never accept it as a production operation or credential.

## Reference atomic per-consumer value/receipt

`R89OfflineAtomicConsumerReceiptStore` is a separate, **test-only
reference store** (not a replacement for ObjectiveProgressService,
MatchSessionService or CollectionLogService). One configured consumer
has one canonical properties file under a temporary test directory.

The store:
1. Requires an active, matching R86 source-proven token for the exact
   player PvP outcome; only the fixed offline consumer names
   `objective`, `match` and `collection` are accepted.
2. Serializes participating test writers by a cooperative OS file
   lock and refuses unknown, corrupt, symlink or ambiguous staging
   evidence.
3. Reads a bounded, exact-schema canonical snapshot of the numeric
   test value **and** its per-operation UUID/fingerprint receipt set.
   Duplicate UUID+same payload returns `DUPLICATE` without a
   second value increment; UUID+conflicting payload refuses.
   Capacity exhaustion refuses new operation receipts rather than
   evicting old ones. Overflow refuses rather than wrapping.
4. Writes the updated numeric value and new receipt **in the same
   properties file**, using the existing
   `AtomicAccountPublisher.publish` forced-stage and REQUIRED
   atomic replacement. It never separately publishes an
   increment followed by an independent receipt file.
5. Fails closed on errors. A fault just before publication leaves
   no new receipt or value; a simulated failure **after** successful
   replacement but before its acknowledgment returns
   `INDETERMINATE`, yet inspection proves value+receipt already
   coexist. Repetition **during the same source callback** returns
   `DUPLICATE`. Neither outcome authorizes automatic recovery.
6. A cross-JVM `review` interface is read-only and returns
   `PRESENT_QUARANTINED`, `MISSING_BLOCKED`, or
   `INDETERMINATE_BLOCKED`. ALL statuses explicitly have
   `replayAuthorized=false`,
   `crossConsumerCommitted=false`,
   `rewardAuthorized=false`, and
   `loginAuthorized=false`. The evidence checksum is
   accidental-corruption detection, not authentication.

A forced file plus an atomic rename does **not** prove parent-directory
fsync or storage power-loss durability. The cooperative lock covers
only compliant writers; no multi-file all-or-nothing commit is
established.

## Source-grounded R89 regression

`R89OfflineAtomicConsumerReceiptContractTest` subscribes to the
actual R86 active `LocalPlayerInteractionHandler` semantic
DomainEventBus. It reuses genuine Player81WorldSync Attack, lethal
damage and HP packet 134, and the R86 source-proof listener. The
three **reference** stores then receive test-defined increments of
objective +1, match +2 and collection +7. These values do NOT
represent restored or durable modifications to the real
objective/match/collection services.

Scenarios run under real R86-proven kill/death callbacks:

- Complete: each of three independent reference consumer files has
  one receipt and one value. A duplicate source callback cannot
  double-apply, and asking twice for an ID for the same genuine
  event returns one token.
- Partial fanout: only objective's separate file is changed.
  The absence of match/collection receipts cannot mean the entire
  combat operation completed or should be retried.
- Pre-publish failure: the match file is absent; the first
  objective file remains valid.
- Post-publish / pre-ack failure: the match file **already
  contains both value + receipt**; applying again in the original
  source scope refuses to double-apply, even though the first
  acknowledgment was ambiguous.
- Capacity one: a different genuine death source fact/token cannot
  evict the original kill receipt from the bounded test store.
- Spoof or late replay: a copied equivalent CombatOutcome cannot
  mint a token; after listener exit, the original token cannot
  authorize another consumer mutation.
- Corrupt canonical evidence and adjacent abandoned staging
  evidence return indeterminate and preserve the staged file.
- An **independent Java process** reopens the three files for each
  scenario; it verifies only the observed test values and
  quarantined status, never replays or rewards anything.

Dedicated R89 Linux and Windows workflows run the R89 source
regression and all inherited R63–R88 tests with Java11-compatible
bytecode. The independent full server build and Windows atomic
persistence jobs must also pass on the **exact final head**.

## Blocked production steps and next integration

R89 is NOT a persistent PvP event stream, source-signed ID, real
per-projection commit, distributed transaction or production
catch-up mechanism. It neither changes LocalSession's default no-op
outcome observer nor installs the reference store in real gameplay.
Account files, rewards, player network and the original
ObjectiveProgressService/MatchSessionService/CollectionLogService
implementation remain unchanged.

Before any restart-safe deployment, a real authoritative producer
must mint and durably bind one logical ID to an accepted combat
operation, distinguish multiple legitimate events with identical
payloads, and expose immutable provenance that is verifiable after
restart. Each real consumer would then need a receipt that commits
**atomically with its own durable aggregate**, plus stable
cross-consumer sequencing and an explicitly reviewed recovery
policy for partially completed fanout. There must also be live
acceptance tests, migrations, fault injection and security review
before activation.

**R89 DRAFT / OFFLINE ONLY / UNMERGED / NO LIVE ACTIVATION.**
