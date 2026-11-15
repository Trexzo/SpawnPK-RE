# R68 — Real LocalPendingRequestDispatcher mutation/admission audit

## Status

**DRAFT, stacked on R67, unmerged; offline test only.** R68 is the first milestone using the **actual `LocalPendingRequestDispatcher`** routing body instead of a separately constructed handler spy to verify old-session denial. An explicit, off-by-default, test-only hook wraps each already-decoded typed request; **LocalSession never installs it**. All normal request routes preserve their original code and semantics when no hook is present.

This is not a real login, active game packet, account or WorldPlayerPersistence integration. Real session ownership authority and production recovery remain unchanged.

## Observed production pipeline and its current protection

| Stage | Reviewed source | Existing bound | R64/R68 guarantee absent in live code |
|---|---|---|---|
| Client wire | `ClientPacketProbe.readNextKnownPacket` | ISAAC opcode/frame authority, bounded typed FIFO; unknown opcode pauses | Does not authenticate current account-owner epoch after a reconnect |
| Socket acceptance | `LocalSession.java` lines ~925–945 | R50 transport terminal-state checks, `processPendingOnWorld` | No R60/R64 persisted owner lock on actual socket route |
| World actor queue | `LocalSession.java` lines ~1120–1143 | `world.submitAndWait(worldPlayer,worldPlayerGeneration)`; checks on entry and inside world callback | World actor-generation is not tied to R63/R64 on-disk generation / owner UUID |
| Typed request drain | `LocalPendingRequestDispatcher.java` lines ~159–615 | `serverPackets.requireGameActionAdmission()` separately for **each** typed request | Terminal outbound-queue state is not a durable account-owner epoch |
| Real domain mutations | `routeMovement`, `routeNpcAction`, `routeItemAction`, widgets, drops, spells, commands | World execution context, individual handler rules, transport preflight | No shared R60 lock or R64 owner recheck for actual live handlers |
| Asynchronous persistence | `WorldPlayerPersistence.java` lines ~356–465, ~505–625, ~1109–1148 | Snapshot captured under world actor `mutationLock`, owner checked before enqueue; writer drains separate IO queue | The IO `write()` calls `repository.save(snapshot)` later, without a second R63/R64 generation/owner recheck at the actual disk write |
| Deferred login/logout save | `LocalSession.java` lines ~1256–1419 | Captures under world generation; final `submitCapturedWithBackpressure` waits | A delayed queued save must be fenced again at IO commit under an authenticated session ownership authority |
| Trade callback | `LocalSessionRuntimeBindings.java` lines ~72–107 | RUNTIME binding registered under World player ownership; `TradeService.register(..., ()->bridge.saveAccount)` | R55–R67's offline paired-trade intent/commit protocol is not installed in real trade persistence |
| Pet follow/test sequence | `LocalPendingRequestDispatcher.drain` lines ~167–175; `LocalPetRealtimeScheduler.java` lines ~105–160 | `world.scheduleRealtime` carries World actor generation | Scheduling takes place **after typed request drain and outside R68 per-request test hook**; future realtime callbacks are not covered by R68 |
| Tick/teardown | `LocalSession.onWorldTick`, `LocalSessionTeardown` | Tick attachment generation and separated teardown phases | Must audit callbacks triggered by tick / logout and cross-thread saves before live admission |

### Typed request mutation entrypoints inventoried in the real dispatcher

There are **27 top-level routed typed-request classes** in `LocalPendingRequestDispatcher.acceptTypedRequests` / extracted `routeTypedRequest`:

- World/session/UI: `RegionLoadAck`, `InterfaceClose`, `DialogueContinue`, `CharacterDesign`, `WidgetAction`, `AmountEntry`, `ContainerDrag`.
- Inventory and interactions: `DropItem`, `ItemOnItem`, `ItemOnNpc`, `ObjectInteraction`, `PlayerAction`, `NpcAction`, `SpellTarget`, `GenericInteraction`, `Movement`, `ItemContainerAction`, `GroundItem`.
- Currently conservative/directed diagnostics: `PublicChat`, `PrivateMessage`, `NameEntry`, `ChatMode`, `ReportAbuse`, `SocialList`, `LoadoutEditorSave`, `DailyChallenge`.
- Commands and specials: `Command` (including routed dialogue-option paths).

Every routed family is behind the existing per-request R50 transport check. **Not all are state mutators today**: several intentionally produce a fail-closed diagnostic without invoking an unproven target adapter. Do not classify a decoded request as harmless solely because it is currently diagnostics-only, since later target implementations may add mutations.

