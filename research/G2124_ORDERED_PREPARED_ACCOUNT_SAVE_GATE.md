# G21.24 — ordered, opt-in PREPARED account snapshot barrier

## Certified predecessor and intent

Based on G21.23 exact hosted-green head `c90dccff5d4568242daf14c3918eb28a2ba20997` / 322 focused Java11 tests. G21.23 added an opt-in strict file writer (file force, required atomic replace, directory force). The existing `WorldPlayerPersistence` sends ordinary saves, checkpoints and final disconnect through one bounded FIFO worker, but G21.23's writer was outside that queue.

This milestone introduces a **non-mutating** strict PREPARED-only save barrier on the existing persistence worker. It does **not** grant rewards or mark a Mailbox message CLAIMED.

## Captured-write ordering

`WorldPlayerPersistence.submitPreparedStrictBarrier(owner, generation, immutableSnapshot, writer)` is an off-World opt-in admission method. It requires a verified registered player/generation/account and a snapshot carrying exactly `PREPARED_NO_GRANT`. It enqueues the strict snapshot writer onto the existing single I/O worker. Previously admitted save tasks and in-flight checkpoints execute before the queued barrier; the strict writer never runs on the World execution context.

At admission, after the task is successfully queued, it establishes an **account-local capture-sequence cutoff** under the same persistence admission lock. A delayed old deferred save that was *captured before* the barrier but submitted *after it* must be rejected rather than restoring the account to pre-barrier state.

Critical subtlety: coalesced checkpoints previously took their sequence only when drained on the worker. G21.24 moves that sequence to the point of World-owned snapshot capture. Without this change, a delayed checkpoint drain could appear newer than a strict barrier even if it contained old state.

On the FIFO worker, every ordinary write checks the capture-sequence cutoff before calling `repository.save`; stale snapshots fail exceptionally, not as successful saves. Checkpoints **captured after** the strict admission remain permitted; the caller must keep the live player state consistent with its saved snapshot. A successful strict write is not immunity against later legitimate player saves.

If the worker cannot admit the strict task due to shutdown/backpressure/final-save reservation, no cutoff is published. An uncertain strict write may leave the cutoff active conservatively and must be reconciled on disk before any reward settlement. Forced shutdown does not turn an in-flight unknown outcome into a success receipt.

## Specific negative guarantees and remaining vetoes

The staged `MailboxPreparedClaimJournal` is inert and never replays as a grant. This boundary is not automatically invoked by native widget32181; it does not yet serialize in-memory inventory credit and Mailbox CLAIMED with strict persistence. A synthetic file write can return after Java/NIO force calls without proving faulty hardware or loss of device caches impossible. A player mutation *after* the barrier may legitimately create a newer ordinary checkpoint, so claim mutation/restart recovery needs its own authoritative matching postimage and idempotency gate.

No original SpawnPK item economics, Mailbox banking, client packet ordering or reward settlement claims are inferred. Frozen R25 promotion PR #1847 remains untouched.

## Acceptance

`G2124OrderedPreparedAccountSaveIntegrationTest` drives an in-flight blocked checkpoint, a previously captured deferred ordinary save, a strict PREPARED snapshot on the **same** persistence worker, then release. It requires the delayed old capture to fail instead of overwriting the strict result, confirms a new post-barrier checkpoint writes normally, verifies the PREPARED+UNCLAIMED state survives file reload without inventory credit, and tests failed strict file writes, old registration-generation rejection and shutdown admission. Hosted Java11 test success is source/persistence verification, not manual native v308 gameplay acceptance.
