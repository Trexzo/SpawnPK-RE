# R61 — Offline paired-trade COMMIT decision and idempotent roll-forward

## What R61 fixes in the isolated model

R56 demonstrated that saving just one participant after a successful coin/shark trade can create valid individually loadable accounts whose combined inventories lose or duplicate items. R57–R59 added offline prepared intent evidence, before/after SHA-256 snapshot hashes and a completeness-checked pair registry; R60 added an OS-level cooperative reader/writer lock across two JVMs. None of those versions could **repair** a known two-account split-save after a restart.

R61 introduces a **second, explicit COMMIT decision** and independently stored immutable-after-snapshot payload files under a test-owned storage directory. A new JVM can use them to roll a known partial pair **forward**, only after exact verification of both participants' registered PREPARED intent, the COMMIT decision, both post-state payloads and both existing canonical account snapshots.

**This is an isolated test architecture only. It has not been connected to any real LocalSession, TradeService, account repository login gate, WorldPlayerPersistence or autosave callback.**

## Offline commit evidence

`R61TradePairReplay.publishCommitDecision(journalRoot, payloadRoot, txId, beforeA, afterA, beforeB, afterB)`:

1. Holds R60's exclusive cooperative OS lock across the whole operation and requires R59's indexed PREPARED intent for *both* account names.
2. Uses R57's strict journal review to prove supplied pre-snapshots match `BOTH_BEFORE` and supplied post-snapshots match `BOTH_AFTER`.
3. Saves both full post-state `PlayerSnapshot` payloads to a transaction-specific, privately created directory using the existing R16 forced-stage/atomic-replace account publisher. Reloads the files and checks every snapshot hash.
4. **Only after both post-state files match**, publishes a separate strict-schema `decision-<UUID>.properties` record containing `phase=COMMIT`, both account names and both post-snapshot digests.
5. Refuses duplicate decisions or a non-new payload directory. A failure before publishing COMMIT leaves evidence intact, but is not permission to overwrite an account.

The R57 PREPARED intent alone never authorizes the simulator to write an account. COMMIT means a specific **offline roll-forward decision** supported by two complete post-state snapshots; it does not imply the real game ever durably agreed to that decision.

## Restart/replay algorithm

`R61TradePairReplay.replayCommitted(journalRoot, accountRoot, payloadRoot, txId, injectFault)` executes entirely under the same R60 OS lock:

- Reads a bounded, versioned COMMIT marker with exact known keys, canonical UUID, ordered accounts and valid digests. An ambiguous R16 stage is refused without cleanup.
- Requires R59 registry membership for both account names, with **exactly one** pending transaction per participant. A non-cooperative writer that has added a second valid-looking intent for either account forces refusal; membership in the requested transaction alone is insufficient.
- Reloads **both** stored post-state payload files. Verifies their hashes against COMMIT, then checks that the R57 PREPARED journal identifies precisely that `BOTH_AFTER` pair.
- Loads **both** canonical account snapshots. A missing, corrupt, unrelated or otherwise unknown state fails closed **before writing either account**.
- Uses R57's four-state review. For `BOTH_BEFORE`, writes A and B post-state; `ONLY_A_AFTER`, writes B only; `ONLY_B_AFTER`, writes A only; `BOTH_AFTER`, writes nothing. Each write is individually atomic via the existing account repository.
- Reloads both canonical accounts to verify `BOTH_AFTER`. Re-reads the indexed pair before return. The R57/R59 PREPARED evidence remains registered and **both accounts still fail the journal admission test**. No COMPLETED marker, index retirement, login release or automatic cleanup is implemented.

An interrupted roll-forward can be called again: the next replay sees a valid one-sided known state and writes only the missing post-state. Replays never intentionally revert a published post-state or apply a guessed default state.

## Connected Windows verification — November 15, 2026

- JDK 21 `jar testClasses verifyJava11Bytecode --offline --no-daemon`: **BUILD SUCCESSFUL**; 2,211 Java11-compatible classes (major 55).
- `R61_OFFLINE_PAIRED_TRADE_REPLAY_PASS`: four actual filesystem checkpoint states created after a successful in-memory `TradeService` coin/shark exchange, replayed in **fresh child JVMs** to verify consistent final inventories.
- Idempotent replay from `BOTH_AFTER` performs zero account writes.
- A deliberately interrupted first replay throws after publishing only account A. A later **new JVM** completes B without further coin/shark duplication or loss.
- An additional **test-owned child JVM is abruptly terminated via `Runtime.halt(91)` immediately after its first individual account publication**, without running Java shutdown hooks or `finally`. The parent observes the exact exit code, confirms one account is old and the other new, then launches another independent JVM which completes the pair. This is a real abrupt JVM exit against disposable files, **not a physical power interruption**.
- The PREPARED-only path without a COMMIT marker refuses to write accounts. Corrupted post-state payload or unknown canonical gameplay data is rejected without touching the other account. A leftover test-owned COMMIT staging file causes refusal and is preserved until the test harness itself removes its own fixture.
- Tests assert both participants remain indexed and blocked even after both account files match the post-state.
- A negative control uses R57/R59 direct offline helpers to add a second, otherwise valid, unresolved transaction involving the first account. R61 refuses to replay the original transaction and preserves both canonical account files byte-for-byte.
- R60, R59 and R56 inherited tests **PASS** on the connected Windows machine. The dedicated Linux and Windows GitHub workflows include R61 and R55–R60 plus normal trade-flow regressions.

## Explicit unsolved boundaries

1. **No production integration.** Actual trade commits still use independent account save callbacks. Without connecting a correctly authorized and durable coordinator to real trade logic, R56's production split-save window remains unsolved.
2. **Not a hardware power-loss guarantee.** R16 forces individual staging files and requires atomic move but does not prove parent-directory fsync durability on each platform/storage device; JVM fault injection is not a power cut.
3. **No authenticated COMMIT authority.** A malicious writer with access to rewrite both payloads and journal/decision/index could forge evidence. SHA-256 alone detects mismatches, not malicious coordinated rewriting. No HMAC/signing or protected key ownership is implemented.
4. **No durable account generation fencing.** Ordinary autosaves, logout saves and login reattachment do not use this OS lock in the actual server; they could race a future replay unless the whole persistence pipeline adopts the protocol.
5. **No safe finalization/retirement.** Even after both accounts match post-state, R61 does not delete intent, mark COMPLETED or permit login. Removing journal evidence prematurely could unquarantine users while asynchronous save callbacks are still pending.
6. **No guarantees against non-cooperative writers or path races.** The R60 lock only coordinates code that voluntarily uses it. Full filesystem adversary and parent-directory metadata semantics remain unproven.
7. **No loss-free behavior for missing, corrupted or unexpected snapshots.** R61 refuses account modification and requires manual/independent adjudication, rather than inventing an outcome or replacing legitimate post-transaction changes.

## Next validation priority

R62 should add a **strict offline completion/quarantine-release decision** (not live login release) that requires a durable revision fence, both canonical post hashes, a verified COMMIT record and no outstanding writer. Exercise interrupted completion, stale participant generation, extra autosave and repeat start. Only after that should the real World/login/save integration be considered.

**R61 is DRAFT, STACKED ON R60, UNMERGED. No live account actions have been authorized.**
