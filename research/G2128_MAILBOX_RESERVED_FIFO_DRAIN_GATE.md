# G21.28 — bounded reserved persistence FIFO drain (read-only, no reward grant)

**Certified input:** G21.27 exact head `04e2bd89720f18be42e93da8870d10cb0202b60a`, workflow 37794333448 SUCCESS / 326 focused Java11.

G21.27 introduced a bounded opt-in exclusive, current WorldPlayer/generation and PREPARED-only account-write reservation, blocking both old/new deferred saves, autosaves, other final reservations and strict prepared writes for its account. However, a `repository.save` already in flight when the reservation was admitted may complete afterward; the G21.27 token alone was never a quiescence receipt.

## New G21.28 worker-order observation

`WorldPlayerPersistence.drainReservedPreparedAccount(token, immutableG2125Proposal)` is an asynchronous **off-World** admission to the existing **single bounded persistence I/O FIFO**. The caller obtains a `CompletableFuture<PreparedDrainObservation>` rather than blocking World. It validates the bound token/account/generation, original immutable selected Mailbox envelope and exact live G21.22 PREPARED + G21.25 hypothetical inventory/CLAIMED postimages before enqueueing.

The task runs *after* previously admitted saves and checkpoint drains in that FIFO. Under the same owner-to-io lock ordering, it verifies that the reservation is still current, reads the repository snapshot **on the worker**, verifies the unchanged owner and full pre/postimage again, then classifies one whole normalized disk account snapshot:

- `MISSING_ACCOUNT`: no readable record.
- `EXACT_PREPARED`: complete account matches the immutable G21.22 PREPARED/UNCLAIMED preimage.
- `EXACT_HYPOTHETICAL`: complete account is visibly the detached proposed inventory-credit + Mailbox CLAIMED postimage.
- `DIVERGENT`: anything else, including mutated unrelated account state.

The result's `workerQuiescentAtRead=true` means the **previously admitted FIFO tasks completed before this single read**. It is not permanent quiescence for the whole filesystem; external writers are outside this token. **Every result has `durabilityReceipt=false` and `grantAuthorized=false`.** An exact hypothetical record can result from a failed post-rename directory force and must never become an authorized item payout.

## Reservation pinning and shutdown

One pending drain at a time per reservation. The token is pinned from successful FIFO admission until the read finishes or fails. Concurrent duplicate drain admission and cancellation while a task is pending are refused, preventing newer ordinary writes from entering between drain admission and disk observation. Queued and forcibly interrupted in-flight drains are completed exceptionally, not silently discarded; failures release the drain pin but leave the actual account reservation held.

After *any* drain was attempted, `cancelIfStillUnclaimed` requires that the **latest** completed observation was `EXACT_PREPARED` and that the current exact WorldPlayer/generation/envelope/prepared intent and full snapshot are unchanged. A missing, divergent, hypothetical or failed disk read keeps the account locked; a later exact PREPARED read can allow explicit cancellation while UNCLAIMED, but there is no automatic unlock on logout or process restart. A persisted reservation/restart recovery protocol is still a missing feature before this could be used for live claims.

Normal account writes are unaffected when the opt-in token is not held. Forced shutdown rejects queued and in-flight drain futures. No unbounded worker waits are introduced by this API; callers enforce finite `future.get(timeout)` and can choose retry/reconciliation without releasing a suspicious account.

## Regression and negative authority

`G2128MailboxReservedFifoDrainIntegrationTest` deliberately blocks an earlier ordinary save, acquires a reservation, queues the drain, proves it cannot finish/release early, rejects newer conflicting captures and autosaves, releases I/O, verifies exact PREPARED data, and checks replay/repair of hypothetical/divergent/missing and IOException cases. Also tests stale registration and shutdown against a valid token. It never credits live inventory or changes an actual Mailbox claim state.

**Still not implemented:** full crash-durable reward transaction, atomic live WorldPlayer credit+CLAIMED switch, future checkpoint reconciliation, idempotent restart resume, packet settlement, bank transfer or native widget32181 live claiming. These require further authority. Original SpawnPK server reward semantics are not inferred. Manual native-v308 client gameplay acceptance is separate. Freeze R25 PR #1847.
