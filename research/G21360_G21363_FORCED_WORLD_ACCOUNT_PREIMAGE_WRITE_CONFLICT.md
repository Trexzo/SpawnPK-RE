# G21.360–363: forced World account preimage write-conflict veto (826 focused)

Parent core [#2918](https://github.com/Trexzo/SpawnPK-RE/pull/2918), exact `fe3cfa2a4f78a0582f66a9d222b6cf0952fe67b2` (822 candidate), includes 819 certified cumulative [#2916](https://github.com/Trexzo/SpawnPK-RE/pull/2916). No 822 or 826 CI certification is pre-claimed.

## Source gap

The `FilePlayerRepository.saveForWorld` opt-in forced World path verified its OWN serialized temp digest and ACTIVE marker inode/bytes/hardlinks. It did NOT pin the existing account's disk identity and contents from before serialization to the publication boundary. An uncooperative RAW file writer can modify same-inode account bytes (even restoring size/mtime), or replace the account pathname with a new inode containing identical bytes, while World is preparing its temp; without a preimage check, the older World snapshot can silently replace this external update. The existing RAW opt-in `LocalOptInRawTemp` already has a bounded NOFOLLOW preimage capture+verify implementation.

## Production

Reuse `LocalOptInRawTemp.captureAccountPreimage` before World temp allocation (after initial guarded negative-admission checks), and `requireAccountPreimageUnchanged` within the cooperating publication lock immediately before `replaceSnapshotTemp`. Enable only when both strict opt-in forced World postimage and negative final-save review witness are configured. Capture includes absent first-time account, exact fileKey, size, creation/modified times and bounded physical SHA-256. Wrap drift as `G21.360 WORLD_ACCOUNT_PREIMAGE_CHANGED_BEFORE_MOVE`. No rollback of external updates; fail closed and do not invent final acknowledgment.

## Real regression coverage

- G21360: outside same-inode in-place byte edit during actual inside-publication callback, restored length and mtime, preserve externally changed disk bytes and inode, fail before move.
- G21361: outside same-bytes new-inode replacement during actual inside-publication callback, preserve replacement disk bytes and inode, fail before move.
- G21362: unrelated foreign account changes during selected forced World publication; exact selected save completes, foreign changes intact.
- G21363: exact selected account absent at first World forced save; unchanged absent preimage permits initial create, original ACTIVE marker intact.

Manifest 822→826 distinct classes with retention anchors and individual JavaExec tasks. Require exact-head 826/826 Java11 major55/full Gradle CI. Still not a durable epoch or hostile-writer atomic exclusion: an uncooperative actor may write after the last observation. Neither preimage checks nor final review witness authorize automatic marker retirement, player recovery, account replay, or reward acknowledgment. All PRs draft; main remains untouched.
