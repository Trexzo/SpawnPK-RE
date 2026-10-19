# SpawnPk-Decompiled-main (5) donor intake — verified R1

Archive SHA-256: `897a668bb7279b360c268a5bcdb8cfbe83a73a4a077d537256c164bca6c02da9`.
File count: **7,636 ZIP entries**. Audited **without extracting or executing archive code**.

## Important authority boundary
This is a community reconstruction. The archive's `docs/HANDOFF_TREXNO_SYSTEMS.md` calls the `spk/local` server a reconstructed reference. In `server/plugins/trade/.../TradeServiceImpl.kt`, the author explicitly states that it was re-expressed from `spk/local/TradeService.java`. Therefore the Kotlin implementation is **not independent original SpawnPK protocol evidence**, and porting it back wholesale would add little value and risk losing RE's established concurrency defenses.

## Sources worth reviewing first
1. `server/plugins/recipes/`: 9 Java/Kotlin sources. Compare its conversion/rollback invariants against actual RE inventory and skilling constraints; do **not** copy unverified IDs or XP rules.
2. `server/plugins/party/`: 10 Java/Kotlin sources. Compare participant identity/disconnect ownership before adapting any multiplayer party model.
3. `server/plugins/global-events/`: 13 sources; candidate modular scheduling/lifecycle concepts, not original timed-event authority.
4. `server/plugins/collection-log/`: 9 sources; investigate durable per-account item evidence and presentation separately from item grants.
5. `server/plugins/usage-quotas/`: 9 sources; evaluate only after account durability ownership and persisted timestamps can be trusted.
6. `server/plugins/trade/`: 6 sources. **Reference/backport relationship confirmed**; use only to contrast behavior and discover missing regressions, never as a replacement for RE's trade transaction layer.

**Caveat:** The server `gear` plugin alone contains more than 3,000 source files, primarily item-specific definitions. A high file count does not imply 3,000 implemented gameplay features.

## Reproducible intake
Run `python scripts/audit_spawnpk_donor.py "<archive.zip>" --output donor-manifest.json` to produce a read-only SHA-pinned inventory. Optional `--compare-zip "<old.zip>"` compares Java/Kotlin source bytes only when both archives contain the same normalized path under `server/`. This is a file-level comparison, not semantics or common authorship. Use `python -m unittest discover -s scripts -p 'test_audit_spawnpk_donor.py'` for scanner safety tests.

## Next development gate
Select one donor recipe/party implementation **after** confirming the corresponding RE runtime integration path and original v308 packet acceptance. Write one deterministic RE regression before porting functionality. Do not merge or close an issue based on donor source alone.

## Source-only recipe integration checkpoint (PR #2965)

The donor's recipe implementation is treated as a **testable design reference**, not client/server authority. The following implementation now exists on the draft branch:

- `RecipeConversionQuantityPlanner`: bounded batch arithmetic, retained/consumed tool overlap, null count rejection and consumed-tool-only conversion parity.
- `RecipeStackPostimagePlanner`: read-only stackable inventory postimages, quantity conservation, capacity/overflow rejection, canonical empty-slot validation and tool-aware preparation.
- `RecipeBankSemanticAdapter`: snapshot-sealed one-shot prepared authority for one `BankState`; stale inventory and bank-overlay transitions reject before queue publication. Preparation/direct commit synchronize on the BankState monitor, but this is **not** a cross-World account lock.
- `ServerPacketWriter` + `OutboundPacketQueue`: queue-only recoverable atomic presentation admission of opcode-53 inventory views; direct non-retractable writer paths are denied. A failed queue admission preserves the semantic preimage and permits a safe retry.
- `BankState.previewRecipeInventoryPacket`: malformed negative and non-encodable client item IDs are denied rather than normalized to empty slots. Open banks use both normal and bank inventory views in a single queued batch.
- `LocalItemOnItemHandler`: no new donor recipe actions are registered; unverified item-on-item pairs remain denied. The existing narrow Doppelganger dye route remains separate.

### Deterministic verification

Mandatory Gradle build tasks include `recipeConversionQuantityPlannerRegression`, `recipeStackPostimageRegression`, `recipeBankSemanticAdapterRegression` and `recipeItemOnItemDispatchBoundaryRegression`. The recipe test classes now cover:

1. Overlapping retained and consumed tools, tool-only conversions and 512 seeded quantity/postimage parity trials.
2. Max-stack spillover, missing inputs, malformed inventory slots and 1,024 seeded item-ledger conservation trials.
3. Stale and tampered prepared requests, one-shot replay rejection, terminal/busy writers and zero-byte queue retractions.
4. Fault injection between bank-open views at the recoverable publication callback, followed by byte-exact outbound ISAAC retry comparison.
5. Closed-to-open and open-to-closed bank overlay transitions, rejecting obsolete prepared requests before publication.

