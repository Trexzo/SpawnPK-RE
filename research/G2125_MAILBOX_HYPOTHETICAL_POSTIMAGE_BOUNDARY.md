# G21.25 — immutable hypothetical Mailbox inventory+CLAIMED account postimage

**Parent certified gameplay frontier:** G21.24 exact SHA `e458ef1f58a3fc0646c13a017d9c0ab60043c62e`, hosted workflow 37783934572 SUCCESS, 323 focused Java11.

## The narrow capability

G21.21 validates all current inventory attachment IDs, verified stackability, amounts and 28-slot capacity without granting items. G21.22 stores a versioned **PREPARED_NO_GRANT** intent with immutable inventory pre/postimages and attachment fingerprint. G21.23 provides an opt-in strict file writer with file/directory force and required atomic replacement; G21.24 runs that **PREPARED-only** writer inside the existing FIFO account-save worker and rejects older delayed captures.

G21.25 defines **`MailboxSettlementPostimagePlanner`**, a read-only composer of a hypothetical *complete* account record. It requires the current WorldPlayer registration generation, the same selected in-memory message object, its UNCLAIMED status, the exactly matching staged G21.22 intent and freshly recomputed G21.21 inventory feasibility.

Under the player mutation lock it captures the immutable account PREPARED preimage, loads that snapshot into a **fresh detached WorldPlayer**, applies the exact full proposed 28-slot inventory **only to the clone**, and calls the Mailbox claim-state acknowledgement **only on the clone**. Capturing that clone yields one versioned `PlayerSnapshot` with *both* hypothetical inventory credit and the selected Mailbox message CLAIMED. Other Mailbox messages, read flags, gameplay state and existing snapshot extensions remain intact. The planner strictly canonicalizes the entire hypothetical snapshot and refuses any stale/invalid input before returning the two immutable snapshots plus the G21.22 idempotency key.

This method has **no repository writer, disk mutation, live WorldPlayer inventory mutation, live Mailbox claim mutation, socket packet emission or client C2S handler**. No bank widget32178 integration is included. The proposal has the explicit authority `CUSTOM_LOCALLAB_G2125_HYPOTHETICAL_POSTIMAGE_NO_GRANT`.

## Replay classification is observation, not transaction recovery

The proposal can classify any candidate on-disk snapshot by first fully validating/normalizing it and comparing exact canonical account values:

- `EXACT_PREPARED_PREIMAGE`: no proposed item credit and selected Mailbox envelope remains UNCLAIMED.
- `EXACT_HYPOTHETICAL_POSTIMAGE`: a byte-for-byte-equivalent *hypothetical* complete postimage (not independently proven durable or an authorized grant).
- `DIVERGENT_REQUIRES_MANUAL_RECONCILIATION`: any different/malformed/foreign snapshot, even if it appears to contain some equivalent items.

It **does not** replay the intent, grant items, roll back a disk file, mark a live message CLAIMED, or consider a postimage classification alone a successful durability receipt. A partial or ambiguous persistence failure cannot be healed by guessing whether an attachment was credited; the proper response remains fail closed until durable account transaction recovery is certified.

## Remaining critical veto before any live claim

A complete immutable hypothetical postimage is not yet a *World-coordinated* crash-durable transaction. A later implementation must atomically reconcile the live in-memory inventory+Mailbox state with a successfully fsynced account postimage, prevent future auto-checkpoints and generation turnover from replacing it with stale live state, prove exactly-once recovery of ambiguous after-rename failures, and implement safe client inventory+Mailbox refresh without treating socket publication as rollbackable. G21.24 PREPARED barrier is not permission for a live CREDIT/CLAIMED commit. No recovered original SpawnPK reward transaction policy or interactive native-v308 acceptance is claimed.

## Permanent acceptance

`G2125MailboxImmutablePostimageIntegrationTest` independently reloads hypothetical and prepared account snapshots and checks both inventory and Mailbox state, other-envelope preservation, zero live mutation, deterministic repeated planning, stale-account/selection/inventory/generation/replayed/claimed/unprepared vetoes, exact replay classification and conservative divergence rejection.

Freeze R25 promotion PR #1847; do not change or merge it.
