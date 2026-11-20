# R91 — World-thread capture provenance bound to R90 producer incarnation

**OFFLINE TEST-ONLY / DRAFT / STACKED ON R90 PR #3135 / NOT DEPLOYED**

## Actual source path and what changed

`WorldPlayerPersistence.captureDeferredSave` and
`captureDeferredFinalSave` capture a `PlayerSnapshot` under the
`WorldPlayer.mutationLock()`, on the authoritative World execution
thread, and assign a `captureOrdinal` before the bounded IO worker
gets involved. World-tick `checkpointDue` similarly captures and
coalesces immutable snapshot state before queuing its persistence
task.

R90 previously protected the **installed R89 gate** via a durable
producer-incarnation record, but its `withOrderedAdmission` input
did not carry the World actor's producer identity. R91 adds a
strictly offline-only immutable `OfflineCaptureStamp` at the
**moment of World-thread snapshot capture**. Its fields are
username, R90 stream UUID, R90 incarnation UUID, R64 owner epoch,
R90 producer generation, capture ordinal, snapshot digest and
World-player generation.

The stamp follows the immutable captured payload through:

- `CapturedSave.ticket.provenance` and `SaveTask` for ordinary
  deferred saves and final/logout saves.
- `PendingCheckpoint.provenance` for tick autosaves, surviving
  coalescing and IO queue delay.
- `WorldPlayerPersistence.write` for test-only
  `OfflineStampedSaveAdmission`, selected only when the fixture
  explicitly installs `R91OfflineWorldCaptureGate`.

All existing live saves and inherited R63–R90 tests retain their
previous gate path: ordinary `OfflineOrderedSaveAdmission`
without a R91 issuer remains valid for existing tests, while the
R91 wrapper denies unstamped calls.

## Same-lock producer and snapshot validation

`R91OfflineWorldCaptureGate.installForTest`:

1. Requires a test-only R90 session that was explicitly initialized
   or restarted under R60/R63/R64/R89.
2. Uses the existing R72 `seedOfflineCaptureOrderForTest` before
   World start to resume from the committed ordinal.
3. Installs the offline-only World-thread stamp issuer and the
   R91 stamped IO-admission wrapper; none is installed by live
   `World`, `LocalSession` or network dispatch.
4. Enables strict R89 stamp requirements on this fixture alone.

At capture, `WorldPlayerPersistence` checks that stamp ordinal,
username, player-generation and digest exactly match the
newly produced immutable snapshot, **before queue admission**.

At save, the R89 writer still acquires R60's real cross-process
OS lock, checks R64/R63/R59 ownership and R89 highwater, then calls
the R90 producer fence. R90 checks stamp stream, incarnation,
owner epoch, producer generation, ordinal and snapshot digest
against its current producer ticket **inside that same lock**,
before publishing R90 PREPARED, R89 PREPARED, account/ledger/owner,
R89 COMMITTED and R90 COMMITTED.

Mismatched or absent stamps refuse admission without modifying any
canonical files. A source that was valid at capture but superseded
before IO delivery is refused under the same lock even if the
underlying R64 account owner has not changed.

## R91 executable regression coverage

`R91OfflineWorldCaptureProvenanceTest` verifies:

- Real isolated World captures and saves a stamped deferred
  snapshot; the stamp is visible on the exact `SaveTicket`.
- A tick-100 autosave checkpoint carries the stamped snapshot
  through `PendingCheckpoint`, the queue and the IO worker,
  advancing the real R89/R90 watermark without bypassing the
  account repository.
- A stamped deferred **final** save advances the ordinal and
  survives reopening the durable producer record.
- A held snapshot captured by an old World incarnation is
  refused after a second, explicitly restarted R90 producer
  takes ownership of the same stream, even while the R64 owner
  UUID and epoch remain unchanged. All account, ledger, R64
  owner, R89 watermark and R90 producer bytes are compared
  after refusal.
- Missing stamps, unstamped direct admissions, wrong stream,
  wrong incarnation, wrong capture ordinal, wrong snapshot
  SHA-256, and an issuer that returns a bad digest **on the World
  thread** all fail before account mutation.
- The new World is seeded with the persisted ordinal and can
  commit a valid stamped capture after old-World refusal.
- All R90/R89/R88/R87 and earlier offline safety regressions
  remain part of the dedicated Linux/Windows CI, plus full
  Linux server and Windows atomic-persistence workflows.

## Explicit limitations

This is **not cryptographic producer authentication**. In-process
Java objects and package-visible test seams can be forged or
reconstructed by adversarial code with sufficient access. Stamps
are neither signed nor bound to authenticated client connections.
A malicious writer with direct access to the filesystem can
bypass the optional R91/R90/R89 coordination.

R91 also does not turn R90/R89/R64/R63 multi-file publication
into a single durable transaction. It retains their deliberate
PREPARED quarantine, with no automatic recovery, no disk-power-loss
fsync proof, no login authorization, no exactly-once PvP/reward
claims and no real account migration or production integration.

**PR remains DRAFT, UNMERGED, and NOT DEPLOYED.**
