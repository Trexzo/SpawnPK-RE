# G21.23 — strict synchronous account file write barrier (opt-in, not a claim transaction)

## Certified parent and current limitation

G21.22 exact head `79a63f3b0d99655ee64f84d7b59a04e98cb34dba`, workflow 37780856946 SUCCESS / 321 focused Java11. Mailbox C2S185 widget32181 still runs only G21.21's non-mutating inventory preview. G21.22 adds a canonical `PREPARED_NO_GRANT` snapshot extension, with no claim acknowledgement or item credit.

The existing `WorldPlayerPersistence` asynchronously queues account snapshots and checkpoints. `FilePlayerRepository.save` writes to a temp file, tries `ATOMIC_MOVE`, and falls back to a non-atomic `REPLACE_EXISTING` move without explicit file/directory fsync. It is **not** a suitable receipt for crash-durable, exactly-once inventory+Mailbox settlement.

## New isolated strict-file primitive

`StrictDurablePlayerSnapshotWriter` accepts an immutable version-2 `PlayerSnapshot` with the existing `FilePlayerRepository.PathResolver`. Its opt-in, synchronous, per-instance serialized save:

1. Validates the whole snapshot and creates a unique temporary file in the same account directory.
2. Writes legacy-compatible Properties with `FileChannel`, then `channel.force(true)`.
3. Requires `Files.move(temp, target, REPLACE_EXISTING, ATOMIC_MOVE)` with **NO fallback**. If unsupported, throws before replacing the original account file.
4. Opens the **containing directory** for reading and requires `FileChannel.force(true)` to complete. If the platform refuses directory force, the save fails closed. A successful `Receipt` is emitted only after this barrier returns.

This expresses strict Java/NIO filesystem API behavior, not a guarantee against faulty hardware, power-loss controller caching, or lost media. It does not prove a crash-durable settlement transaction by itself.

### Failure semantics

- BEFORE_TEMP_CREATE, AFTER_TEMP_CREATE, AFTER_SERIALIZE, BEFORE_FILE_FORCE and BEFORE_ATOMIC_REPLACE failures must preserve the old account file and remove the uncommitted temp.
- Failures after a successful atomic move (**BEFORE_DIRECTORY_FORCE** or **AFTER_DIRECTORY_FORCE**) throw `UnconfirmedCommitException`, without a receipt; the file may already contain the new state. No dishonest rollback is claimed.
- The injected regression exercises both sides, checks the temp cleanup and complete inventory/Mailbox/PREPARED snapshot file round-trip, and confirms restored PREPARED intent never mints items.
- Filesystems that do not support atomic move and directory force cannot use this strict barrier successfully. This is a safety property, not an invitation to silently fall back.

## Why this does NOT enable native item claiming

`WorldPlayerPersistence` and its already-queued autosave/checkpoint tasks continue to use the old `PlayerRepository.save` path. They are **not coordinated** by this separately exposed strict writer. A stale checkpoint may overwrite a newer strict save; no World-owned transaction currently guarantees atomic durable update + live state switch, retry and generation fencing. The PREPARED marker is not an authorizing token for mutation.

The strict writer is not registered as an automatic `PlayerRepository` replacement, does not change `LocalMailboxRootlessSession` or `WorldMailboxPresentationSession`, and never modifies `BankState` or calls `acknowledgeAttachmentSettlement`. There is **no C2S185 widget32181 item credit or CLAIMED packet** in G21.23. Further work must prove ordered checkpoint quiescence, one exact durable postimage containing both inventory credit and CLAIMED, idempotent recovery of ambiguous commit errors, and client refresh reconciliation before enabling any payout. Bank widget32178 remains out of scope.

Original SpawnPK reward transaction and ordering remain unknown; every new authority is `CUSTOM_LOCALLAB`. Human native-v308 gameplay acceptance remains independent. Frozen R25 promotion PR #1847 untouched.
