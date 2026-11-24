# R112 — conservative inventory mutation epochs and write-surface audit

**DRAFT / STACKED ON VERIFIED R111 #3156 / OFFLINE TEST ONLY / UNMERGED / UNDEPLOYED**

## Proven gap from R111

The R111 real WorldCommandInbox test showed that a queued opcode14
item-on-player intent can see the same item ID and quantity after
the originally selected stack is removed and an identical stack
is created in the same slot. Item+quantity preflights alone
cannot detect this **ABA** transition.

The actual C2S opcode14 `LocalGenericInteractionHandler` route
remains `DECODED_FAIL_CLOSED`; R112 does not enable it.

## Mutation-surface inventory

The current `BankState` is 905 lines before R112, with state
updates distributed across multiple operations. The conservative
inventory audit covers **20 known entrypoints**:

- Item insertion/removal: `spawnItem`,
  `consumeInventoryAmountSemantic`, `consumeInventoryOne`,
  `consumeInventoryAll`, `addInventoryOne`,
  `addInventoryOnePreferred`, `addInventoryAmount`
- Banking: `depositInventory`, `withdrawAmount`,
  `depositAmount`
- Equipment and cosmetics: `equipFromInventory`,
  `unequipToInventory`, `equipCosmeticFromInventory`,
  `unequipCosmeticToInventory`
- Inventory transformations: `transformInventoryOne`,
  `splitInventoryOne`, `combineInventoryOne`
- Normal/bank inventory dragging: `applyDrag`, including
  `swap` / `insertMove` helpers
- Trade and account restoration: `restoreTradeInventory`
  and `restoreAccountState`

Read-only presentation, item/slot lookups and bank-only tab
changes are not independently instrumented. There are additional
indirect entrypoints that delegate to these guarded operations
(`apply`, `applyAmount`, `wieldFromInventory`).
`restoreAccountState` includes a full inventory
`System.arraycopy` path.

`server/tools/audit_r112_inventory_epoch_write_surface.py`
checks the 20 covered method-entry markers and inventories
canonical `inventory[slot]` direct write sites. It also
checks account array replacement and drag helpers.
This is an intentionally **lexical** audit, not a complete Java
dataflow/type-system guarantee.

## Conservative opt-in model — not precise per-slot epochs

`BankState.enableOfflineInventoryEpochForTest()` can be called
once on an isolated test state. It installs a monotonically
increasing **whole-inventory** revision. Each of the 20 known
inventory-mutating methods bumps this revision **before attempting**
the operation. The immutable `InventorySlotSnapshot` includes
`offlineMutationEpoch`, which is zero when auditing is off.

Why invalidate the whole inventory? Without proving every
affected slot across banking, drag, equip and restore paths, a
fine-grained per-slot counter could give false assurances of
unchanged inventory authority. R112 **conservatively invalidates
all selected slots** for any covered mutator call, including
rejected attempts or a trade restore that recreates identical
values. False positives are deliberately accepted in this
offline safety experiment.

The audit is **off by default**. It does not alter existing
item moves, account persistence, client packets or live item
interaction routing, other than a dormant branch in the
existing mutator methods.

`R110OfflineItemOnPlayerPreflight.Review` now carries the
optional mutation epoch under the existing source/target
dual-actor World lock. `R112OfflineEpochPinnedItemOnPlayerIntent`
wraps the existing read-only R111 source/target pin, refusing
use unless auditing was enabled at capture and the epoch is
identical on the final queued recheck. It remains
**diagnostic-only** and cannot consume an item or authorize
a content callback.

## Real WorldCommandInbox tests

`R112OfflineInventoryEpochAuditTest` inherits the full
R103–R111 suite, then uses a new isolated World and genuine
World command queue. It verifies:

- Strict pinning denies an inventory snapshot with no audit
  enabled; once enabled, a read-only normal inventory packet
  leaves the revision unchanged.
- Source consumes the selected rune and **reintroduces the
  same ID+quantity in the same slot** in a prior FIFO command.
  R111's weaker snapshot would pass, but R112 rejects the
  incremented inventory epoch.
- Two inventory drag swaps restore the original item/slot
  values; the covered `applyDrag` invalidates the old pin.
- Trade checkpoint restoration conservatively invalidates
  the pin even when the visible inventory is unchanged.
- Deposit-all and withdrawal through real bank APIs
  invalidate the pin as expected.
- The live item-on-player generic route still refuses the
  opcode14 event, and no source-to-target item mutation
  was enabled.

## Critical remaining gap (explicit negative control)

`BankState.inventoryAt(slot)` exposes a **mutable
package-private `Stack`**. Other internal Java code can
retain this reference and change `qty` without calling a
guarded `BankState` mutator. The R112 test deliberately
increments and then restores `Stack.qty` through this
raw alias and verifies the epoch remains unchanged.

That is a **known bypass of the experimental model**, not
a safe capability or an indication that production
stack authority has been secured.

The source audit also cannot prove all future or indirect
mutation paths through raw aliases, Java reflection or
native code. A real sound implementation would need
to encapsulate the mutable stack arrays/fields, remove raw
writable aliases, centralize every inventory mutation under
a single exclusive World actor transaction, and enforce
a monotonic mutation epoch with tests for every public
and internal entry point. Only then could a
fine-grained per-slot optimization be considered.

No production item-on-player request processing, public
`ContentPlayer`/plugin API, login/session or socket
semantics are changed. The unresolved R107 direct
reference and R109 leased-target mutator gaps also remain
intentionally documented.

**Verification requirement:** exact-head dedicated Linux
and Windows Java11 R112 tests and lexical audit, inherited
R111/R110 and full server plus Windows atomic persistence.

**DRAFT / UNMERGED / UNDEPLOYED.**