These are **synthetic/source-only tests**. Any successful GitHub Linux Gradle or Windows preflight verifies the exact tested commit only; it is not a real v308 GUI, TCP reconnect, remote acknowledgement or power-loss result. Check the current PR head's Actions before claiming an exact-head green status.

### Explicitly unresolved — do not enable recipes yet

- No exact v308 client recipe UI, widget action, item/stackability evidence or XP grant has been established by the donor.
- Queue admission is not socket delivery, remote client acknowledgement, durable profile write or reconnect consistency.
- Account generation, session writer lease, cross-World/cross-JVM exclusivity, and final-save crash durability are not implemented by this PR. Follow account-safety PR #2963 and issues #2540 and #2559 rather than inventing a separate recipe-specific recovery authority.
- Prepared requests must not cross live session/account boundaries. There is no active gameplay entry point; no recipe registration, implicit donor item grant, auto-rollback or account marker cleanup is permitted.
- Merge and issue closure require the latest exact-head CI results and review of runtime authority boundaries, not simply a growing synthetic regression count.

## Mixed-item recipe milestone — source-only implementation

`RecipeMixedPostimagePlanner` supplements (does not replace) the verified-stackable-only planner. It accepts explicitly supplied and mutually exclusive verified stackable/nonstackable item ID sets; both the recipe items and every occupied inventory slot must have a known classification. Nonstackable inputs consume exactly one item per slot; nonstackable outputs require a distinct empty slot for every unit. Stackable outputs still merge safely and split at `Integer.MAX_VALUE`. Retained/consumed tool overlap remains quantity-checked. A planned result is computed against copied arrays and rejects capacity shortfall before mutating any `BankState`.

`RecipeBankSemanticAdapter.prepareMixedWithTool` passes the mixed postimage to the existing sealed, one-shot, queue-only inventory publication boundary. This is **not a registered crafting feature**; synthetic test IDs and classification sets do not establish real v308 asset semantics.

The Gradle build now includes mandatory `recipeMixedPostimageRegression` and `recipeMixedBankAdapterRegression` tasks. Together they cover nonstackable slot reuse/capacity, retained and consumed nonstackable tool handling, 1,024 seeded per-item conservation checks, 512 differential cases against the original all-stackable planner, queue admission, one-shot replay, queue-full retry, and bank-open mirrored inventory publication.

### Additional publication authority defenses

- Bank overlays now carry a monotonic presentation revision. Every successful open/refresh and close transition invalidates previously prepared recipe views; failed publications leave that revision unchanged. ABA transitions (open-close-open, closed-open-closed, and client-close-reopen) have regression coverage.
- A prepared recipe pins the outbound writer on its first attempted publication. Queue backpressure remains retryable only through that same writer, and a second writer is denied before queue admission. **This is not a persistent session lease or cross-World account ownership protocol.**
- No source-only recipe planner supplies a live account generation, final-save journal, or client acknowledgement. Coordinate those separately with issues #2540/#2559 and PR #2963.

**Release gate:** treat every newly committed test as pending until both Linux Gradle and Windows source-only CI pass at the exact PR head. Neither source-only tests nor synthetic opcode-53 packets certify player-visible crafting.

## Subsequent mixed-item correctness hardening

- The stackable-only recipe planner now requires a retained tool ID to belong to its explicitly verified stackable set, rather than accepting any observed inventory ID. Nonstackable tools belong to the mixed-item planner, which demands mutually exclusive classification evidence.
- The stackable-only planner rejects unencodable opcode-53 input, output and occupied-slot item IDs during preparation, not merely when later generating the packet body.
- Mixed-item property regressions now include another 1,024 seeded retained/consumed-tool ledger cases, including overlapping ingredient/tool IDs, preserved tool quantities, affordability denial and input immutability.
- The mixed BankState bank-open regression now compares actual queued bytes to an independently constructed reference publication, matching both inventory views and the outbound ISAAC stream, rather than only asserting a non-empty queue.

All source-only planner tests use intentionally synthetic IDs. Successful packet framing verifies only the locally reconstructed packet writer, not original v308 client-side acceptance or persisted account safety.

**Exact-head rule:** the Java/Gradle and Windows workflows for this new head must both conclude successfully before these changes are called verified. Earlier green commits do not certify later code.

## Source-local inventory ABA and publication bypass hardening

A slot-for-slot comparison is insufficient if inventory A is mutated to B and then restored to A before a prepared request is published. The existing BankState now tracks a **source-local monotonic inventory mutation revision**, advanced by normal inventory postimage replacement (`replaceStacks(inventory, ...)`), semantic full replacement, exact-slot semantic consume and account snapshot restore. A prepared recipe seals this revision along with the existing bank-overlay view revision. Ordinary changed-slot denial retains `RECIPE_STALE_INVENTORY_POSTIMAGE`; only restored-value ABA is reported as `RECIPE_STALE_INVENTORY_GENERATION`.

