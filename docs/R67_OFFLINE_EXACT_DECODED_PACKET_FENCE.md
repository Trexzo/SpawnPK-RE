# R67 — Offline exact-decoded packet dispatch and stale-session handler fence

## Intent

R66 prevented stale synthetic in-memory WorldPlayer events from running after account takeover. The next question is whether actual **decoded client request objects** reach gameplay handlers from an old session after a reconnect. R67 joins three previously independently verified components:

1. `ClientPacketProbe` — the existing exact-current pinned v308 client ISAAC opcode framing and byte-transform decoder.
2. `R64OfflineSessionOwnership` / `R63AccountSaveAdmission` — persistent owner epoch, snapshot generation/hash and journal revision.
3. `R66OfflineGameEventDispatch` / `R60TradePairCoordinator` — actor-incarnation and real OS FileLock, held across owner validation and handler invocation.

**This is a test-only decoder-to-instrumented-handler harness, not a production LocalSession packet router or authenticated game packet processing path.** No game sockets, real account files, live combat/movement engine or TradeService are invoked.

## Three exact-current wire families

The test constructs a small synthetic, fully framed byte stream encoded with the same ISAAC cipher and pinned-client packet writer transforms used by the pre-existing regression fixtures. `ClientPacketProbe.readNextKnownPacket()` decodes these wire bytes and emits the actual typed `ClientRequest` objects in FIFO order:

- **Movement opcode 164**: variable-length walking payload `startX LE_A`, `startY LE`, `run negated`; target (3088,3495).
- **NPC attack opcode 72**: fixed-2 `BE short-A` NPC scene index 131.
- **Inventory menu action opcode 145**: three `BE short-A` fields for widget 3322, slot 0 and item ID 995.

`R67OfflineDecodedPacketDispatch.capture` accepts only these request classes with their exact previously documented `ClientRequestMetadata` opcode/schema/source/provenance; it **freezes primitive field values at capture time**. Unknown opcodes, unexpected typed request classes and mismatched schemas are refused. A regression deliberately alters the decoded `MovementRequest` backing arrays after capture and proves this does not change the deferred handler command.

## Handler boundary

`R67OfflineDecodedPacketDispatch.dispatch` passes a frozen request to a new generic **test-only** `R66OfflineGameEventDispatch.runVerifiedHandler` bridge. That bridge holds the existing R60 exclusive cross-JVM OS FileLock across:

- Current R64 persisted owner UUID and monotonic epoch check, ACTIVE phase, snapshot generation, hash and R59 journal revision;
- The actor-incarnation check against the current dispatcher-attached WorldPlayer;
- A bounded in-memory duplicate request-ID guard;
- Execution of the instrumented handler spy.

The handler spies are explicitly **not real movement or NPC-attack implementations**. They record movement targets, attacked scene indices and item action fields; for the inventory family they apply a small synthetic item change only to a disposable WorldPlayer bank to verify a post-handler effect.

An old request must fail before these spies run. A duplicate event ID is rejected in-process. The request-ID guard is not durable across a restart.

## Connected Windows verification — November 15, 2026

- Isolated JDK21 `jar testClasses verifyJava11Bytecode --offline --no-daemon`: **BUILD SUCCESSFUL**; 2,259 Java 11 major-55 class files.
- `R67_EXACT_DECODED_PACKET_DISPATCH_FENCE_PASS` confirms ISAAC opcode recovery, exact transforms and metadata, the three typed requests in FIFO order, and unknown opcode 255 pausing the probe fail closed.
- Three separate executor threads queue the three **fully decoded** commands while owner A is current. A real independent child JVM publishes owner B under R64's cooperative OS lock. Each old queued callback is then released **after** takeover, one at a time so it reaches the stale-owner check instead of losing a race for the lock. All three are rejected and handler counts remain zero.
- SHA-256 snapshot comparisons confirm the old and successor WorldPlayer actors retain their pre-test in-memory state; byte-for-byte checks confirm canonical account, generation ledger and session owner files are untouched by the stale requests.
- Fresh commands from owner B reach all three instrumented handlers once; duplicate callback replay is rejected. Deliberate mutation of an old decoded walk request's arrays after capture cannot change the frozen walk target.
- Old generation, replaced actor incarnation, logged-out owner and pending indexed R57/R59 trade all prevent their delayed decoded commands from reaching their handlers.
- Inherited R66/R65/R64/R63/R62/R61/R60/R17 and original exact-client movement, NPC attack and inventory request tests are included in local and independent Linux/Windows regression matrices.

## Explicit limits

1. **Not the actual live request route.** Real `LocalPendingRequestDispatcher`, `LocalSession`, socket receives, authenticated game-session attach, `WorldPlayerPersistence`, combat, movement and bank semantics remain unchanged. The test only drives their existing decoder output into instrumented, disposable handlers.
2. **No real gameplay authority demonstrated.** Stale movement, combat and inventory handler *reachability* is tested, not actual gameplay mechanics or authorization rules. No real NPC damage, in-game movement or exchange is performed.
3. **Only cooperative account-owner locking.** Existing direct session/game handlers do not acquire R60 and may bypass the test gate. R64 owner tokens are not secure login credentials.
4. **No durable event once-only guarantee.** The queue and deduplication set are in-memory. JVM exit may lose accepted unsaved events or make a repeated command possible after restart.
5. **No physical power-loss or multi-file account transaction proof.** R61–R66 address controlled process faults under an isolated cooperative protocol. A hardware power cut, parent-directory fsync and real multi-account persistence are outside this test.
6. **No journal retirement or real account admission.** PREPARED trade participants remain quarantined. R67 does not automatically repair, delete or promote evidence; no real account login is authorized.

## R68 focus

Audit the **actual inbound typed-request dispatcher and its mutation entrypoints** before any wiring. Enumerate which request families mutate WorldPlayer, combat targets, movement, inventory or save queues, which requests are deferred to other threads, and where live session identity is checked. Build an **opt-in isolated dispatcher composition harness** to demonstrate a single central owner gate across all known mutating requests without changing real connection behavior. Avoid claiming live safety until the real dispatcher and asynchronous paths participate in the same authenticated generation/ownership protocol.

**R67 DRAFT, STACKED ON R66, UNMERGED.**
