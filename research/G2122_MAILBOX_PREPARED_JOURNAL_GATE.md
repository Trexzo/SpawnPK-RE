# G21.22 — prepared Mailbox claim intent (not item settlement)

## Certified predecessor

G21.21 @ `260ad6edfa8fac2b2d9c1d706dfbffbf0ec339f4`, hosted workflow `37777957896` attempt 2 SUCCESS (320 focused Java11). C2S185 inventory deposit widget32181 is source-routed to a **zero-mutation** complete inventory feasibility preview.

## Repository audit and durability limitation

- `PlayerRepository.save(PlayerSnapshot)` owns a complete account record; `PlayerSnapshotCodec.capture` copies all inventory and Mailbox state under the WorldPlayer mutation lock. The same snapshot **can** represent both components.
- `WorldPlayerPersistence` captures on the World execution side but queues asynchronous repository writes. Its completion future tracks repository return, not a stronger storage durability contract.
- `FilePlayerRepository.save` writes a temporary `.tmp` Properties file, closes its stream, and attempts `Files.move(..., ATOMIC_MOVE)`. If unsupported, it **falls back to non-atomic REPLACE_EXISTING**; it neither guarantees fsync of file contents nor parent-directory metadata.
- Therefore a completed snapshot write is NOT yet demonstrated as a crash-durable exactly-once Mailbox CLAIMED+inventory grant transaction. A successful save callback cannot be used to retroactively guarantee that packets/credits are atomically settled, especially if a crash or replay interrupts the workflow.
- No stable original SpawnPK reward economics, transactional settlement policy, claim authorization timing or banking semantics are inferred from the v308 client. Unknown stackability remains a hard veto per G21.21.

## New foundation

`MailboxPreparedClaimJournal` defines a bounded, immutable, account-owned **PREPARED_NO_GRANT** record. For an actually UNCLAIMED immutable message with a G21.21-eligible all-attachments preview, it copies both canonical 28-slot preimage and full proposed postimage, ordered attachment identities and stable account/message ID into a deterministic SHA-256 replay key.

The digest detects inconsistent saved data and conflicting prepared previews; it is **not** a malicious tamper-proof MAC or authentication credential. The strict namespace codec refuses unknown keys, state changes, unsupported versions, noncanonical integer/slot encodings, item-ID/quantity errors, hash mismatch and over-budget input. It holds only `PREPARED_NO_GRANT`, not an undocumented COMMITTED/ACK state.

`stageOnly` writes a matching proposal into the already-certified `PlayerSnapshotExtensionState` namespace `extension.mailbox-claim-intent.*`. Replaying the same preimage/key is inert; a different in-flight key or changed inventory preimage is rejected. This is **opt-in foundation API**, not called by live widget32181 and not a true durable write barrier.

Because that namespace participates in the same `PlayerSnapshotCodec` as inventory and Mailbox, repository snapshots can round-trip the proposed intent next to both domain components. On load it is inspected only; it **never grants items, never marks an envelope CLAIMED, and never executes a prepared record**. A power-loss after staging but before account persistence can lose the proposal safely, without losing or duplicating a reward.

## Safety gate for future G21.23

Do not enable any mutating claim until there is a demonstrably crash-durable, atomic account snapshot write or recoverable transactional journal including inventory credit and Mailbox CLAIMED, with a stable idempotent transaction key, failure injection at every crash boundary, stale-generation cancellation, rollback/roll-forward, and safe SaveTicket ordering. Ensure no stale checkpoint can overwrite a committed claim. Only after that can exact v308 S2C53/S2C250 claim/refresh packets be published as recovery-aware client presentation. Even then, the network flush is not rollbackable.

The pinned native v308 client must separately undergo real in-game acceptance. The frozen R25 promotion PR #1847 remains untouched.
