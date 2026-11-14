# R55 — rollback server inventory on synchronous trade publication failure

## Failure addressed

R53 rejects a known-dead trade partner before commit. R54 rejects a known-full packet queue before commit. But `TradeService.commit` still changed both authoritative inventories before flushing both packet batches; a new synchronous publication error after those mutations could exit with asymmetric inventories, a still-active trade, and partially published UI packets.

## Narrow implementation

- `BankState.captureTradeInventory` deep-copies the 28 inventory slots (item ID, quantity, tab) under the existing two player mutation locks. `restoreTradeInventory` restores *only inventory*, preserving other bank state.
- `TradeService.commit` captures both inventories immediately before the batch, and on synchronous `IOException` or `RuntimeException` restores both inventory checkpoints, cancels/detaches the trade, invokes **no save callbacks**, and rethrows the original failure so the session can handle it as failed.
- `ServerPacketWriter.abortTradeBatchAfterFailure` discards any unflushed batch bytes and clears batch depth, instead of invoking `endBatch` inside a `finally` after a failed transfer. On live queued outputs, `OutboundPacketQueue.failClosedAfterTradePublication` clears unsent encrypted packets and permanently refuses further offers/drains. This avoids resuming an already uncertain packet/cipher stream.

## Connected Windows verification (2026-11-14)

- Gradle `jar testClasses verifyJava11Bytecode --offline --no-daemon`: **BUILD SUCCESSFUL**, 2,178 classfiles, major 55 (Java 11 compatible).
- `R55TradePublicationRollbackTest`: two separate real `WorldPlayer`/`TradeService` in-memory contexts, reciprocal coin/shark reservations and both acceptance stages. One scenario injects an IOException on the *first* writer's final flush; another on the *second* writer after the first emitted its bytes. Both must restore every exact inventory slot, item, quantity and tab; neither saves, and neither remains in an active trade. **PASS**.
- A queued-writer negative proves that cancelling an unpublished transaction batch clears pending bytes and fences further encrypted packet emission. **PASS**.
- Previous R54 headroom refusal, R53 terminal writer refusal, normal trade flow and trade-generation ownership fencing: all **PASS**.

## Important limitations

R55 repairs **in-memory authoritative server inventories** only for exceptions caught during the final publication block. It is not a cross-account durable transaction or a socket-level atomic commit. If the first participant already received part of an outbound packet, the client cannot be retroactively corrected by this rollback; their session should disconnect/reconnect rather than continue on the uncertain stream. This fixture does not prove a real client's reconnection/UI resynchronization.

An abrupt JVM or OS crash between the two independent account saves, a disk write failure after commit, or a failure during a later asynchronous persistence callback remains outside this fix. A future journalled two-account transaction and crash/restart acceptance would be required for those guarantees.

R55 is **DRAFT, STACKED ON R54, UNMERGED**, pending independent CI.
