# G21.20 — native Mailbox DELETE 32184, guarded refresh

Based on certified G21.19 `33dd678dcce1a6038a8691a12cb04deeb98c3ccd`, hosted run 37773582779 SUCCESS / 318 Java 11 focused regressions.

## Authority

- **EXACT_CURRENT_CLIENT v308:** client emits C2S185 widget **32184** for Mailbox `Delete this message` and understands S2C250 subtype31 inbox CLEAR / APPEND / FINALIZE for the native root 32019.
- **CUSTOM_LOCALLAB_G2120 policy:** only delete a selected immutable envelope whose attachment state is `EMPTY` or `CLAIMED` after a separate **external completed** settlement. Refuse `UNCLAIMED`; no item movement, deposit, claim, reward expiry or reconstructed original server deletion policy.

## World-owned mutation and wire order

A live native/rootless Mailbox scope requires the correct current WorldPlayer registration, generation, bound inbox and immutable selected message identity. For an exact widget32184 action:

1. Validate typed opcode/provenance/selected row and current `ClaimState`.
2. Build a complete hypothetical snapshot **without** the selected envelope; validate surviving subjects, read states, duplicate IDs and max **35 visible rows** using the extracted zero-wire `MailboxInboxProjection.validateRows`.
3. Delete only the selected matching envelope, mark `mailboxSnapshotKnown` (including the explicit empty-tombstone case), and rebind the active inbox, retiring the previous selection.
4. Publish the exact recovered inbox S2C250/31 operations: CLEAR(0), 0–35 APPEND(1), FINALIZE(5), within one `ServerPacketWriter` batch.

Selection, ownership or surviving-row validation failure **before deletion** leaves domain and wire unchanged. A transport failure during the final batch flush **after deletion** cannot restore the envelope; the known-empty tombstone and surviving rows remain durable. Exact C2S185 Refresh widget32185 republishes the stored state. This is not a transaction across domain state and network I/O, and a transport that has partially sent bytes may require UI refresh.

Bank/inventory widget32178/32181 remain inert and never settle attachments. Stale/recycled message IDs, foreign players, retired roots (including C2S130) and obsolete registration generations are denied.

## Verification

`G2120MailboxLiveDeleteIntegrationTest` asserts exact inbox bytes, empty and externally claimed deletion, unclaimed protection, stale-selection replay rejection, malformed surviving-row preflight, byte-failure durable deletion/recovery, empty tombstone persistence, account isolation and generation fences. Source/CI verification is not a manual native-v308 gameplay acceptance. Frozen R25 PR #1847 remains untouched.
