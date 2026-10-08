# G21.27 — opt-in PREPARED account write reservation, no settlement

**Certified predecessor:** G21.26 `27552f71523dbd50a789cccac614ac85a37b3d55`, hosted workflow 37790492234 SUCCESS / 325 focused Java11.

## Why old-capture fencing was insufficient

G21.24 rejects deferred account saves captured **before** an opt-in strict PREPARED-only barrier, but deliberately allows newer gameplay checkpoints. A future reward commit cannot store inventory credit+Mailbox CLAIMED durably if a newer capture of **unreconciled live state** can overwrite that account record. Therefore we need a bounded per-account write exclusion period before attempting any live settlement.

## Narrow G21.27 foundation

`WorldPlayerPersistence.reservePreparedAccount(owner, generation, G21.25 proposal)` is off-World, opt-in, and currently does **no disk I/O or reward settlement**. It requires the current owner/generation, exact account identity, immutable selected Mailbox message, G21.22 PREPARED intent and a re-derived exact 28-slot inventory+Mailbox hypothetical postimage. Admission is serialized under the existing persistence `io` lock, capped at **32 simultaneous distinct account reservations**, and refuses duplicate reservations and competing final-save reservations.

While a token remains active:
- `enqueueSaveImmediate` and `enqueueSaveWithBackpressure` reject both **older** and **newer** captured saves for its account, with failed SaveTickets. Foreign accounts may still save through the same worker.
- `checkpointDue` skips fresh captures for the reserved account. An older coalesced checkpoint already queued is independently stopped at the worker `write()` guard, including a snapshot allocated after the previous G21.24 cutoff.
- `write()` rechecks the live reservation map immediately before calling `repository.save`, so queued ordinary/final/checkpoint writes cannot silently bypass the account exclusion solely because they entered the FIFO earlier.
- New conflicting final-save reservations and opt-in G21.24 PREPARED strict-file barriers are rejected. Read-only G21.26 FIFO `load()` remains allowed and does not grant items.
- The reservation has **no automatic close/release**; `cancelIfStillUnclaimed()` removes it only when the same World owner/generation and original immutable message are still current and the **whole account snapshot** exactly matches the original PREPARED/UNCLAIMED preimage, including the original intent key. Repeated successful cancellation returns false; any changed inventory, Mailbox, unrelated gameplay state, logout/re-registration or recycled envelope **keeps** the exclusion held.
- A reserved account that loses its registration cannot auto-release into an unverified new generation. This is deliberately fail-closed and may require an explicit future recovery mechanism before ordinary saves resume for that account. This is **not** an acceptable general-purpose always-on save lock.

## Critical limits: no in-flight quiescence receipt

The new token can be admitted while an already-running `repository.save` is past its prewrite check. That operation **may still finish** after reservation admission. G21.27 DOES NOT claim to cancel it, prove the disk is quiescent or provide a durable commit receipt. The reservation is only a *conflicting-new-write fence*; future work must queue/drain a bounded worker acknowledgment after admission, then validate the exact on-disk account record before initiating a real strict transaction.

This milestone cannot grant, deposit, mint, claim, acknowledge CLAIMED, publish a C2S185 response, infer original SpawnPK reward economics, or enable Mailbox inventory widget32181. It does not replace the older FilePlayerRepository or guarantee OS/hardware crash durability. Existing Mailbox G21.19–G21.26 behavior is left unchanged.

## Regression

`G2127MailboxAccountWriteReservationIntegrationTest` uses an intentionally blocked real account write, acquires the opt-in token without misrepresenting it as quiescent, denies a duplicate reservation, rejects older/newer captured saves and autosave snapshot capture, refuses new final-save/strict barrier admission, verifies an unrelated account can still save, checks exact-preimage explicit release then ordinary checkpoint recovery, rejects changed inventory on cancellation, and proves registration retirement leaves the account fenced. Full Java11 focused suite must pass on the exact PR SHA.

This is a source/CI proof, **not** actual v308 game session runtime acceptance. Frozen R25 promotion PR #1847 remains untouched.
