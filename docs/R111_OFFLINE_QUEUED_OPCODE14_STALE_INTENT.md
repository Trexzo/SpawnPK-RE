# R111 — queued opcode14 intent pinning, stale inventory and target rechecks

**DRAFT / STACKED ON VERIFIED R110 #3155 / OFFLINE TEST ONLY / UNMERGED / UNDEPLOYED**

## Precise risk

R110 showed that a single WorldPulse preflight can match an opcode14
packet's untrusted selected widget, item ID, slot and viewer-local
target index to authoritative World actors and current inventory.
That read-only snapshot ceases to prove anything once the World
actor locks have been released. The actual inbound opcode14 route
correctly returns `DECODED_FAIL_CLOSED`, and R111 never enables it.

R111 verifies two different boundaries on the real World command
queue: **the source inventory can change** and **the target can
unregister/re-register** after an initial item-on-player request
has been decoded and captured, but before a queued callback
would process the same request. A stale event must never be
treated as a verified mutation permission.

## R111 package-private test-only pinned intent

`R111OfflinePinnedItemOnPlayerIntent` retains only the immutable
original decoded `GenericInteractionEvent`, World reference,
source/target `EntityId` and generations, and the selected
item quantity captured during R110's real two-player locked
snapshot. It never returns a raw `WorldPlayer` or bank.

- Capture requires an actual WorldPulse callback **under the
  source actor's mutation lock**. The underlying R110
  preflight resolves the viewer index and acquires both actor
  locks to validate ownership and authoritative item slot.
- Queued recheck requires the *same World/source/generation*
  and source mutation lock; it reruns R110's dual-actor
  preflight and requires the **same target EntityId and
  generation** and **same selected-item quantity**.
- If source item/slot/quantity or current target visibility
  has changed, recheck rejects without executing an action.
- No item consumption, target mutation, publication, client
  semantic dispatch, authorization or durable receipt is
  performed here.

The new `selectedQuantity` field in the existing R110
`Review` is captured **inside** R110's two-player lock,
not from an unlocked inventory read.

## Executable real WorldCommandInbox FIFO tests

`R111OfflineQueuedItemOnPlayerIntentTest` inherits R103–R110,
creates actual isolated World + registered source and target,
sets up the real viewer/target `Player81WorldSync` contexts
and a selected two-unit item stack, constructs and decodes
the pinned fixed-eight-byte opcode14 event, then starts the
real WorldPulse.

The source World actor captures a valid pinned intent and
proves the immediate recheck succeeds without mutation.
Then separate source-owned `World.submit` operations enter
the actual `WorldCommandInbox` in FIFO order:

1. The earlier command consumes one unit of the selected
   item through the exact-slot semantic inventory API.
   The queued R111 recheck is rejected because the quantity
   changed from 2 to 1.
2. A subsequent command consumes the last unit, emptying
   that slot. The queued recheck is refused by R110 because
   the claimed selected item is no longer there.
3. The item is reintroduced under World actor ownership and
   a new pin is captured for quantity 1. A queued command
   **removes the item and reinserts an identical item
   into the same slot** before the next recheck. This
   deliberately passes the ID-and-quantity snapshot check.
   The test must observe this as a **known ABA blind spot**:
   it proves that the current representation has no per-slot
   mutation epoch and cannot guarantee the identity of an
   individual selected stack across queueing.
4. Finally, the original target unregisters and re-registers
   under a later generation before the queued recheck.
   The old viewer mapping is no longer current; recheck
   refuses the stale target.

Positive controls verify the original opcode14 still
returns `DECODED_FAIL_CLOSED`, inventory and target state
changes occurred **only in intentionally queued fixture
actions**, and the preflight itself neither removes items
nor calls a content handler.

## What this does NOT prove

A quantity+item ID match is not a durable inventory identity.
**Same-item/same-quantity replacement (ABA) still passes.**
Even an inventory mutation counter alone is not safe unless
all item mutations that affect this slot increment a properly
scoped revision and the revision is checked under the World
actor mutation lock at the final authorized action.

This test also does not establish a real packet admission
receipt, replay nonce, client intent authorization, approach
range, item consumption/rollback atomicity, or protection
against untrusted Java plugins retaining raw player state.
R107 and R109 direct-reference/leased-target gaps remain
known and intentionally reproduced by inherited tests.
The actual opcode14 handler stays fail-closed; no public
`PluginContext`, `ContentPlayer` or
`ContentItemOnPlayerContext` API is modified.

Before any actual item-on-player behavior is promoted,
production would need an authoritative inventory revision
or equivalent slot identity that cannot be bypassed by
any mutation path; an exact target ID+generation pin at
validated admission; consistent locked validation at
execution; and explicitly authorized source-to-target
semantics. Those designs are **not** approved by R111.

Dedicated Java11 Linux and Windows CI and independent full
server/Windows atomic persistence checks must pass on
the exact commit before marking R111 fully verified.

**DRAFT / UNMERGED / UNDEPLOYED.**
