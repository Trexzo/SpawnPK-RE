# R53 — two-party trade counterparty outbound-health fence

## Why R50 was not sufficient

R50 prevents a new typed World command on the session **submitting** that command after its own packet-stream failure. Trades are different: the second player's final acceptance commits **both** players' inventory changes. At that point the other participant's outgoing connection can already be terminal, despite the accepting player's connection remaining healthy.

Previously `TradeService.commit` checked both accounts' ownership, available offers and inventory capacity, but not both outgoing writers' known transport health. A healthy partner could therefore reach inventory mutation even when the first participant's queue was already known to have suffered a partial packet write.

## Bounded change

Under the existing ordered player mutation locks, `TradeService.commit` now calls `requireGameActionAdmission()` on both participant writers **before any inventory mutation**. On a previously detected terminal transport failure or queue overflow it cancels the trade and returns `TRADE_COMMIT_REJECTED_OUTBOUND_UNHEALTHY`. Cancellation attempts to close the UI for each participant; a known-failed stream cannot receive that notification, while failure is swallowed by the existing cancellation path.

The change does not touch the accepted happy path or the existing ownership, item availability and inventory-capacity checks.

## Real Windows Java acceptance, 2026-11-14

- JDK21 / Gradle8.10.2 `jar testClasses verifyJava11Bytecode`: **BUILD SUCCESSFUL**, Java 11 classfile major 55, 2,175 classes.
- `R53TradePartnerTransportFenceTest`: two WorldPlayer contexts, real BankState and TradeService reservations, two real OutboundPacketQueues and ServerPacketWriters. After mutual first-stage acceptance and the first player's final accept, a deliberately partial outbound write poisons **only** that participant's queue. The healthy partner's later final click must cancel, not commit.
- Assertions: one byte physically written before simulated IOException, terminal queue state confirmed, healthy participant still active, neither coin/shark inventory changed, no save callbacks, both trade contexts detached, healthy queue not poisoned. **PASS**.
- `EngineR4TradeFlowTest`: normal full offer + two acceptance stages still commits a coin-for-shark exchange. **PASS**.

No proprietary client, production account, live Java owner session, network listener or Windows optional feature is involved in the R53 test. The new CI workflow repeats the bounded acceptance on Linux and Windows.

## Explicit limits

This fence only covers a counterparty transport that is **already known to be failed before commit**. It is not a cross-client distributed transaction. There remains a race if an outbound stream fails *after* preflight, and the existing implementation mutates two inventories before two independent persistence callbacks. Server crash, delayed disk failure, lost account saves or two-host transaction atomicity are **not** solved. Even a green R53 result is not graphical v308 client acceptance.

R53 remains **DRAFT, STACKED ON R52, UNMERGED** pending independent CI.
