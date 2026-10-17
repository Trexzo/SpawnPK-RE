# Chat 1 G21.191 — forced account postimage publication primitive (NOT WIRED)

## Certified starting point

- Core G21.190 [Draft #2593](https://github.com/Trexzo/SpawnPK-RE/pull/2593), exact SHA `617c985c26f5205e2b7dd8def3122fef8e809a0d`, [Actions #38565096554](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38565096554) SUCCESS Java11 major55, **490/490** unique focused/full Gradle 8m20s.
- Latest joint Chat 1+Chat 2 G21.190/G18.5.2 [Draft #2594](https://github.com/Trexzo/SpawnPK-RE/pull/2594), SHA `4211f07162c2bb494db23fa625856d0f711fb3f6`, [Actions #38565757908](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38565757908) SUCCESS Java11 major55, **497/497** focused/full Gradle 7m50s.
- G21.190 proves one healthy starting JVM can force ACTIVE marker before World membership, lose unsaved mutation on process kill, then a fresh JVM denies stale BOOTSTRAP login. The marker is **NEVER retired even after healthy final save**; opt-in session is one-shot and deliberately not deployable as normal gameplay. Default LocalLab still does NOT create an intent.

## Missing durable final snapshot step

Current `FilePlayerRepository.saveForWorld` serializes `Properties.store` to an owned temporary file and uses `Files.move(...ATOMIC_MOVE)` with a non-atomic fallback. It does NOT explicitly force serialized temp bytes and the parent directory. A healthy `SaveTicket.completion` cannot safely be used to justify retiring the ACTIVE marker. Existing direct/forensic `save()` and Mailbox-native authority remain deliberately unchanged.

G21.191 supplies one **standalone, not wired to account save** primitive, `LocalForcedAccountPostimagePrimitive`:
1. Validate bounded nonempty postimage bytes, normalized account path, NOFOLLOW ancestor and regular existing account leaf.
2. Exclusively allocate an owned unique sibling temporary file. Capture its initial regular-file `fileKey`, write the postimage through NOFOLLOW `FileChannel`, call `force(true)`, then read and verify the exact length and SHA-256 digest.
3. Revalidate temporary inode and exact bytes. Require `Files.move(...REPLACE_EXISTING,ATOMIC_MOVE)`; **NO non-atomic fallback**. If unsupported, fail without a receipt, retaining an uncertain temp for offline review.
4. Force the parent directory via `FileChannel.force(true)`. If unavailable/failed, **fail closed** with no receipt. Read back exact final account postimage independently, require regular NOFOLLOW leaf, length, full raw-byte equality and digest before returning a receipt containing exact file path/digest/length and file/directory forced indicators.
5. On any failure, never automatically delete/unlink a foreign temp, ACTIVE marker or account; this does not include a durable transaction journal, snapshot epoch, ownership fencing, collision-proof hostile path semantics or marker retirement.

The stage callback exists ONLY to create deterministic test cutpoints: `TEMP_FILE_FORCED`, `ATOMIC_REPLACE_BEFORE_DIR_FORCE`, `DIR_FORCED_BEFORE_VERIFY`. The primitive is otherwise unused.

## Genuine separate-JVM cutpoint tests

`G21191CoreForcedPostimagePublicationCutpointTest`:
- Establish an old baseline account using the same stand-alone file+directory-forced helper, in a disposable isolated directory.
- Spawn a **separate JVM** and pause immediately after forcing a unique temporary account file but BEFORE rename. Force-kill child; old account remains byte-identical; that account's own forced temp remains for review, and a separate fresh JVM reads old bytes. This cutpoint has no receipt.
- Spawn a new child for a separate account and pause AFTER atomic rename but BEFORE directory force; kill child. New account is visible on this OS and separately readable by a fresh JVM, but **NO directory-forced receipt exists**. This does NOT prove new bytes would survive machine power loss.
- Spawn a healthy child for a third account, complete temp+directory force and exact readback, obtain a completed receipt. Fresh JVM independently reads exact NEW bytes.
- Assert no review-intent marker was automatically created/cleared in these cases and no unrelated JVM publication/watch leases were leaked. Child timeouts bounded and private test process only; no real user accounts or server sessions.
- Verify any leftover temp by **its specific account filename**, never by counting all other scenario leftovers.

Expected: `G21191_FORCED_PUBLICATION_PASS standalonePrimitiveOnly=true atomicRenameFallback=false preRenameCutpointTested=true postRenamePreDirectoryForceCutpointTested=true fullyForcedReadbackTested=true machinePowerLossTested=false actualWorldSaveWired=false accountEpochReceipt=false reviewMarkerRetirement=false uncooperativeRawWriterExcluded=false`.

## Strict nonclaims and next steps

This is **source-side plumbing**, not a complete account persistence fix. The code is not wired into `FilePlayerRepository.saveForWorld`, `WorldPlayerPersistence`, `LocalSession` or review-intent retirement. It does not prove crash-/power-loss-safe data in production, exact generation/session epoch, raw/legacy writer exclusion, hostile symlink/hardlink/rename defense, multi-host lock agreement or recovered original SpawnPK client/server reward semantics. A receipt from this helper is not enough to retire markers until it is integrated with real World snapshots and verified cross-process epoch ordering at each crash cutpoint. Generic durable design [#2540](https://github.com/Trexzo/SpawnPK-RE/issues/2540) and global owner [#2559](https://github.com/Trexzo/SpawnPK-RE/issues/2559) remain OPEN.

New helper/test, Gradle focused **490→491 unique** and dedicated `g21191CoreForcedAccountPostimageCutpointRegression`. Require **fresh exact-head hosted** Java11 bytecode major55, G21.191 marker, **491/491** unique focused, full Gradle SUCCESS before calling certified. Draft/unmerged. No settlement/grants/replay/cleanup/rollover/pruning/unlink/durable ACK, R25 #1847/G21.87/G21.88 frozen, Chat 2 gameplay + Chat 3 client untouched.
