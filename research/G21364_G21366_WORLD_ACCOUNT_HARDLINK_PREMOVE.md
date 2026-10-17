# G21.364–366 — selected account hardlink continuity before forced World replacement

Exact cumulative parent [#2921](https://github.com/Trexzo/SpawnPK-RE/pull/2921), commit `159bdc8802a1209b008d76cc4193893b98d00655`, 826-test candidate. Last independently certified combined [#2916](https://github.com/Trexzo/SpawnPK-RE/pull/2916) exact `6b3b1d367a5191d7c467b3df641b691ee36f5537` 819/819.

**Source risk:** forced World opt-in writer protects ACTIVE nlink prior to move and selected account physical inode+SHA as an optimistic preimage; neither alone rejects the selected account inode gaining additional hardlink names. The optional generic `LocalJvmAccountSessionLease.requireNonHardlinkedAccount` gate may be off while the forced negative witness writer is on; the latter would reject the new postimage alias only after replacement, too late to preserve the old disk account alias. The forced World path must not assume an external hardlink means exclusive file ownership.

**Production:** re-use existing `LocalFinalSaveNegativeWitness.requireSingleObservedLink` NOFOLLOW `unix:nlink==1` check, with new `requireSingleObservedLinkIfPresent` helper allowing honest first-time account absence. Under strict forced World + negative witness opt-ins, check selected account inode before World temp allocation and after the existing G21.360 account preimage comparison, immediately before `replaceSnapshotTemp`. Unsupported `unix:nlink` remains UNKNOWN/negative. Existing opted-out historical writer semantics retained.

**Real regression gates:**
- G21364 preexisting selected account alias, same physical inode/bytes: deny before World temp and retain old account.
- G21365 inject new hardlink to exact selected inode at actual inside-publication callback: unchanged fileKey, size, bytes; deny before move, retain account.
- G21366 unrelated account hardlink alias in same folder: allow selected World save; preserve unrelated account alias.
- Retain G21363 first-time missing-account positive control from parent.

All 829 focused entries and new G21.293 critical retention family required to pass exact-head hosted Gradle, Java11 major55/full build. Not an atomic exclusion of uncooperative writers, durable owner epoch, final-save ACK, automatic marker cleanup, or replay policy. All changes draft, main untouched.
