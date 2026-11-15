# R72 — Terminal save capture and durable ordinal evidence (offline only)

## Status

**DRAFT, stacked on R71, unmerged.** R72 adds two **explicitly installed
test-only** extensions to the real `WorldPlayerPersistence` flow:

1. An opt-in per-World-actor *final snapshot capture fence*.
2. A separate R60-locked durable capture watermark, bound to the R64 session
   owner and R63 canonical account generation/hash.

No `World` or `LocalSession` production constructor installs either.
With the opt-in flags absent, the ordinary save, checkpoint and final-save
routes retain their old execution semantics. R69/R70/R71 offline admission
hooks remain unchanged.

## Observed source problem

R71's ordinal was emitted on the World thread and survived deferral and
checkpoint coalescing, but its high-water record was only in memory.
Consequently a new process could start its capture counter from zero and
would lack an independently verified freshness baseline.

`captureDeferredFinalSave` suppressed periodic checkpoints, but it did
not block an explicit `captureAndSave` from the still-registered actor
after final capture, nor late submission of a separately captured ordinary
deferred snapshot. That allowed a possible terminal-save inversion.

## R72 test-only terminal-capture fence

`installOfflineTerminalCaptureFenceForTest` must be called before the
World pulse starts or any capture-order ordinal is issued. The final-save
capture now sets a terminal marker for the exact WorldPlayer EntityId and
World actor generation, while holding the player's mutation lock.

- A duplicate final snapshot or later ordinary/deferred capture by that
  World actor is denied before assigning a new ordinal.
- `submitCapturedWithBackpressure` refuses late submission of
  pre-final **non-final** CapturedSave objects, completing their futures
  exceptionally rather than leaving an unresolved future.
- The intended final CapturedSave still goes through the real bounded
  executor and coordinated IO writer.
- Post-final checkpoints stay suppressed by the pre-existing rule.
- World unregister/close cleans up the test-only in-memory marker.

This is **not** a gameplay mutation terminal barrier: arbitrary gameplay
state can still be changed by other World actions, and queued saves already
in the IO executor can complete before the final snapshot. The R71 ordered
commit hook is still required to refuse old snapshots that arrive after
a newer commit. Forced-shutdown and final-save-loss guarantees remain open.

## R72 durable marker (test-owned separate root)

`R72OfflineDurableCaptureOrder` uses an additional distinct disposable
directory under the existing R60 cooperative cross-JVM lock. The strict
properties marker contains:

- exact account name;
- owner UUID and epoch (offline, **not authenticated**);
- last accepted World-thread capture ordinal;
- R63 generation and R59 journal revision;
- the canonical account SHA-256 digest.

Initialization is explicit, permitted only for a fresh R64 epoch-1,
R63 generation-1 account; existing markers or ambiguous adjacent
`AtomicAccountPublisher` stage files are never replaced or cleaned
implicitly. Reads require strict schema, regular-file/no-follow checks
and exact equality with active R64 / R63 persisted records.

Every IO commit under R60:

1. Revalidates R64 active owner and R63/R59 consistency.
2. Re-reads and checks the marker against the *currently validated*
   R64 token.
3. Refuses an ordinal at or below the marker high-water threshold.
4. For a newer identical snapshot, advances only the marker.
5. For a newer changed snapshot, calls the existing coordinated
   `R64.saveOwnedUnderLock`, then publishes the marker through R16
   atomic file replacement before reporting successful completion.
6. Updates the in-memory owner token only after marker publication.

The supplied unversioned raw PlayerRepository writer must never be
invoked by this test-only policy. The marker cannot silently adopt a
different R64 owner, generation or account hash, and does not grant a
real authenticated login or authorize a live account save.

**Multi-file power-loss atomicity is not established.** If the account,
R63 ledger and R64 record are committed but the marker has not yet been
published, a future resume finds a mismatch and refuses. There is no
automatic repair, promotion or marker resynchronization. A crash during
an identical-snapshot marker write can leave the previous ordinal; the
R16 staging preflight is fail-closed on surviving ambiguous stages, but
physical storage durability remains unproven.

## Real-world-thread / real-IO acceptance tests

`R72OfflineTerminalAndDurableOrderTest` uses temporary files, three
isolated fixtures, actual `WorldPlayerPersistence` IO workers and the
existing R64 separate-JVM worker:

- Capture a deferred ordinary save at ordinal 1, then commit a changed
  snapshot at ordinal 2. Commit an identical snapshot at ordinal 3:
  no R63 increment, **durable ordinal 3**.
- Capture final save at ordinal 4: later ordinary capture, duplicate
  final capture, and late admission of the already captured non-final
  snapshot must refuse. The final task itself commits through the real
  worker; tick-100 autosave remains suppressed. Account, ledger, owner
  and marker bytes remain unchanged on refusal.
- Close World 1. Reattach to the unchanged R64 owner using the persisted
  marker, seed a brand-new WorldPlayerPersistence ordinal before starting
  World 2, and save a new snapshot with ordinal 5. A pre-restart ordinal
  replay is refused without changing any of the four records.
- Inject an interruption after coordinated R64 save but before marker
  publication. The real IO future must fail; a new resume refuses the
  valid-but-newer R64 account with mismatched stale marker. No implicit
  recovery modifies the evidence.
- A separate JVM replaces the R64 owner. Neither the old owner nor the
  new owner is allowed to silently rebind the existing marker; owner
  transfer needs a distinct, explicit authenticated migration design.

The Linux/Windows workflow also runs inherited R63–R71 admission and
persistence regressions, plus Java 11 classfile verification and the
repository-wide build checks.

## Deliberate remaining gaps — R73

- Protect the **entire** final logout lifecycle (quiesce gameplay actions,
  drain relevant IO, then atomically release ownership after verified final
  commit), not only the test-only capture and submission routes.
- Ensure old queued checkpoints, direct saves and network handlers cannot
  mutate after session release. R68's dispatcher hook is test-only, not a
  live authentication protocol.
- Make owner transfer and durable ordinal reseeding a trusted protocol
  across process restarts, without manufacturing permission from an
  unverified UUID.
- Audit full bounded-queue backpressure, forced shutdown,
  interrupted/uncertain commit outcomes, and filesystem durability.
- Resolve genuine incomplete multi-file commits through explicit,
  independently validated recovery; never infer authorizations from
  surviving files alone.

**OFFLINE ONLY. DRAFT. NO LIVE SESSION OR ACCOUNT MODIFICATION.**