The source-only regressions exercise semantic replace-and-restore, exact-slot consume-and-restore, account snapshot restore, and structural add-and-restore. The mixed-item BankState integration regression additionally tests queue-full backpressure, a same-values semantic inventory replacement, rejection of the old prepared request with zero queued recipe bytes, and successful publication from a fresh prepared request. Rejected inventory packet publication and invalid semantic postimages must leave the mutation revision unchanged; new accepted structural inventory publication advances it. A newly prepared request after a local mutation remains valid.

A prepared request that has begun a queue-backed publication attempt is permanently pinned to its outbound writer. It **may not subsequently invoke the direct source-only semantic-commit seam** to bypass backpressure or a failed publication. The successful queue-admission branch uses a separate internal commit path and the retry path remains available only through the original writer. The direct path is still intended solely for isolated source tests, not live gameplay authority. The read-only prepared preview now synchronizes on the same BankState monitor as preparation and queue publication.

**Boundary:** These revisions only fence calls participating in the same BankState instance and its tracked mutation APIs. This PR does not seal external mutable Stack aliases, implement a globally exclusive World/account lease, persist a revision over restart, provide crash-durable intent records, or prove original-client network acknowledgement. Do not claim an account-durability fix or enable donor recipes based on these local guards. Exact-head CI is mandatory.

## Mutable-alias and concurrent-admission isolation

Two additional `BankState` boundaries have been hardened for source-local inventory revision integrity:

1. `inventoryAt(slot)` and `bankAt(slot)` now return defensive `Stack` copies. A caller mutating the returned quantity or tab cannot silently modify canonical `BankState` contents without incrementing its inventory mutation lineage.
2. `restoreAccountState` now deep-copies the supplied bank and inventory `Stack[]` snapshots before admission. Mutations to caller-owned arrays or contained mutable stacks after restore no longer affect hydrated account data. This does **not** turn account hydration into a durable save or add a cross-World lease.

`replaceInventorySemantic` also rejects noncanonical empty sentinels below -1 and client-unencodable item IDs (65,535 and higher) before modifying any slot; invalid postimages must not advance the mutation revision.

The source-only recipe regression suite adds an accessor/restoration alias test and **128 two-thread admission trials** on a single `BankState` and packet writer. In each trial, two separately prepared requests race at a start gate against the same initial inventory. Exactly one may publish/commit; the other must report a stale preimage and no second packet batch may be queued. An additional **64 two-writer races** intentionally violate live writer ownership while sharing one BankState, and assert exactly one of the writers gets any recipe bytes; these tests demonstrate local stale-request rejection, **not** genuine multi-session isolation. These are local deterministic scheduler tests, not production session or network admission tests.

**Outstanding:** arbitrary callers may still mutate state through *other* APIs that do not participate in the recipe monitor; this PR does not establish exclusive World-thread lifecycle ownership, account epochs or crash durability. A successful source-only build must not be described as original v308 GUI gameplay verification.

## Source-only bridge into existing World owner generations

`RecipeWorldOwnedAdapter` now wraps recipe preparation **and** queue-backed publication with `World.withOpenPlayerMutationOwnershipIfCurrent(player, expectedGeneration, action)`. It uses the canonical `WorldPlayer.bank()` (not an externally supplied inventory) and seals both the exact `World`/`WorldPlayer` identities and generation in a private `Prepared` object. The outbound writer is bound at preparation. Unregistered, expired-generation, foreign-World and closed-World ownership rejects before the underlying recipe publisher can enqueue or commit. This leverages existing World lifecycle/player-mutation locks rather than inventing a recipe-specific named-account lease.

A mandatory `recipeWorldOwnedRegression` Gradle task exercises real in-memory-repository World membership: unregistered preparation refused, registered mixed-item publish and replay denial, unregister/rejoin invalidating prior prepared requests, foreign-World denial, backpressured old-generation retry refusal and successful fresh-generation retry. It also runs **32 publication/unregister races**, accepting only the two valid linearizations: complete recipe publication before unregister, or zero-byte rejection after unregister. Each case asserts inventory/queue consistency and no post-exit replay.

**This is a source-only integration seam, not a live recipe handler.** It has not been wired into `LocalSession` packet actions or v308 client-visible crafting; no new real item IDs or XP have been admitted. `World.withOpenPlayerMutationOwnershipIfCurrent` proves only one World's *registered player generation*; it does **not** establish globally unique named-account ownership across different Worlds, independent JVMs or a durable post-crash epoch. Those gaps belong to the separate design PR #2963 and open issues #2540/#2559. An optional experimental `LocalJvmAccountSessionLease` is not promoted to mandatory authority by this recipe bridge.

