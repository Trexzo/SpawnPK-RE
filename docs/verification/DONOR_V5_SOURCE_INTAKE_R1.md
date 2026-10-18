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
