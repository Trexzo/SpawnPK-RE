# G21.167 — ctime-based in-place lock-leaf mutation detection (NO AUTHORITY)

**Certified parent:** G21.166 [Draft PR #2512](https://github.com/Trexzo/SpawnPK-RE/pull/2512), exact `1861ff8fb73184587598161c26dd22f63ba07f7c`, [Actions #38395037387](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38395037387) Java 11 466/466 focused and full Gradle SUCCESS. Issue #2513.

## Source-confirmed limitation

G21.166 observes NOFOLLOW regular lock-leaf fileKey, creationTime, file size and mtime across G21.165's deny-only cutover window. A raw actor can rewrite the SAME inode with exactly the SAME byte count and restore mtime, so all four fields might match even though the path's content changed while the cooperating OS lock was held. Existing G21.75 `optionalRestartChangeTime(Path)` reads `unix:ctime` where supported. It does not claim availability on all Java/Windows providers.

G21.167 uses a `CutoverLockLeafCensus` holding G21.166 pathname identity plus optional ctime, sampled after G21.73's lock acquisition. The original G21.166 `finally` now rechecks the same identity, compares optional ctime and rechecks pathname identity again. Any changed ctime gives a checked `G21.167 CUTOVER_LOCK_CTIME_CHANGED_UNSAFE_NO_AUTHORITY`. Null/null is permitted but explicitly means the filesystem cannot supply this evidence; no claim of excluding such edits is made. The existing seven-file G21.165 witness and original G21.162 negative denial remain unchanged.

## Real file regression

New `G21167MailboxCutoverLockLeafCtimeIntegrationTest` writes a regular lock file to a fixed size, records mtime and POSIX ctime, then uses a preexisting test-only in-lock callback to wait past coarse timestamp resolution, change the same inode's bytes, and restore its mtime. On a provider with unix:ctime the ctime difference MUST refuse; raw bytes are preserved, account bytes are unchanged, original fileKey/size/creationTime/mtime match, and all JVM leases end. If unix:ctime is unsupported, the test explicitly reports this and asserts only inherited NO AUTHORITY.

**Acceptance target:** new Java11 focused regression manifest 466→467, dedicated `g21167MailboxCutoverLockLeafCtimeRegression` task, bytecode major55 and exact-head hosted full Gradle success. Keep Draft/unmerged pending proof.

## Explicit non-claims

Metadata observation is not a continuously held descriptor identity proof, a complete raw/default/legacy/future-writer exclusion guarantee, cross-platform in-place ABA proof, independent signer custody/historical provenance, atomic multi-file crash/power-loss epoch, or a grant/replay/session/release/ACK/cleanup/rollover/pruning/unlink capability. Production World/restart/settlement callers, scanner, writers and frozen R25 #1847/G21.87/88 untouched.
