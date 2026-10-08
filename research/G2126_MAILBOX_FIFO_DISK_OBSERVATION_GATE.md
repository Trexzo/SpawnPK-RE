# G21.26 — FIFO Mailbox disk observation (NO CREDIT, NO CLAIM)

## Certified predecessor

G21.25 exact source head `23a515dd30466ea86795e0edef2dc915f78b8034`, hosted run 37787294136 SUCCESS / 324 Java11 focused. Its immutable proposal contains both hypothetical inventory credit and Mailbox CLAIMED, but neither is applied to the active WorldPlayer, written to disk, nor authorized by a durability receipt.

## Existing file-read ordering authority

The existing `WorldPlayerPersistence.load(String)` accepts work onto the **same single bounded FIFO executor** as normal saves, checkpoint drains and G21.24 opt-in strict PREPARED-only saves. Therefore, a read admitted after an in-flight ordinary save and a queued PREPARED barrier runs *after both*. It is unnecessary and unsafe to open a second direct repository read that bypasses this worker.

G21.26 adds `MailboxDiskPostimageObserver.observe(world, owner, generation, proposal)`, callable off World execution context. Before and after the FIFO read, it checks:
- The identical, currently registered `WorldPlayer` account and generation in the same World registry.
- Recomputed current Mailbox selected envelope, strict attachment inventory preflight, G21.22 prepared-key and the **entire** immutable G21.25 before/after snapshots. No stale/recycled identity or changed inventory can inherit earlier evidence.
- The candidate disk record is loaded and normalized through the certified `PlayerSnapshotCodec` and classified using G21.25's **exact** full account map equality, not an item-count heuristic.

## Observations are NOT a commit receipt

The returned bounded `Observation.state` has four cases:

1. `MISSING_ACCOUNT_RECORD`: no account snapshot currently readable at the FIFO turn.
2. `EXACT_PREPARED_ACCOUNT`: identical full PREPARED snapshot, with no proposed item credit.
3. `EXACT_HYPOTHETICAL_ACCOUNT`: identical full hypothetical inventory+CLAIMED postimage **seen in file contents**.
4. `DIVERGENT_ACCOUNT`: anything else, including unrelated gameplay changes, forged extensions, mismatched IDs or malformed records.

In *all* four cases, `durabilityReceipt=false` and `grantAuthorized=false`. In particular, a G21.23 strict write that replaced the file but failed before directory sync can leave the hypothetical account file visible. A subsequent exact read **cannot convert that unconfirmed write into a successful crash-durable grant**. An I/O read exception must remain an exception, not a positive result.

G21.26 never stages, grants, deposits, commits, acknowledges Mailbox CLAIMED, sends a success packet, replays a journal, or mutates a saved file. The live v308 C2S185 widget32181 still calls G21.21 non-mutating feasibility preview.

## Host tests and limitations

`G2126MailboxDiskReconciliationIntegrationTest` verifies the FIFO order by blocking an earlier ordinary account save, admitting a strict PREPARED writer, and queuing the observer behind both. It covers absent file, repeated exact PREPARED reads, pre-rename failure (old prepared retained), after-rename uncertain failure (hypothetical file visible **without** receipt), divergent persisted map, injected repository I/O failure, unrelated account, mutated inventory, recycled same-ID envelope and stale logout/re-registration. All live item/claim state must remain unchanged.

This does **not** solve mismatching live state after a real credit, later autosaves from legitimately newer gameplay, crash-durable power-loss recovery, or human v308 client acceptance. Those remain an explicit fail-closed gate before any real inventory reward claiming. No recovered original SpawnPK reward economics/server settlement policy is claimed. Frozen R25 PR #1847 is untouched.
