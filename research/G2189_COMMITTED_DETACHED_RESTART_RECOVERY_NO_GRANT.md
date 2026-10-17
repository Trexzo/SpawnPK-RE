# G21.89 — disk COMMIT-backed detached restart hydration (NO GRANT)

**Parent:** G21.88 certified head \`7860aef927f24151a21758a6f1deffb88d191bbf\`, [Actions #38051154838](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38051154838) SUCCESS, 388 unique Java11 focused. [Issue #2357](https://github.com/Trexzo/SpawnPK-RE/issues/2357).

This milestone adds an **actual cross-restart account restoration operation**, \`MailboxCommittedDetachedRestartRecovery.recoverDetached\`, that materializes a G21.86 COMMIT-record-backed terminal account onto a **fresh, unregistered detached WorldPlayer** without needing pre-crash World owner, G21.25 Proposal or G21.66 Receipt.

### Strict recovery preconditions
- Bounded G21.73 cooperating account lock, exact canonical resolver account.
- G21.86 persisted write-once disk COMMIT must classify \`DISK_COMMIT_MATCH_NO_LIVE_APPLY\`, including G21.83 identity journal, G21.64 coherent terminal account, all matching transaction hashes, and no G21.32/47/48 negative markers.
- Account/journal/COMMIT physical file identities and metadata are captured using NOFOLLOW before hydration and checked again after, along with repeated disk COMMIT classification and exact canonical snapshot hash.
- Only a detached WorldPlayer is hydrated through the existing G21.68 \`PlayerSnapshotCodec.applyValidated\` primitive; the in-memory decoded Mailbox must *already be CLAIMED*, and every inventory slot must equal the recorded G21.22 proposed postimage.
- Capture the detached state back to a PlayerSnapshot and reject even one differing serialized value, then return only the canonical snapshot and inert metadata—**not the detached WorldPlayer**—to prevent accidentally treating the detached player as a World admission handle.

### Real-file tests
\`G2189MailboxCommittedDetachedRecoveryIntegrationTest\`: write genuine G21.83 PREPARED journal, confirm G21.66 strict terminal account and G21.86 disk COMMIT; reopen five independent recovery/hydration sessions and re-apply each recovered snapshot to a new detached WorldPlayer, asserting exact 25 coins, not 50/75/100/125, and CLAIMED remains CLAIMED; repeat via original restorer. Verify original live owner remains **UNCLAIMED** with empty inventory and G21.87 still blocks actual World session admission. Reject missing COMMIT, raw stale PREPARED rollback after COMMIT, missing journal, negative review marker, corrupted/oversized/symlink COMMIT, unknown account and invalid account name. Ensure disk account/journal/COMMIT raw bytes were not changed by successful recoveries, no temp or lock leaks.

### Contract limitations
This reconstructs the **already-persisted** terminal inventory; it does not perform reward grant, attachment replay, normal World admission, automatic live owner mutation or release/ACK. It is subject to G21.39 cooperating locks, filesystem inode and checksum assumptions; not a hardware crash/power-loss simulation. G21.87/88 negative load/write fences remain fully active, including for same COMMIT. A subsequent milestone must establish World-owned live adoption, generation ownership, idempotent future saves and reservation release before a real positive claim is permissible.

Focused Java11 manifest 388 -> **389** unique tests; standalone \`g2189MailboxCommittedDetachedRecoveryRegression\`. Keep PR Draft/unmerged until exact-head hosted full CI. Frozen R25 #1847 unchanged.
