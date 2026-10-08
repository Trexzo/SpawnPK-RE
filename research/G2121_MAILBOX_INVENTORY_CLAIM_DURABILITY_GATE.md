# G21.21 — Mailbox inventory claim preflight, NOT settlement

**Certified parent:** G21.20 `5d6abe04a53d0195bb0d966892d881a9f1859a4c`, hosted workflow 37775919743 SUCCESS / 319 Java 11 focused.

## Exact v308 facts

The pinned v308 client sends C2S185 widget 32181 for Mailbox **Deposit to inventory**. Its attachment display is S2C53 widget32175 and its claim-state controller uses subtype31 operation4. This proves the widget and presentation format, **not** the original server's item-grant transaction.

## Audited server facilities and missing authority

- `WorldPlayer.bank()` holds the canonical **28-slot** inventory in `BankState`; `inventorySlotSnapshot(i)` returns independent slot values.
- `BankState.prepareAddInventoryAmount` and `replaceInventorySemantic` establish basic stack/capacity and prevalidated mutation shapes, but the known committed behavior is not an atomic crash-durable transaction covering `MailboxRewardDeliveryService` *and* inventory.
- `ItemDefinitionRepository.stackabilityEvidence(id)` distinguishes verified current config, established stackables, semantic ammo categories, and the `UNKNOWN_DEFAULT_NONSTACKABLE` fallback. Unknown fallback is unsafe for a real reward grant and **must be rejected**.
- `WorldPlayer.mutationLock()` provides serialized in-memory ownership, and `PlayerSnapshotCodec.capture` captures inventory and Mailbox under that lock. `WorldPlayerPersistence` uses a **separate bounded asynchronous persistence worker**, so a successful in-memory mutation alone does **not** prove account-durable settlement before acknowledging CLAIMED.
- `MailboxRewardDeliveryService.acknowledgeAttachmentSettlement` changes only the Mailbox claim state; its contract explicitly requires successful *external* item settlement first and cannot itself implement an atomic grant.

Thus **G21.21 does not enable a mutating claim**. This is a safety decision, not a claim that the exact client lacks the action.

## Implemented CUSTOM_LOCALLAB preflight

After a selected immutable Mailbox row and exact-current typed C2S185 widget32181, the live LocalSession → World-owner path calls `WorldMailboxPresentationSession.previewSelectedInventoryClaimFromWidget`. It verifies player/generation and selected object identity, then `MailboxInventoryClaimPreflight.inspect` simulates the *entire* attachment bundle against a copy of all 28 inventory slots.

Rejections include: missing/claimed/empty selected state; nonpositive or over-int attachment amounts; unknown/out-of-range item IDs; unsupported/unknown stackability; insufficient slots for every nonstackable unit; insufficient slots for stackable types; and stack merge overflow. Repeated attachment IDs are accumulated consistently with canonical inventory stacking semantics. An otherwise eligible result is explicitly named `PREFLIGHT_ONLY_NOT_SETTLED`, **never** a receipt or permission to mint items.

No inventory mutation, Mailbox claim acknowledgement, item serialization, network grant packet or account save occurs from this widget. It remains consumed so no later generic handler can misinterpret the button as an inventory action.

## Future mutation gate (not authorized now)

A later implementation must demonstrate a single account-durable, all-or-nothing inventory-plus-Mailbox commit, including rollback/failure injection, idempotent transaction identity, deduplication across disconnect/restart, proof of durability before ACK, safe recovery of partially persisted records, and atomic client wire reconciliation. Without that proof, keep the grant disabled. Bank deposit widget32178 remains independent and disabled.

Hosted CI verifies source and synthetic integration, not interactive visual acceptance in the original v308 client. Frozen promotion PR #1847 remains untouched.