A source-only CI PASS means the exact checked commit compiled and passed configured regressions; it does not prove remote socket delivery, actual client acceptance, session reconnect handling or account persistence.

## Exact LocalSession writer witness (source-only)

`RecipeSessionWriterGuard` is a revocable, exact-identity witness for one `World`, `WorldPlayer`, registered generation and `ServerPacketWriter`. `LocalSession` now creates it immediately after the login outbound writer is constructed, **revokes it as soon as an ordinary client logout is requested**, and calls `revokeAndAwait()` again **before** normal session command/tick teardown. The monitor ensures any already-admitted guarded action finishes before revocation returns; later calls reject with `RECIPE_SESSION_WRITER_REVOKED`. A foreign writer/player/World/generation rejects `RECIPE_SESSION_WRITER_IDENTITY_MISMATCH`, and a terminal writer rejects before queue admission.

`RecipeWorldOwnedAdapter.prepareSessionStackable`, `prepareSessionMixed`, and `publishSession` wrap both steps in the existing World player mutation ownership lock, then the exact-writer witness (lock order: **World → session writer witness → BankState**). A private `SessionPrepared` token stores the original witness and prepared inventory; callers cannot substitute a writer when publishing. The older direct World-only adapter remains **source-only** and does not authorize session gameplay.

The mandatory `recipeSessionWriterGuardRegression` Gradle task exercises genuine isolated World membership, exact writer/player mismatch, revoked preparation and publication, queued-backpressure revocation and fresh-generation witness recovery after unregister/re-register, terminal writer denial, an in-flight action revocation barrier, and **64 revocation-vs-publication races** that must either commit before revocation or deny with zero item/packet effect. This adds tests but does NOT activate any recipe opcode or turn the optional experimental JVM account lease into production authority.

**Not certified as live gameplay:** The witness is a package-local source integration primitive. Although real `LocalSession` constructs and revokes it, there is still no original-client recipe dispatch, UI evidence, item registration, socket/client ACK, durable owner epoch, or crash-replay contract. The source-only tests construct their own witnesses deliberately. Real account and two-JVM ownership/durability work remains on #2540/#2559 and PR #2963; the session witness is not a substitute for those contracts.

## R6 correction: compile failure and one-time witness claim

The original exact-head R6 commit `2dbd19de` **failed both Linux and Windows** at `RecipeSessionWriterGuardTest.java` compilation: a Runnable in the 64-case revocation race used `CountDownLatch.await()` without handling checked `InterruptedException`. Commit `8924fb13` fixes the checked interruption path and preserves the interrupt flag, with new CI required; no preceding PASS should be cited for the failed commit.

Further source audit identified an actual same-generation resurrection risk: a revoked `RecipeSessionWriterGuard` could be constructed again for the same still-registered `WorldPlayer` generation, including a substituted outbound writer. `WorldPlayer.claimRecipeWriterGeneration` now seals one irreversible **in-memory** writer-witness claim per registered generation. Its construction passes through `World.withOpenPlayerMutationOwnershipIfCurrent`, so unregistered, stale or foreign-World players cannot consume a rightful claim. Revocation or backpressure does not release the claim; only a new successful World registration generation can create a new witness. This prevents a future verified recipe handler from treating a second manually minted session guard as a legitimate logout recovery.

Mandatory session guard regressions have been updated for unregistered claims, same-generation substitute-writer rejection, foreign-World claim denial with a rightful subsequent bind, backpressure revocation, re-register/fresh-generation rebind, **64 simultaneous witness-claim races** (exactly one winner) and existing 64 publication/revocation races. No account-wide/JVM-wide or process-durable epoch is implied; distinct WorldPlayer objects representing one named account remain the separate issue #2559, and failed final-save recovery remains #2540.

### Login-to-World logout visibility

The `LocalSession.recipeSessionWriterGuard` field is now `volatile`: the socket/login thread creates the guard, while logout can be requested on the World callback path. The existing two-sided logout check remains: the callback sets the volatile `logoutRequested` flag and revokes any published guard; login checks that flag after guard publication and revokes immediately if logout arrived during construction. A source regression checks the field's volatile modifier so a later cleanup cannot silently remove this cross-thread visibility contract. This is local JVM visibility, not a durable session epoch.

## R7 follow-on: full World lifecycle proof, terminal/reentrant writer denial

