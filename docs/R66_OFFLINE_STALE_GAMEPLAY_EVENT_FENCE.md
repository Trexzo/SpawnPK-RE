# R66 — Offline stale gameplay-event and in-memory actor fence

## Why R66 exists

R65 rejects delayed asynchronous account SAVE and final-logout callbacks if an old session's persisted R64 owner token no longer matches the new owner. That protects the **offline persistent account files**, but a delayed gameplay action could modify a `WorldPlayer`'s in-memory inventory before any save callback runs.

R66 adds a **test-only simulation of the gameplay dispatch boundary**, using real disposable `WorldPlayer` instances and their bank API but **no actual player input, game sockets, LocalSession hooks or real account folders**.

## Implementation

- New read-only `R64OfflineSessionOwnership.requireActiveOwnerUnderLock` enforces, while **this Java thread holds R60's real OS FileLock**, that the captured R64 owner UUID/epoch/ACTIVE phase matches persisted session ownership and that R63's canonical snapshot hash, generation and R59 journal revision are still valid. Any unresolved indexed pair denies the event.
- `R66OfflineGameEventDispatch.bindCurrent` validates the recorded owner before binding a specific test-owned `WorldPlayer` object and packet writer to an in-memory actor-incarnation UUID. Rebinding the account supersedes the old actor.
- `capture(boundActor, testItemId)` creates an in-memory queued synthetic event. Capture does not read filesystem state or authorize future mutation.
- `dispatch(event)` acquires the **same R60 cross-JVM lock used for registered trade intents, R63 saves, R64 takeover and R65 callback dispatch**. It validates persisted ownership at **execution**, checks that the captured actor incarnation remains the current dispatcher binding, and only then calls the **test-owned** `WorldPlayer.bank().spawnItem(itemId,1,testWriter)`. It verifies the synthetic item count changed by exactly one. A stale owner, account generation, actor incarnation, released session, unknown ledger/stage or PREPARED trade throws **before in-memory mutation**.
- The dispatcher keeps an **in-memory** set of event UUIDs per actor and refuses duplicate delivery in the same dispatcher lifetime. This is an at-most-once *test instance* guard, not a durable event queue and not a server-side idempotency guarantee after a crash.
- A narrow fault-injection hook holds the OS lock after owner validation but before the in-memory mutation. A distinct JVM attempts a real R64 takeover at that exact point and must receive the OS lock-busy refusal. Thus a cooperative takeover cannot slip between validation and mutation.

## Connected Windows acceptance — November 15, 2026

- JDK21 Gradle `jar testClasses verifyJava11Bytecode --offline --no-daemon`: **BUILD SUCCESSFUL**, 2,250 major-55 Java11-compatible class files.
- `R66_OFFLINE_GAMEPLAY_EVENT_FENCE_PASS`: two real executor threads queue separate synthetic gameplay events while session A is current, and wait at deterministic latches. A **separate child JVM** explicitly publishes session B. Both A events then execute, one by one, and independently refuse with `R64_SESSION_DENIED stale owner`; **neither** session's test-owned in-memory bank changes. The canonical account, R63 generation and R64 owner files remain byte-for-byte unchanged by the old events.
- Session B successfully dispatches one synthetic bank event, but replay of the same callback UUID does not apply twice.
- An R64 account save advances R63 generation; already-captured events with the prior generation refuse despite a matching owner UUID and epoch.
- Explicit actor rebinding denies queued events from the earlier `WorldPlayer` object, even when both use the same current owner generation.
- A released/logged-out session's pending in-memory event refuses.
- During a legitimate event's **post-authorization/pre-mutation lock hold**, a distinct JVM's real owner takeover attempt is rejected with the R60 lock-busy refusal; after the hook releases, the authorized test event applies.
- A R57/R59 PREPARED trade registered after event capture denies that queued event and preserves in-memory inventory.
- Inherited R65 deferred persistence, R64 session ownership, R63 generation ledger, R62 completion, R61 paired replay, R60 OS lock and R17 crashed-owner regression tests all **PASS** locally. GitHub's cross-platform CI includes R55–R66, R17 and ordinary `EngineR4TradeFlowTest`.

## Explicit limits

1. **No live gameplay integration.** R66 is a package-private test-only actor dispatcher. No `LocalSession`, protocol packet router, live `WorldPlayer` actor, WorldPlayerPersistence scheduler, TradeService or login endpoint uses this gate. No real in-game permission or authenticated user identity is established by the test token.
2. **Synthetic mutation only.** The test uses a test-owned WorldPlayer bank and a local `OutboundPacketQueue`, not a real gameplay packet. The test must not be interpreted as proving security for actual attack, movement, inventory exchange or plugin event handlers.
3. **No multi-file or process-crash transaction durability.** The mutation is **in memory**, not durably committed. A crash can lose an accepted unsaved event, and this in-memory event-ID set disappears on restart. Exactly-once event delivery across a JVM crash is NOT proved.
4. **Cooperative components only.** Real code or any direct/unfenced API may mutate in-memory player state without taking R60's lock. R66 prevents takeovers interleaving with mutations only for code that voluntarily uses this dispatcher and the shared coordinator.
5. **No safe quarantine release.** The R57/R59 pending transaction directory and index remain intact; even the R62 offline completion certificate never grants actual login or automatic account recovery.
6. **Not an authentication scheme or security boundary.** The R64 owner UUID and generation bind controlled test actions. A production server must associate these with authenticated sessions, the correct world actor and every permitted game mutation, and protect against unsafe client replay/authorization bypass.

## Next R67 priority

Develop an **offline LocalLab packet-dispatch integration harness** that routes a simulated decoded player command through a test-only session generation/actor gate *before* calling its handler. Inject a queued attack/inventory/movement action from a superseded session, compare the complete pre/post in-memory actor state, and test disconnect, concurrent fresh login and pending-trade quarantine. First audit all real inbound mutation entrypoints and schedule cross-thread callbacks; do NOT activate live hooks until a fail-closed integration design and independent local/CI verification exist.

**R66 is DRAFT, STACKED ON R65, UNMERGED.**
