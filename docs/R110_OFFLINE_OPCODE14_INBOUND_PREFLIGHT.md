# R110 — exact opcode-14 item-on-player inbound route and read-only preflight

**DRAFT / STACKED ON VERIFIED R109 #3154 / OFFLINE TEST ONLY / UNMERGED / UNDEPLOYED**

## Critical finding: the actual inbound route is deliberately fail-closed

The exact-current client emits item-on-player **C2S opcode 14**:
`GenericInteractionPacketDecoder` recognizes an 8-byte packet
containing selected widget, viewer-local player index, selected item
ID and selected inventory slot, with its pinned client transforms.
It produces `GenericInteractionEvent.Family.ITEM_ON_PLAYER`.

The real `LocalPendingRequestDispatcher` passes typed
`GenericInteractionClientRequest` events to
`LocalGenericInteractionHandler.handle(event)`.
The `ITEM_ON_PLAYER` branch currently returns
`V5185_ITEM_ON_PLAYER ... result=DECODED_FAIL_CLOSED` with
unknown production semantic authority. **It does not call
`ContentRegistry.dispatchItemOnPlayer`.** R110 must preserve this
live fail-closed behavior, not turn uncertain packet semantics into
a player-state mutation.

The separate `ContentRegistry.dispatchItemOnPlayer(itemId,target)`
method and its registered callbacks are callable *internally* by
World-thread fixtures, as demonstrated by
`ContentItemOnPlayerRegistrationTest`. That direct invocation does
not prove actual inbound packet routing, selection validation or
authorization. The R109 negative controls also show that a leased,
writerless semantic target facade is not uniformly actor-lock guarded.

## Offline preflight only — never an authorization receipt

`R110OfflineItemOnPlayerPreflight.inspectForTest` accepts a decoded
event, real isolated World, source player with expected generation,
and the actual `Player81WorldSync.Context` for this viewer.

It refuses requests unless all conditions hold:
1. The caller is on the real WorldPulse. Event is exactly
   opcode 14 with `ITEM_ON_PLAYER` family.
2. The selected widget is the locally confirmed normal inventory
   container (3214); selected slot lies in 0–27 and item ID is valid
   positive input. These are still *untrusted client claims*.
3. The viewer's exact source player object and generation match
   current ownership. The viewer-local target index is not an
   `EntityId`; `viewer.resolveVisible(index)` must resolve a
   currently visible, non-self player through the real generation-
   fenced Player81 source.
4. Both source and target are currently registered and locked
   under the existing R79 offline dual-player World mutation helper.
   While holding both locks, confirm the viewer mapping remains the
   exact target and source's **authoritative**
   `BankState.inventorySlotSnapshot(slot)` is occupied, positive,
   and contains the selected item ID.
5. Return a diagnostic-only immutable review containing the
   actor IDs, generations, selected slot, widget and viewer index.
   The result contains no raw `WorldPlayer`, bank or packet writer;
   it is not an executable permit.

**No item is consumed and no target action is executed.**
The snapshot becomes stale after the lock is released, so callers
must never treat a successful preflight as a durable or
cross-thread item-on-player authorization.

## Executable real-route regression

The `R110OfflineItemOnPlayerPreflightTest` first runs the full
R109 suite (inheriting R103–R108). Its new isolated World:

- Registers source and target players, registers an item-on-player
  content handler for the exact selected item, creates real
  `Player81WorldSync` source/target contexts and obtains a
  viewer-local index by transforming the proven packet81 idle
  fixture. Source inventory is populated with a known item.
- Generates the *actual eight-byte opcode-14 payload*, decodes it
  using the production `GenericInteractionPacketDecoder`, and
  confirms the typed selected widget/slot/item/index fields.
- Runs `LocalGenericInteractionHandler(world.content()).handle`
  on the real World actor context and proves it still logs
  `DECODED_FAIL_CLOSED`; the registered content callback is
  **not invoked**. The separate read-only R110 preflight succeeds
  without changing either player's item count or run energy.
- Rejects off-World use, wrong selected item, empty/wrong slot,
  incorrect widget, out-of-range slot, forged/out-of-view
  player index and wrong source generation.
- Separately invokes the internal content registry handler
  manually as a positive control. Confirms it runs once while
  another inbound generic item-on-player attempt **remains**
  fail-closed and does not call it again.
- Unregisters the target; the previously visible index is no
  longer authorized. Preflight refuses the stale target
  and no state mutation occurs.

`server/tools/audit_item_on_player_inbound_route.py` is a
read-only lexical CI fence checking the exact opcode14 decoder,
typed event fields, generic request routing and
`ITEM_ON_PLAYER: DECODED_FAIL_CLOSED` branch.
It also verifies the live generic handler does not call
`ContentRegistry.dispatchItemOnPlayer`. This is a source
guard, not a complete call-graph or bytecode verifier.

## Next required proof before any production promotion

A real semantic item-on-player implementation would additionally
need authenticated session identity, a validated selected-item
slot and count under World locks, target visibility and expected
generation across asynchronous approach, distance/consent
semantics, any item consumption and rollback/packet publication
atomicity, and explicit target capabilities for each content
mutator. R109's synthetic pair permit is not sufficient.

The current test intentionally **does not** enable live
item-on-player actions. It changes no public
`PluginContext`, `ContentPlayer`,
`ContentItemOnPlayerContext`, or production packet/session
wiring. The unresolved leased ContentPlayer target-energy
and raw captured-reference bypasses remain documented.

The dedicated Linux/Windows Java11 workflow and independent full
server build/Windows atomic-persistence suite must pass at the
exact R110 commit before calling this milestone fully verified.

**DRAFT / UNMERGED / UNDEPLOYED.**