The recipe witness no longer treats possession of the player's mutation monitor as sufficient authority. `World.currentThreadHoldsOpenPlayerMutationOwnership` requires the **current thread** to hold the exact World's lifecycle monitor and that player's mutation monitor, verifies the World is open, and rechecks exact player membership plus registered generation. `RecipeSessionWriterGuard.runBound` rejects any failure with `RECIPE_WORLD_MUTATION_OWNERSHIP_REQUIRED` before executing its callback. A manual `synchronized(player.mutationLock())`, stale unregister/re-register owner, or unowned direct call is not valid. This is an **in-process World authority check**, not a cross-World account ownership lease.

An outbound writer that is already terminal **before** constructing the witness is rejected *without consuming* the one-time generation claim. A writer that becomes terminal after construction causes the first attempted guarded action to revoke its witness; subsequent preparation is denied and the generation cannot mint a substitute writer. Nested/reentrant writer action entry is also denied by an in-flight latch, with `finally` cleanup after normal and exceptional callback exit. The previously established publication and sealed-inventory consistency checks still own item mutation.

Updated synthetic regressions cover legitimate World-owned action, manual player-lock spoofing, expired owner under manually held lock, terminal prebind/after-bind rejection, non-resurrectable generation, nested calls and in-flight cleanup after checked exceptions. A separate isolated-World fixture prepares a bound request, closes its World before publication, and asserts no queued packets or inventory changes and no re-created witness under the closed World. These tests do **not** prove a live LocalSession recipe action, original v308 packet agreement, durable final save or remote acknowledgement. Keep PR #2965 draft until latest exact-head Linux and Windows CI report success.

## R8 follow-on: legacy authority bypass closure and read-only session preview

The earlier `RecipeWorldOwnedAdapter.prepareStackable`, `prepareMixed`, and `publish` paths used the correct World/player generation but **not** the newer exact session-writer witness. Without a further gate, their package-local callers could still publish from an arbitrary writer after the live session claimed its witness. `WorldPlayer.requireUnclaimedRecipeGeneration` now rejects those weaker, source-only paths as `RECIPE_WORLD_ONLY_PATH_BLOCKED_BY_SESSION_WRITER` once a writer witness is claimed for that generation. The check executes **inside** the existing World player ownership monitor. A request staged before the claim is also denied at publication after the claim. Revocation does not reopen that authority; a newly registered World generation is required. The deliberately source-only, unbound regression paths remain functional only if no session writer has been claimed.

The regression suite includes direct before/after witness-claim denials and **128 two-thread races**: 64 concurrent legacy-prepare versus writer claim and 64 legacy-publish versus writer claim. Only the legal lock-linearized outcomes are accepted: a preparation already completed before the writer claim remains unpublishable afterward; a legacy publication may succeed if it committed **before** the writer claim, but it cannot enqueue or change inventory after the writer claim wins.

A new `RecipeWorldOwnedAdapter.previewSession` provides a *read-only* inventory postimage preview through the **same current World + exact session-writer witness** as the queue-backed publication path. The returned bytes are detached from internal state, and preview does not mutate inventory, alter the item mutation revision, enqueue packets, advance the outbound ISAAC stream, or perform an account save. Multiple previews of an unchanged preparation are stable; successful queue-backed publication consumes the prepared authority. Source regression cases cover stable copies, later publication, changed-value ABA via inventory mutation revision, revoked writer, and expired World generation.

