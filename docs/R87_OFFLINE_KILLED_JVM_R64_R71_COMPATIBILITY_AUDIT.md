# R87 — real killed-JVM takeover cutpoints and R64/R71 compatibility

**OFFLINE ONLY / DRAFT / STACKED ON R86 PR #3131 / NO LIVE CHANGES**

## Source-based compatibility review

| Boundary | Verified existing offline source | R86/R87 fixture | What remains absent |
|---|---|---|---|
| Owner authorization | `R64OfflineSessionOwnership` binds owner UUID/epoch and ACTIVE/RELEASED state to R63 account generation, snapshot SHA-256 and R59 journal revision, under the R60 cooperative file lock | R86 checks a stream, owner UUID, epoch and per-record revision under a different disposable fixture lock | No account identity, authenticated sessions, snapshot hash, R59 trade journal check, or compatible R60 shared lock |
| Account-save atomicity | R64 `saveOwnedUnderLock` uses R63 coordinated account/ledger publication under R60, then updates the owner record; ambiguous gaps are quarantined | R86 writes its own simulated owner, HP, IDs and three projection checkpoints in **one unrelated file** | No transaction spanning actual attacker/victim HP records, account metadata, reward/match ledgers, or the real server |
| Snapshot freshness | `R71OfflineOrderedPersistenceAdmission` receives a World-thread **capture** ordinal, refuses missing/older ordinals, and updates an **in-memory-only** high-water mark. Its admission also verifies active R64 ownership first | R86 uses a single-record monotonically increasing revision, changing on each modeled commit and ownership transfer | No real World snapshot capture ordinal, no coordination with R71 worker/coalescing/final logout, no durable real save watermark |
| Staged ambiguity | R64 calls `AtomicAccountPublisher.requireNoAmbiguousStage`; R63/R64/R70/R71 tests refuse partially published persistence | R87 adds refusal of any orphan `r86-stage-*.tmp` in the fixture before reading/committing; the staged file is **not** silently deleted | No general-purpose audit/recovery controller, file-tree quarantine registry, production stage parser, or automatic recovery authority |
| Cross-JVM replacement | R64 `replaceOwner` validates previous owner and account token under the R60 lock, then increments its owner epoch. R71 independently checks real deferred-save freshness | R86 performs a single-file owner-epoch CAS under its own OS lock | The two locks and tokens are not interchangeable. R86 cannot grant or revoke a real R64 session or validate incoming client packets |

These are actual source semantics, not proposals to install R86 in
R64 or `LocalSession`. In particular, **R71's ordinal watermark is
not restart-durable**. An R86 file revision does not replace its
World-thread capture-order requirement.

## R87 changes in the disposable V2 model

R87 introduces an **offline-only thread-local takeover cutpoint** to
`R86OfflineOwnerEpochRecord`, used only by a child JVM from
`R87OfflineKilledOwnerTakeoverTest`. The two exact points are:

1. `STAGED_BEFORE_MOVE`: R86 wrote the complete successor state to
   a unique staged file and called `FileChannel.force(true)`,
   but has not yet renamed it over the canonical file.
2. `REPLACED_BEFORE_ACK`: the same-thread `ATOMIC_MOVE` returned
   and the canonical file now contains the successor, but the
   takeover callback has not returned/acknowledged.

The JVM writes a cutpoint marker and remains **inside the real
OS-locked takeover**. The parent confirms it cannot acquire the
lock, forcibly terminates the child with `destroyForcibly()`,
waits for process exit, and then checks the canonical bytes
without a live child. A bounded, **read-only** lock-release observation
allows Windows to finish releasing the exited JVM's OS lock; it does
not retry a takeover, issue a token or perform a consumer write.
These are real independent Java processes, rather than R86's
exception-injection substitute.

### Expected results at each kill point

| Property | Kill before move | Kill after move, before ack |
|---|---|---|
| Canonical owner epoch | Original epoch | Successor epoch |
| Event IDs and existing objective checkpoint | Unchanged | Unchanged |
| Staged `r86-stage-*.tmp` | Orphan remains | Consumed by rename |
| R86 `inspectForTest` / write admission | **Refuse** with `R87_STAGE_QUARANTINED`; evidence preserved | Reads valid successor; old owner token refused |
| Existing owner handle after kill | Cannot proceed while orphan stage exists | Cannot proceed after epoch/owner/revision mismatch |
| Automatic replay, automatic deletion, recovered login | **None** | **None** |

The quarantine rule scans the fixture's staging filename pattern
before any canonical read. It does not rewrite, discard, or
reconcile ambiguous evidence. The parent tests confirm the
original bytes remain parseable in the pre-move case but **do
not treat that as permission to proceed**: the orphan stage
still blocks admission. The stage is destroyed only when the
whole *disposable CI temporary fixture* is removed.

The post-move case validates that the **old owner cannot write**
and that the new canonical record has an advanced epoch and
owner UUID. Merely reading that record does **not** authenticate
a new session, authorize recovery of a successor token, or
dispatch a deferred projection.

## Strict limitations

- Force-killing the **process** is not a full operating-system power
  loss. This test does not prove storage-controller write barriers,
  directory fsync, persistence after sudden power loss, multi-file
  atomicity, or disk-full and permission errors.
- A local attacker with write access can corrupt or replace the
  record. The checksum is for incidental damage detection, and
  the predecessor token remains a nonsecret CAS descriptor.
- `R86OfflineOwnerEpochRecord` is a short single-lethal-event
  model, not a real transaction log. The crash hook is local to
  this synthetic class; actual `PlayerCombatResolutionService`,
  `World`, `LocalSession`, R60/R63/R64/R71, real file paths,
  account saves and gameplay dispatch are **not modified**.
- An interrupted real R64 account/ledger/owner multi-record
  operation cannot be made safe simply by copying this
  single-file R86 protocol. It needs a separate transaction
  design with explicit event provenance, atomically bound
  account and projection mutations, authenticated owner
  replacement, and verified durable recovery handling.

**No production exactly-once guarantees or automatic event
replay are claimed. R87 remains draft, unmerged, offline-only
and not deployed.**
