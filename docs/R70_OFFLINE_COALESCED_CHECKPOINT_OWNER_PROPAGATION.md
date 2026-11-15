# R70 — Offline checkpoint capture-owner propagation through real coalescing

## Problem

R69 verified that manually captured `WorldPlayerPersistence` saves can be denied at the **actual I/O worker commit** after an independent JVM transfers account ownership. Its test-only gate deliberately refused **all checkpoints**, because the original checkpoint queue stores only a `PlayerSnapshot`, accessory item and tick, with no authenticated session-owner token. Reattaching whatever session happens to own the account when the worker finally runs would be incorrect: it would silently turn an **old session's checkpoint bytes into a newer session's authorized save**.

R70 supplies a **test-only, explicitly bound checkpoint capture/commit path** to exercise this exact owner-propagation and coalescing race. No production `World`, `LocalSession`, game socket or actual account is connected to it.

## Changes

### Checkpoint data provenance

`WorldPlayerPersistence.PendingCheckpoint` now carries a *fourth* immutable field: `checkpointOwner` of type `R64OfflineSessionOwnership.Token`. The original `tick`, `snapshot` and `petAccessoryItem` are unchanged.

When the opt-in `OfflineCheckpointOwnerCapture` is installed, `checkpointDue` captures the owner **on the World execution thread, under the exact WorldPlayer's mutationLock**, adjacent to its snapshot. The authority is selected only from an *explicitly registered test actor identity + World actor generation*; it is never guessed by username, inferred from the current persisted R64 record or silently replaced by the worker.

Checkpoint coalescing continues to assign the complete `PendingCheckpoint` object to `slot.latest`, so a newer snapshot automatically replaces **its own owner token together with the payload**. The older object's frozen token remains attached to the older object and is dropped with that old object. This avoids pairing a new session's owner token with old session snapshot bytes.

The regular, hook-free World checkpoint capture still runs with a null test token and its original `repository.save` behavior; no real account persistence is enabled or changed.

### Actual I/O commit

The real `drainCheckpoints` passes the **captured checkpoint token from the selected pending object** to the existing persistence worker's `write`. The original `write` overload remains intact for manual saves and calls the new internal overload with null checkpoint authority.

When no test admission hook is installed, `write` still invokes `repository.save(snapshot)` exactly as before, preserving the unmodified live persistence path.

The R69 `OfflineIoWriteAdmission` remains a functional interface; its new default `persistCheckpointAtCommit` routes into the old `persistAtCommit(..., checkpoint=true)`, ensuring that R69's conservative unbound checkpoint denial and original tests remain unchanged.

R70's `R70OfflineCheckpointAuthority` explicitly implements the capture and I/O hooks. In a test it binds an existing WorldPlayer **instance**, its current World actor generation and a specific R64 owner token. The capture lookup uses an identity map and refuses missing/mismatched generation and account identities; it does **not** take R60's OS lock while holding the World mutationLock. The test-only worker hook verifies the frozen token again at commit under the real R60 cross-JVM OS FileLock and invokes the R64/R63/R59 canonical save, account-generation and owner-record checks **before calling the actual PlayerRepository.save**. A stale/released token, unresolved trade, hash mismatch, missing token or competing OS lock fails closed.

Manual test-saves may still be delegated to R69's existing one-shot ticket/owner gate. R70 checkpoint snapshots are **not converted to R69 manual tickets**, and no authority is borrowed from a current manual-save queue.

## Independent acceptance criteria

The new `R70OfflineCheckpointAuthorityTest` uses an isolated World, the **real** `WorldPlayerPersistence` single-threaded executor and a counting test-owned `FilePlayerRepository` wrapper.

1. Under owner A, capture a changed snapshot via the **actual `checkpointDue(100)` World-tick path** and pause the worker before commit. A distinct Java process takes R64 owner epoch 2. The released A checkpoint must fail **before any repository call**, and canonical account, generation ledger and owner bytes must remain unchanged.
2. To force coalescing deterministically, pause a real repository **load** on the same single-thread executor. While its I/O worker is blocked, capture B's snapshot at tick 200. Transfer R64 ownership again to C in a separate JVM, explicitly rehydrate the test actor from canonical and rebind its token. Capture C's different snapshot at tick 300 into **the same checkpoint slot**. Its `latest` field must hold C's snapshot **and C's frozen owner token**, not B's snapshot with C's authority.
3. Release the load. Exactly one checkpoint should be written for C; the canonical snapshot must contain C's expected item and **neither A nor B's test-only item**, with the matching advanced R63 generation and R64 owner record.
4. A later checkpoint captured with the now-stale prior generation must fail before repository save. An explicit rebind to the current token must be required.
5. A test-owned repository failure injected before canonical publication must leave all durable evidence unchanged; a separately captured, authorized retry may then succeed.
6. An unbound checkpoint must be rejected without looking up or guessing the latest owner UUID/epoch.
7. Inherited R69, R68–R55, real dispatcher, regular persistence shutdown, uninterruptible shutdown and checkpoint-coalescing regressions must continue to pass on independent Linux and Windows CI.

## Explicitly not solved

- **No live login/session authentication.** The owner token is a test supplied value, not a credential, and the binding can be reassigned only by explicit fixture code. A real authenticated actor-to-owner issuance and revocation lifecycle is required before any deployment.
- **No automatic hook installation.** `LocalSession` and ordinary World construction never install R70. Regular live autosaves still use the existing writer and do not consult this test-only OS lock.
- **No full multi-file atomicity or physical power-loss proof.** Canonical account, generation ledger and owner record remain separate individually atomic replacements, with fail-closed disagreement after an interrupted write. Parent-directory fsync, catastrophic storage faults and safe account recovery are not proven.
- **No automatic trade intent retirement or login release.** R57/R59 quarantine remains when a prepared pair exists; offline R61/R62 replay and certificate do not authorize real account login.
- **No durable checkpoint event log.** A coalesced in-memory checkpoint is not a write-ahead transaction, and the test binding map is not persistent. Process crash may lose checkpoints; duplicate restart semantics are not claimed.
- **No all-path guarantee.** Other direct repository writers, live callbacks, game ticks, pet timers and uncooperative processes can bypass the test-only fence.

## Next milestone

R71 should audit and simulate **checkpoint suppression, final-save collision, queued loads, close/shutdown and backpressure** with an explicit test-authenticated session binding. In particular, a delayed checkpoint must never commit after a final logout or after a new session assumes ownership, and a failed token must not be reused across queued tasks. Only after comprehensive production-session identity and trade persistence integration should enabling any live hook be considered.

**R70 DRAFT, stacked on R69, UNMERGED.** Independent CI required before full verification.
