# G21.44 — recheck strict PREPARED owner at actual persistence-worker execution

**Certified parent:** G21.43 exact commit `c5069092f0a63b59133e6acbbce70a4c24e97c2e`, hosted [workflow #37841447504](https://github.com/Trexzo/SpawnPK-RE/actions/runs/37841447504) SUCCESS, 342 focused Java11. Frozen R25 promotion PR #1847 untouched.

## The genuine post-enqueue gap

G21.43 compares a caller-submitted G21.22 PREPARED snapshot with the **complete current live WorldPlayer** under its mutation lock before admission, and blocks stale proposals without enqueuing them. But `WorldPlayerPersistence` has one bounded FIFO I/O worker. A strict barrier can sit behind an earlier slow ordinary World save; the actual WorldPlayer can then change movement, inventory, mailbox, pet/extension fields or unregister while the strict snapshot is already admitted. Prior to G21.44, the worker's `PreparedStrictBarrierTask.run()` did **not** recheck that the immutable snapshot still matched its original owner/generation before calling the strict file writer. Therefore the G21.42 publication lock could safely coordinate disk writes but still publish an outdated PREPARED account state.

## Narrow production fix

The real `PreparedStrictBarrierTask` now retains the **actual WorldPlayer owner and expected generation** at admission alongside its immutable PlayerSnapshot. Immediately after being dequeued by the **existing** persistence worker and before invoking any strict writer I/O, for a **concrete FilePlayerRepository** it calls `requirePreparedOwnerStillCurrentBeforeWrite(owner,generation,snapshot)`.

Under the existing owner mutation lock, without performing filesystem I/O, the worker:
- Requires the player remains registered at precisely the admitted generation and same canonical account. A retired/replaced owner throws `G21.44 STRICT_PREPARED_WORKER_OWNER_RETIRED`.
- Captures an exact complete live snapshot using `PlayerSnapshotCodec.capture(account,owner,PlayerSnapshotCodec.accessoryItem(snapshot))`, preserving the caller's normalized pet accessory value.
- Recomputes G21.30 complete SHA-256 and requires byte-equivalent canonical values through digest equality with the immutable PREPARED input. Any difference throws `G21.44 STRICT_PREPARED_WORKER_SNAPSHOT_DIVERGED`.
- Releases the live mutation lock **before** G21.42's strict writer serializes or forces the account file; a veto settles the existing future exceptionally before the writer is entered, without writing any account temp file.
- Retains G21.43's separate **admission-time** validation and G21.42's **last-minute persistent review-fence and file-publication lock**, all independent gates.

No World tick callback performs disk I/O; no private worker/scheduler is introduced. Historical strict tasks for custom/non-file `PlayerRepository` adapters retain previous semantics rather than asserting unsupported file-backed snapshot authority. No positive reward grant or review-fence release is implemented.

## Deterministic single-FIFO acceptance

`G2144MailboxStrictWorkerOwnerRecheckIntegrationTest` uses the actual file-backed World persistence worker. A test-only FilePlayerRepository before-replace hook holds an **earlier ordinary World save**, so a valid strict PREPARED task can queue behind it.

1. **Queued stale owner:** G21.43 initially admits the exact PREPARED snapshot. The World owner then changes movement while the worker is stalled. After release, the strict future must fail with the G21.44 stale reason; strict writer's temp-create fault hook must **not** be reached, and previously saved file bytes remain unchanged. A fresh still-PREPARED snapshot then saves successfully through the strict path.
2. **Retired generation:** A second earlier ordinary save blocks the same worker. A strict snapshot is admitted while owner is registered, then `world.unregisterPlayer` retires its generation. After release, task fails before file I/O, with original account bytes preserved.
3. **Stable queued owner:** A third earlier save blocks the worker. A still-matching strict PREPARED snapshot remains queued; after release, it returns a matching strict receipt and saves the unchanged account. Other saves continue, no orphan temp files/JVM leases occur, and all original Mailbox rows remain UNCLAIMED with no inventory credit.

Java11 focused manifest **342→343**, new Gradle `g2144MailboxStrictWorkerOwnerRecheckRegression`; full exact-head hosted CI SUCCESS required before certification.

## Explicitly unresolved

The recheck occurs **at the beginning of worker execution**, before file serialization. It does *not* prevent a concurrent World mutation **after** that check while the strict file I/O is running. Achieving an atomic in-memory game-state epoch plus durable file commit without holding World locks across disk writes will require an explicit owner quiescence/commit protocol, not an optimistic claim based on this test. Likewise, hardware power-loss guarantees, positive inventory+Mailbox exactly-once settlement, recovery decisions, replay, rollback and native widget32181 grant remain excluded. Frozen R25 #1847 remains untouched.
