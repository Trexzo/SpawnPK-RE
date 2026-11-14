# R54 — reject an already-near-full outbound trade queue

## Gap after R53

R53 blocks a two-player trade commit when either participant's outbound socket is **already** known to have failed or its packet queue is already overflowed. A different unsafe condition remained: a queue can be healthy and below its limit but have insufficient room to publish the final trade's multi-packet inventory/close notifications. The old trade path could remove/add both players' items and then throw when `endBatch()` finally offered bytes to an almost-full `OutboundPacketQueue`.

## Conservative pre-commit guard

Under the existing ordered player mutation locks, `TradeService.commit` now calls `requireTradeCommitHeadroom(64*1024)` on **both** session writers, after R53's failure check and before any inventory modification. Queue-backed writers check the current queued byte count, terminal write state and overflow state; direct in-memory test writers are unchanged. If either queue has under 64 KiB free, the trade is cancelled before item mutation and responds with `TRADE_COMMIT_REJECTED_OUTBOUND_HEADROOM`.

64 KiB is a conservative capacity threshold for this bounded 28-slot trade path; it is not a formal whole-transaction byte budget. The check is only a point-in-time gate and does **not** reserve queue capacity or lock out unrelated concurrent packet producers. R54 deliberately does not claim rollback, all-or-nothing client delivery or two-account crash atomicity.

## Connected Windows verification, 2026-11-14

- Detached R54 worktree built with JDK21 / Gradle8.10.2: **BUILD SUCCESSFUL**, `LOCALLAB_BYTECODE_MAJOR_PASS major=55 classes=2176`.
- New `R54TradeOutboundHeadroomFenceTest`: stages reciprocal coin/shark offers, confirms first-stage and first player's final accept, then fills a **healthy** `OutboundPacketQueue(1<<20)` until exactly **1,024 bytes remain**. The other player's final acceptance must reject, leave both inventories unchanged, invoke no save callback, detach both trade contexts, and not mark either queue terminal. **PASS**.
- Existing R53 partial-write known-terminal counterparty test: **PASS**.
- Existing normal successful two-stage trade commit test: **PASS**.
- All tests used isolated `World` instances with in-memory queues; no actual network listener, original v308 GUI, account credentials or live player data used.

## Next unresolved boundary

A socket write or queue overflow occurring **after** these checks can still fail midway through the two-player commit. The server uses independent snapshots for the two players and cannot yet prove atomic recovery across an abrupt crash. Any future stronger guarantee requires a deliberately designed transaction/commit protocol and independent power-interruption testing; it cannot be inferred from R53/R54.

R54 remains **DRAFT, STACKED ON R53, UNMERGED** pending cross-platform CI.
