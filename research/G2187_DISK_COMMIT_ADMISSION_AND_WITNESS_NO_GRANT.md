# G21.87 — disk COMMIT record in World admission and portable restart witness (NO GRANT)

Parent: certified G21.86 `141b6bfda06311cd12306c33448380b7240c9b1c`, [Actions #38047634047](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38047634047), 386 Java11 focused tests. Tracking [issue #2353](https://github.com/Trexzo/SpawnPK-RE/issues/2353).

## Actual live-session admission fence

The G21.86 write-once disk COMMIT record now takes a **negative-only veto role** inside `FilePlayerRepository.loadForWorldSession`. While the same G21.59 publication lock is held, the loader checks the pinned COMMIT sidecar name **before** session account decoding and again **after** the G21.58 adversarial read hook and negative review checks. Any present record — valid, invalid or symlink — denies admission, even if an uncooperative raw writer rewrote the terminal account back to a valid G21.22 PREPARED preimage and its G21.83 intent journal matches. Metadata I/O failures propagate. An absent record leaves existing legacy and PREPARED compatibility unchanged. The old four negative markers and G21.84 PREPARED-journal checks retain their authority.

The COMMIT record is not a session-admission token, reward-credit proof or cleanup instruction; it cannot be consumed or removed by this change. Session reads do not acquire a second account lock.

## G21.87 forensic continuity

The G21.85 double-pass portable forensic witness now includes **seven** physically inspected objects: the pinned account, four original negative sidecars, G21.83 PREPARED journal, and G21.86 disk COMMIT record. Both journals have an independent 1024-byte bound (original negative sidecars remain 4096 bytes; account is 64 MiB), with NOFOLLOW metadata, byte SHA-256, file-key/object census, supported ctime and pinned ancestry across two complete passes. The COMMIT inspector's non-authorizing semantic status is included while the same bounded account lock is held; **no nested lock**. A too-large COMMIT file fails before downstream account decoding. Version is `G2187`, deliberately rejecting old `G2172` / `G2185` witness tokens.

## Real integration regression

`G2187MailboxCommitSessionWitnessIntegrationTest` uses a real strict terminal file receipt followed by G21.86 COMMIT publication, independent repository reloads and actual `WorldPlayerPersistence.load`, then injects test-only raw PREPARED rollback to check admission still refuses a disk-COMMITed account. Covers ordinary/journaled PREPARED compatibility, genuine COMMIT observed in portable witness, corrupt/oversized/symlink COMMIT file, foreign marker appearing during guarded read, same-byte new-inode COMMIT replacement between the two forensic witness passes, negative-marker precedence, unchanged original UNCLAIMED inventory and no leaked temp files/JVM account leases.

**Still not authorized:** end-to-end positive settlement COMMIT, automatic replay, live World reward grant, release, client ACK or account admission based on either journal. The record and tokens are unauthenticated forensic files, not proofs against an arbitrary adversarial disk writer or power loss. Frozen R25 PR #1847 remains unchanged.

Focused Java11 manifest 386 → **387** unique tests plus standalone `g2187MailboxCommitSessionWitnessRegression`. Draft/unmerged pending exact-head hosted build.
