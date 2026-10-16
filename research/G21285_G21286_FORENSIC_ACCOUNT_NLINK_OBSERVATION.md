# G21.285/286 — read-only inode hardlink count in operator forensic reports

**Exact parent:** [Draft #2826](https://github.com/Trexzo/SpawnPK-RE/pull/2826) `b3ffcdab190e7d94cc599873a9c1a4bee8ba7796`, 685 focused candidate on G21.284 core + G19.5.8 Chat2. This includes G21281–84 read-only two-pass account hashing and parent NOFOLLOW revalidation, plus Chat2 actual installed sequential progression-stage trace G1957/58. All gameplay code remains unchanged in the new core tranche.

## Actual operator evidence gap

The G21.279 forensic inspector reports a regular account file's SHA256 and exact known negative sidecar names but lacks an observed filesystem hardlink count. If `selected.properties` and a foreign pathname both name the SAME inode, SHA256/fileKey/size/mtime can all be stable while a foreign path can mutate account contents. This cannot produce recovery/admission authority, but an operator should be able to **see a negative alias witness** in the report.

**G21.285 production:** observe `unix:nlink` through NOFOLLOW at entry, after the deterministic first-digest seam, and immediately before the report. If >1, return `accountHardlinks=MULTIPLE_ACCOUNT_LINKS_NEGATIVE`, not a clean/authorized status. If exactly 1, report `ONE_OBSERVED_NOT_AUTHORITY`; unsupported view is `UNAVAILABLE_UNPROVEN` (never assumed one), absent file `ABSENT`; impossible count <1 causes IOException. A count that changes during the bounded read fails closed with `G21.285 ACCOUNT_HARDLINK_COUNT_CHANGED_DURING_READ`. Existing double SHA256, bounded NOFOLLOW ancestry and five negative sidecar observations are retained. No account file alias is unlinked, no marker is changed/retired, and no OS lock is created.

**G21.285 real regression:** actual selected file and second hardlink pathname; exact read-only inspection reports multiple-link negative and preserves both names/bytes. After fixture-owned alias removal, another alias created inside the deterministic between-hash callback must invalidate the in-progress report rather than silently return single observed. Fixture alone deletes its alias and subsequent selected scan reports one-observed-NOT-authority.

**G21.286 control:** foreign OTHER named account has a real second inode link during selected account scan. Selected unaliased account remains `ONE_OBSERVED_NOT_AUTHORITY` and its SHA256/bytes are preserved; foreign account remains multiple-link negative; no global failure or invented review markers. On filesystems with unsupported `unix:nlink`, tests require honest `UNAVAILABLE_UNPROVEN` and do not claim support. Hosted Linux runner should run the full hardlink fixtures.

**Manifest:** 685+2 = **687** distinct focused tests. One actual production Java extension to `LocalAccountForensicEvidence`, two regression classes, two JavaExec Gradle gates. No changes to real RAW/World account writer policy, session ownership, LocalLab MonsterSpawner, rewards, combat or packet code.

## Limits

The nlink probe is **observational**: a foreign hardlink may be inserted after the last check or removed/reintroduced between samples. Single observed link count cannot establish exclusive writer ownership, original SpawnPK account state, cross-process epoch or crash-safe final-save ACK. Unsupported platform is not safe/positive. No positive replay, login, marker removal, grant, repair or recovery. Require exact-head hosted **687/687**, G21285/86 + G21281–84 + G1957/58, Java11 major55 and complete Gradle. Parent branches must be independently verified. Draft/unmerged; main untouched.
