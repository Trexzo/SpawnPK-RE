# G21.169 — NOFOLLOW preflight in actual cooperating World publisher (NO AUTHORITY)

**Certified parent:** G21.168 [Draft PR #2516](https://github.com/Trexzo/SpawnPK-RE/pull/2516), SHA `2415b9a0aa90f6f9aba1ecfa578b83896e9d7dcb`, [Actions #38401922610](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38401922610) SUCCESS Java11 468/468 focused/full Gradle. Issue #2517.

## Actual production writer boundary

`MailboxAccountPublicationCoordinator.withExclusivePublication` (G21.39) coordinates genuine guarded World account replacement and cooperating review-marker publication using a sibling persistent OS FileLock. Its original `FileChannel.open(CREATE,WRITE)` followed symlinks and did not preflight symbolic lock ancestors or nonregular lock leaves. This is **not** the optional G21.73 bounded forensic path, which already has NOFOLLOW protections.

G21.169 changes ONLY G21.39's ordinary cooperator: retain account-scoped JVM lease, original parent `createDirectories`, lock duration and indefinite wait; immediately after creating the existing parent, run the reused G21.77 NOFOLLOW ancestry and lock-leaf preflight. Open FileChannel with `LinkOption.NOFOLLOW_LINKS` and re-run both validations after obtaining a valid OS FileLock but *before* invoking caller publication. Symlink final lock leaf, symlinked ancestor or nonregular leaf now veto without executing the caller. No automatic removal or alteration of unsafe evidence.

`G21169MailboxWorldPublicationNoFollowLockIntegrationTest` verifies a genuine `FilePlayerRepository.saveForWorld` ordinary legacy snapshot succeeds on normal paths. Real test filesystem cases then supply a directory lock leaf (always refused), symlink leaf to unrelated victim (actual guarded World save must refuse, no account writes/victim mutation/temp leak), and symlink directory ancestor (callback must never run), followed by successful independent World save. Tests report POSIX symlink support separately; unsupported host is not advertised as a proved symlink attack case. Zero JVM leases. Java11 focused manifest 468→469 and dedicated Gradle regression.

## Important non-claims

These checks prevent following unsafe **observed** lock paths before a genuine cooperating World callback. No portable atomic binding of already-open lock descriptor to pathname under every race, no full RAW/default/legacy/future uncooperative writer exclusion, no changes to direct `FilePlayerRepository.save` or unguarded `StrictDurablePlayerSnapshotWriter.saveStrict`, and no independent historical provenance/trusted custody or atomic crash/power-loss epoch. Existing World state-admission rules, negative markers, G21.165-168 cutover observations and frozen R25 #1847/G21.87/88 untouched. No cleanup, rollover, pruning, unlink, grant, replay, session, release, ACK.

**Acceptance:** exact-head hosted Java11 major55, 469/469 focused + full Gradle PASS. Draft/unmerged until certified.