**This is not live crafting or an original-v308 packet contract.** `previewSession` returns locally synthesized prospective inventory bytes, not observed client acceptance or a socket delivery/ACK. The legacy lower-level `RecipeBankSemanticAdapter` direct test seams also remain source-only; this batch closes the *World-only adapter* session-witness bypass, not every possible unrelated package-local caller. Account ownership across multiple Worlds/JVMs and crash-durable final saves remain separate blockers (#2540/#2559, PR #2963).

### Source-only mirrored bank-open preview

`RecipeBankSemanticAdapter.encodePreparedInventoryViews` and `RecipeWorldOwnedAdapter.previewSessionViews` expose detached, read-only bodies for **all** affected recipe inventory mirrors. A closed-bank view yields only normal inventory widget 3214; a bank-open view yields widget 3214 followed by bank-inventory mirror widget 5064, matching the existing queue publication's two-view ordering. The preview is guarded by the same World/player generation, revocable exact-writer witness, sealed postimage and bank-view generation as publication, with no writer queue admission or ISAAC advance. The returned `byte[][]` contains independent byte arrays so caller edits cannot change a subsequent preview.

New regression covers an opened-bank pair of widget 3214/5064 bodies, unchanged inventory and revisions, no queued recipe bytes, detached copies, close/reopen (bank-view ABA) invalidation, successful fresh preparation/publication of a single mirrored batch, and denial of preview after that commit. The primary single-view preview and the queue-backed publisher now both delegate to `encodePreparedInventoryViews` for one sealed all-view preflight, so a bank-open mirror encoding error cannot be hidden by a successful normal-inventory preview and publication does not re-encode the same postimage through two separate paths. This is only source-side packet-body evidence, **not original-client display, network delivery or crafting enablement**.

## R9 continuation: exact preview/ISAAC parity and terminal callback revocation

The mandatory `recipeSessionPreviewWireParityRegression` now compares two otherwise identical isolated World/player/writer sessions: one directly publishes a synthetic, fully verified-classification mixed recipe and the other performs **1, 5, or 64 read-only preview iterations** before publishing. Both bank-closed and bank-open (3214 + 5064 mirrored view) cases compare the complete queued byte stream and all final inventory slots/quantities. Each preview arm asserts no queued bytes, no inventory mutation, no bank overlay generation change, and detached preview bodies. This provides source-only evidence that the preflight does not advance the outbound ISAAC stream or alter the eventual queued packet content; it remains **not original client runtime verification**.

`RecipeSessionWriterGuard.runBound` now checks `writer.terminal()` in its `finally` block as well as before callback admission. A writer becoming terminal *during* an action revokes that generation's witness before the action exits, even if the action itself returns successfully or throws a checked exception. New mandatory guard regressions cover both callback outcomes and deny subsequent publication or same-generation reminting.

**CI rule:** the prior base `931d81ca` passed both configured Linux/Windows jobs. Later exact-head commits, including these changes, require their own successful CI; earlier Windows smoke success alone does not certify the full Gradle regression suite. PR #2965 remains a draft; no recipes have been registered or enabled.

### Sealed postimage after queue admission

The queue-backed recipe publisher now uses the exact privately cloned/validated postimage for its **post-admission** semantic commit, without re-reading the mutable `Prepared.proposed` test fixture a second time after packet bytes have already entered the outbound queue. The preflight still rejects a modified proposed view **before** publication and checks the World-local inventory/overlay revisions. This narrows the risk that an unrelated mutation of the deliberately exposed test object after successful admission would turn an already queued client update into an uncommitted server inventory. A regression confirms post-commit edits to that test view leave canonical inventory and queued bytes unchanged. The source-only transaction still does not imply socket delivery or persisted account state.

### Exact Linux regression failure and deterministic recovery

The `2bac3d81` Linux Gradle workflow **FAILED** at the newly added `recipeSessionPreviewWireParityRegression`, with `RECIPE_PREVIEW_WIRE repeated preview mutated next body`. The failure was in the test fixture, not a confirmed recipe writer mutation: the regression held `initial=views` and then intentionally corrupted `views[0]`, thereby corrupting its own expected baseline. Commit `f701677b` fixes the golden comparison by deeply cloning the first preview's two-dimensional array before deliberate corruption. Both Windows and Linux must still pass at the eventual exact PR head; do not describe the failed commit as verified.

A further deterministic concurrency regression now holds the outbound queue monitor, suspends a recipe publisher when it reaches `OutboundPacketQueue.tryOfferBatch` **after sealed preflight but before queue admission**, and mutates only the deliberately exposed test-facing `Prepared.proposed` fields. After releasing the queue, it demands one committed packet batch, identical canonical sealed postimage and no exception. This targets precisely the gap eliminated by the sealed post-admission commit change. It is an isolated synthetic authority regression, not evidence of a remotely delivered packet or durable save.

### Caller recipe-definition snapshots

`RecipeBankSemanticAdapter.prepareWithTool` and `prepareMixedWithTool` now copy ingredient maps and verified stackability/nonstackability sets before planning. The planner therefore evaluates one stable, local snapshot of caller-supplied recipe information instead of repeatedly consulting a map/set whose owner may later reuse or modify it. Preparing a request still does **not** register that recipe as verified client behavior. Added stackable and mixed-item regressions mutate the original maps and classification sets after preparation, then assert the prepared preview and exactly-one queued inventory conversion remain unchanged.

The copy is an in-memory, one-operation source boundary, not a persistent recipe registry or provenance ledger. Concurrent mutation *during* the copy may still cause a fail-closed collection exception; it does not authorize output inventory changes.

### Stackable-only occupied item classification gate

`RecipeStackPostimagePlanner.prepare` now requires each **occupied source inventory slot** to have a verified stackable classification, even if its item ID is not an ingredient or output. An unknown occupied item must not be silently encoded/reconciled under an all-stackable planner. A valid, unrelated, verified stackable item remains unchanged. A mixed/unknown collection should use the separate explicitly classified mixed-item planner or reject; no live item IDs are inferred from synthetic fixtures.

New stack-only regression cases assert rejection of an unrelated unverified item and item ID zero even if zero is supplied in the classification set, and preservation of a verified unrelated stack. This is a **source-only scope guard**, not evidence that a real player's inventory is fully classified.

### Existing live opcode-53 dispatch remains fail-closed

No synthetic donor recipe was promoted into `LocalItemOnItemHandler`. The live handler retains only its narrowly identified Doppelganger dye interaction, while unsupported item pairs or widgets return without an inventory mutation, save reason or outbound bytes. The existing mandatory `recipeItemOnItemDispatchBoundaryRegression` now includes **1,024 seeded unsupported dispatch requests** with varied slots, IDs and inventory/invalid widget combinations. It excludes the known dye item pair and asserts zero wire bytes and unchanged inventory/overlay revisions for every unsupported request. This protects the intentionally unimplemented recipe admission boundary while client evidence is missing.

## Existing live Doppelganger dye transaction — owner-neutral regression

In addition to synthetic recipe planning, this branch now directly tests the **existing live** item-on-item Doppelganger dye behavior, 3241 + 28824 -> 28807. No donor recipe registry was installed. `DoppelgangerDyeTransactionRegression` is mandatory in the Gradle build and invokes the real `LocalItemOnItemHandler` and `BankState.combineInventoryOne` on in-memory inventory and an outbound queue. It covers both item selection directions, bank-open/closed states, dye quantity conservation, stacked regular-pet rejection, full-queue failure preserving state, and **64 simultaneous attempts** admitting exactly one dyed item and queued publication.

The live `combineInventoryOne` method now holds the outbound writer monitor through its validation, publication and semantic commit. It refuses terminal writers or pre-existing packet batches **before** any dye inventory mutation or save intent, preventing a dye conversion from accidentally becoming part of some other batch that may later abort. Regressions also verify that a legitimate request succeeds after a busy batch is aborted. This is a writer-local failure-atomicity fix, not a durable player-save contract or account lease. It preserves the original item pair and output and adds no other live crafting behavior.

**Earlier exact-head CI correction:** `5fa9c133` failed Linux at `recipeSessionWriterGuardRegression` because the stricter all-occupied-stackability check revealed an older fixture that put synthetic item 200 in its stackable-only inventory without classifying it. Separate legacy stackable test fixtures now explicitly classify 200 as stackable **only within that synthetic test scenario**; mixed-item tests continue to classify it as nonstackable. The planner's stricter runtime rejection is unchanged. The fixture correction and new live dye tests require exact-head Linux and Windows CI to pass before being declared verified.

## R12 continuation: live Doppelganger dye concurrent ownership and strict recipe identity

The previously committed `b9ca6984` exact-head **Linux Gradle build failed** in `doppelgangerDyeTransactionRegression`: the 64-case two-thread dye regression emitted the assertion `duplicate live dye published trial=1`. Windows source-only preflight was green. This is a meaningful live-semantics blocker, not grounds to weaken or delete the regression. The newer commits instead serialize the existing `LocalItemOnItemHandler` stale-slot precheck and `BankState.combineInventoryOne` under the **canonical BankState monitor**, while retaining the outbound writer's own monitor over packet admission and inventory mutation. The regression now logs grant counts, packet count, inventory revision, and both handler outcomes on failure so mismatches are diagnosable.

The tests exercise **256 same-writer racing actions** across selected-item order and bank open/closed, and **64 different-writer races** against one canonical BankState. Exactly one item conversion, one inventory mutation revision, and one queued postimage batch are permitted. Two separate writers are not a valid live account owner arrangement; the fixture only verifies that this local inventory boundary cannot double grant even if callers violate session-writer ownership. The underlying `BankState.combineInventoryOne` now also acquires that same bank monitor before taking the writer monitor, so direct package-local callers follow the same BankState → ServerPacketWriter lock order. An additional **32 competing full-queue cases** require all items, inventory revision and pre-existing queued bytes to remain unchanged despite two simultaneous dye attempts.

The existing live `BankState.combineInventoryOne` is now explicitly restricted to `3241 + 28824 -> 28807` (either input order). An arbitrary caller-provided output ID or alternative pair is rejected before packet publication or inventory mutation, even if the result item exists in the catalog. This does **not** authorize additional donor recipes, grant XP, attest a v308 UI, or replace the separate cross-World/JVM account lease.

These changes require new **exact-head Linux and Windows** successes. The failed earlier head remains recorded and must never be claimed as verified. The PR stays draft.

## R12 follow-up — classification regression correction and live dye wire parity

Exact-head Linux Gradle run at `a1aa5ba3` failed in `recipeSessionWriterGuardRegression` with `unverified occupied stackable inventory item`. The stricter stackable-only planner was correctly rejecting synthetic occupied item 200, which the session test simultaneously used as nonstackable in mixed-item fixtures. The test already declared a separate `ALL_STACKABLE_FIXTURE` for explicitly classifying its hypothetical all-stackable scenario, but six direct legacy `RecipeWorldOwnedAdapter.prepareStackable` test calls still passed only `STACKABLE`. Commit `790e7d8b` aligns those six synthetic calls with the all-stackable fixture; it **does not weaken** the planner's unknown-occupied-item fail-closed behavior, and it does not establish real item 200 stackability.

The existing **live** Doppelganger dye interaction has a new deterministic byte-level direction-parity check: for the same pet, dye stack and writer seed, both client selection orders (pet->dye and dye->pet) must queue identical canonical outbound packets. This runs for bank-open and bank-closed inventory presentation, and multiple remaining dye quantities. It asserts one dye/save semantic action and one queued postimage, without adding donor recipes.

**Exact-head status remains contingent on Linux Gradle and Windows source-only results.** Source-only packet byte parity cannot prove original client GUI acceptance, cross-World account exclusion or save durability.

### Bank overlay transition / prepared recipe linearization

Bank UI `open`, `close` and `clientClosed` now synchronize on the same canonical `BankState` monitor as source-only recipe preparation, sealed validation and inventory publication. The lock order remains `BankState -> ServerPacketWriter`; an overlay can no longer change bank-open status and bank-view revision mid-transaction without participating in that monitor. A mandatory `recipeBankSemanticAdapterRegression` extension races **128** open/close transitions against already prepared recipe publication (64 initially closed, 64 initially open). Every run requires exactly one of two outcomes: publication commits first and then the bank overlay changes, or the overlay changes first and the obsolete request is denied with no recipe packet. The test checks bank-view revision, mutation revision, final inventory, queued packet batches and stale replay denial.

This is **per-BankState JVM locking**, not a global named-account lease, persistent epoch or original-client network verification; it does not automatically serialize unrelated non-World accesses to other account objects.

## R13: existing live dye — recoverable queue admission and contention

The only currently admitted live item-on-item transformation remains the previously verified Doppelganger dye pair `3241 + 28824 -> 28807`. No additional recipe was registered. `BankState.combineInventoryOne` now uses `ServerPacketWriter.publishQueueOnlyRecoverablePacketIfIdle` for its three-or-one inventory container updates and commits the prepared canonical stacks **only after** full queue admission. A direct socket writer is denied before emitting partial bytes; a saturated queue retracts packet bytes and ISAAC state, returns a **nonfatal `REJECTED_COMBINE_QUEUE_RETRYABLE` handler result with no save intent**, and preserves inventory. Only direct socket writers (non-retractable) fail closed with IOException. Unlike the earlier general writer path, the recoverable queue is deliberately reusable after its unrelated pending batch drains, without reusing any already committed dye request.

Mandatory `doppelgangerDyeTransactionRegression` now additionally checks (for both bank-open and closed) direct-writer zero-byte denial, full-queue refusal followed by same-writer clean retry, and byte-for-byte equality of the retried packet/ISAAC stream to a clean first-attempt conversion. A further **32 two-thread controlled admission races** hold the outbound queue monitor to stall the first verified dye publisher inside `OutboundPacketQueue.tryOfferBatch`, then submit the second identical request. Before the queue is released, no item or revision may change; after admission exactly one save reason, inventory conversion and queued batch may exist.

A caller can retry after the outbound queue drains without disconnecting the session merely for transient backpressure; the full-queue regression asserts the result is a normal rejection rather than a thrown exception, and that the retried bytes remain byte-for-byte ISAAC-equivalent to a clean conversion.

This increases local packet/item failure-atomicity evidence for the existing dye interaction. It does **not** establish client ACK, socket delivery, crash durability, global account exclusion or permission to enable speculative donor recipes. CI PASS applies only when both workflows finish on the exact PR head.

### Independent dye widget and quantity reference

The live dye regression now constructs an **independent expected opcode-53 batch** using `BootstrapPackets.itemContainer53` and a separate equally seeded `ServerPacketWriter`, without querying the mutated live `BankState` to obtain expected slots. A closed bank must publish widget 3214; an opened bank must publish widget 3214, the bank container and its inventory mirror in the original order. It compares the entire queued ISAAC-obfuscated wire for dye quantities 2, 3 and 256 (testing the extended 255+ quantity encoding). This is locally reconstructed packet proof, not proof of client ACK.

The live dye handler returns `REJECTED_COMBINE_QUEUE_RETRYABLE` and **no save reason** when a recoverable queue admission is full, rather than propagating a fatal IOException. The client can try its already-known dye action again after the outbound queue drains. The same-writer retry is byte-exact against the clean conversion and leaves the pre-retry inventory unchanged. Terminal or direct socket writers remain separate fail-closed conditions; no new donor item semantics were admitted.