## Test-only integration seam

`LocalPendingRequestDispatcher` retains the original public/package constructor and normal `drain` path. The existing large per-request branch body is extracted to a **single `routeTypedRequest` method**, converting each top-level `continue` into a method `return`, with no route family, ordering or domain handler changed.

`acceptTypedRequests` retains `serverPackets.requireGameActionAdmission()` for **every** decoded FIFO item. Without a hook it directly calls the original routing logic. With explicitly installed `installOfflineAdmissionForTest` it calls a package-private `OfflineRequestAdmission.withAdmission(request, handler)`; duplicate hook installations refuse.

`R68OfflineRequestAdmission` implements that test hook with a test-owned R66 bound actor. It calls `R66OfflineGameEventDispatch.runVerifiedHandler`, holding the real R60 cross-JVM OS lock across R64 owner UUID/epoch, R63 account hash/generation and R59 pending intent/revision checks, active actor-incarnation validation, then **the actual `routeTypedRequest` domain handler**. Calls missing the R60 same-thread lock are denied by the inherited R63/R64 implementation. The hook is opt-in and is **never constructed from production LocalSession**.

### Important live-integration limitations

- A synchronous domain handler may call `bridge.saveAccount`, output packet methods, asynchronous schedulers, or unrelated World mutation locks. Holding the OS lock across it **would require a reentrancy/deadlock review and an authenticated session owner design** before any live activation; these are not proven safe by the test fixture's no-op bridge.
- A `ClientRequest` is dequeued **before** the test admission hook; when denied, the current stale request is discarded by the caller's exception path, while later FIFO entries remain. Real cleanup/cancellation semantics after admission denial require a separate test against LocalSession teardown.
- R68 guards the synchronous typed-request route only. `petRealtime.ensureFollowScheduled` and `ensureTestSequenceScheduled` after the loop are not wrapped; their async callbacks, World ticks, standalone save callbacks and IO worker writes still require independent fencing.
- The hook carries a test-owned R64 token, **not a real authenticated game session**. Direct ungated local handler invocations and existing live persistence bypass it; no real network hardening is claimed.

## Windows acceptance — November 15, 2026

- Isolated Java21 Gradle `jar testClasses verifyJava11Bytecode --offline --no-daemon`: **BUILD SUCCESSFUL**, 2,263 Java11-compatible class files (major 55).
- `R68_REAL_PENDING_DISPATCHER_OFFLINE_FENCE_PASS`: reuses the **complete existing `LocalPendingRequestDispatcherTest`** fixture with every real domain handler constructed. On an explicitly installed hook, an exact-current opcode 164 movement packet decodes via `ClientPacketProbe` and reaches the **actual** `LocalMovementRequestHandler` while R60 is held; `movement.acceptedPaths` advances by exactly one.
- Before ownership transfer, three independent exact-current movement 164, NPC attack 72 and item-container 145 requests are decoded and queued. A fresh Java child process publishes R64 owner epoch 2. When the old dispatcher drains those requests, all three fail `R64_SESSION_DENIED stale owner` before `routeTypedRequest` is invoked. The admission counter remains at one (the previously valid movement). The old WorldPlayer snapshot and the canonical account, R63 generation ledger and R64 owner record bytes stay unchanged.
- A second attempt to install an offline admission hook is refused; LocalPendingRequestDispatcher's original, hook-free comprehensive 12-family test still passes, proving the default dispatch path is intact.
- Inherited R67 (decoded packet spy), R66 in-memory events, R65 callbacks, R64 session ownership, R63 generation, R62 completion, R61 recovery, R60 OS lock and R17 lease-recovery suites all remain green locally.
- The dedicated Linux/Windows CI includes the new actual-dispatcher fixture, unchanged baseline, inherited R55–R67 and movement/combat/item typed decoder tests.

## Next priority — R69

Perform a **separate offline IO-commit ownership audit and a test-only `WorldPlayerPersistence` write-admission composition**. Capture a real deferred profile snapshot while session A owns its World actor, hold the test-owned writer queue, give account ownership to session B through the R64 protocol, then release the old save and verify **the stale save is refused before `FilePlayerRepository.save`**. Audit checkpoint coalescing, final save and shutdown/backpressure semantics, rather than integrating R64 directly into the live IO worker prematurely. Real multiplayer login and account recovery must remain off by default.

**R68 DRAFT, STACKED ON R67, UNMERGED.**
