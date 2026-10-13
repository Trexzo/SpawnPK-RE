# Chat 2 — R5 exact loadout-editor target recovery: external client candidate evidence

Date: 2026-10-13. Related: [server issue #1862](https://github.com/Trexzo/SpawnPK-RE/issues/1862). This is a **read-only source-forensics handoff**, not a recovered original-server behavior claim and not a new `LoadoutEditorService` implementation.

## Provenance and scope

External archived source: user-provided `SpawnPk-Decompiled-main (3).zip`, SHA-256
`2f9903b5eb9c89eb736f806e0cbd0aa468e4cd2408eb436ec3fd03d3094e4536`.

It contains decompiled candidate client source under `client/src/main/java/`. The live external GitHub URL was unavailable at previous intake. The three files inspected below are byte-identical to the older, already-intaken `SpawnPk-Decompiled-main (2).zip`, so **this is not independent new client corroboration**. This archive is not proven to correspond to the exact pinned v308 bytecode; exact-current parity remains a separate gate.

| Archived candidate path | SHA-256 of Java source | Evidence |
|---|---|---|
| `client/src/main/java/rs/interfaces/widgets/ItemLoadoutWidget.java` | `31b14d307913c467436a66fc5bddffb7c88837edf99db165a89e2d40b66e4da2` | Root, widgets, serializer |
| `client/src/main/java/rs/Client.java` | `cb92b385334c2715b3ee438b2d1365dbc5fcb05d83e7b9dabf94e4224447dc0e` | Click dispatcher around widget 33006 |
| `client/src/main/java/rs/gui/loadouts/dialogs/SpawnLoadoutAction.java` | `8d5b76e924298349c409a783c8dded6cda73e76ed637f86bd1005139dea1e692` | **Separate desktop-local loadout** workflow |

## Candidate source findings: what is actually supported

### In-game editor `ItemLoadoutWidget` (line-numbered within archived file)

- Lines 14–15 define a panel root `33000`.
- Lines 16–24 define inventory widget `33002`, **28 slots**, draggable.
- Lines 25–33 define equipment widget `33003`, **15 rendered slots**, draggable.
- Line 35 renders the text **“LMS Loadout”**; **a display label is not a trusted semantic loadout ID**.
- Line 36 defines Save widget `33006`. Line 38 defines “Set as default” widget `33009`.
- Lines 58–87 serialize the inventory to **28 inventory pairs** plus **14 specific equipment pairs** (including placeholder `0,0` positions), writing the strings `::cld1 <pairs>` and `::cld2 <pairs>` to separate opaque `Client` fields. **Neither string includes a target loadout ID or version.**

### Candidate `rs.Client` (around lines 6601–6633)

- The UI button-click path routes widget click through a general `outgoingPacket.a(185)` and `outgoingPacket.writeShort(n4)` at lines **6623–6624**.
- At lines **6631–6632**, `n4 == 33006` invokes `ItemLoadoutWidget.method3452()`, preparing the paired `cld1`/`cld2` strings.
- The code indicates that **widget 33006 is a distinct client-side Save intent** in addition to the two command payloads. It does **not** demonstrate where or whether the server establishes which loadout is being edited before that click, nor whether any asynchronous command queue ordering is invariant.
- `33009` appears in the widget declaration, but a direct `if (n4 == 33009)` case was not found in the candidate client-wide Java text search. It may use the generic widget path; no original server behavior or reset/default mutation can be asserted.

### Different workflow: desktop `SpawnLoadoutAction`

`rs/gui/loadouts/dialogs/SpawnLoadoutAction.java`, lines **35–84**, is a Swing `ActionListener`. It reads `LoadoutsPanel.getFolders().getSelectedLoadout()` locally (line 41), then builds the **different** `::item_loadout`, `::equip_loadout`, `::pet_loadout` and `::skills_loadout` command family and book/prayer options.

**Critical non-equivalence:** A desktop selected loadout **does not establish** the target identity or version of the in-game widget-33006 `cld1`/`cld2` editor. Do not copy this desktop selection into session editor authority.

## Current server boundary and why it is appropriately fail-closed

Checked against Chat 1 G21.173 source head `63076d8b1d5c95c4033f9e0dc1b7d8440f38b6aa`:

- `LoadoutEditorCompatibilityPairer`: bounded consecutive `cld1` → `cld2` pair; inventory 28, equipment 14.
- `LoadoutEditorSaveClientRequest`: one transport snapshot, with packet sequence metadata but **no `PlayerLoadoutId` or `PlayerLoadoutVersion`**.
- `LoadoutEditorWireSemanticAdapter`: maps wire into semantic inventory/equipment sections, explicitly refuses to choose a target ID/version.
- `LoadoutEditorService.saveSnapshot(...)`: already has correct external `PlayerLoadoutId` and `expectedCurrentVersion` requirements.
- `ClientLoadoutEditorSavePairingTest`: currently confirms the pair and **`LOADOUT_EDITOR_TARGET_ADAPTER_UNPROVEN`** diagnostic, with zero arbitrary semantic mutation.

Consequently, a default-loadout assumption, “LMS Loadout” label assumption or use of the desktop `LoadoutsPanel` selector would invent authority and undermine the current fail-closed protection.

## Handoff to Chat 3 (SpawnPK-Client) and Chat 1 (SpawnPK-RE)

**Chat 3: client source recovery task for issue #1862.** Verify each of these against **the exact pinned v308 JAR**, and independently against v309 if relevant:

1. Exact class/method identity and bytecode for the widget `33006` click path, including whether opcode **185** button press precedes/enables the paired command send.
2. The entire code path that opens panel/root **33000** and populates `33002` and `33003`, particularly how `33005` title derives and how target selection is signaled.
3. How widget **33009** (“Set as default”) dispatches and what the client can actually prove about that intent.
4. Wire and application UI updates that establish a *specific* loadout identity and revision before Save (if any), including alternative loadout editor roots.
5. Exact invalidation events: close, logout, panel replacement, select different editor target, player generation reuse.
6. Produce class/method/widget/opcode offsets and tested byte vectors; separate **exact-current-client** from archived-candidate clues.

**Chat 1/Chat 2 server:** When those specific target-selection facts become exact and independently testable, bind session-owned immutable editor context `(WorldPlayer identity, generation, loadout ID, expected version, root/UI epoch)` and only then forward R4 semantic adapter outputs to `LoadoutEditorService.saveSnapshot`. Reject missing, stale or replaced context. Do not implement provisional guessed target authority.

## Testable next steps without invention

- Write an **exact client-JAR class/method/command evidence ledger** for root 33000, Save 33006, default/reset 33009 and `cld1`/`cld2`; confirm client revision before acceptance.
- Add a negative regression that a widget-33006 `185` event plus a valid typed `cld1`/`cld2` pair still cannot mutate any loadout without a proven editor context.
- Establish the real UI root/snapshot lifetime before adding optimistic success tests.

**No changes to existing loadout data, policy, command routing or packet decoder are authorized by this note.** It closes a specific archaeology ambiguity; it does not close [issue #1862](https://github.com/Trexzo/SpawnPK-RE/issues/1862).
