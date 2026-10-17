# G21.192 — DEFAULT-OFF opt-in forced REAL World snapshot publication

## Verified parent
Core G21.191 [Draft #2596](https://github.com/Trexzo/SpawnPK-RE/pull/2596) exact SHA `142649b179f0e8ab949ae74d87fef7cdf52a559c` [Actions #38568504657](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38568504657) SUCCESS Java11 major55, **491/491 focused** and full Gradle 6m54s. G21.191 implements `LocalForcedAccountPostimagePrimitive` unused by actual World persistence, and three external-JVM cutpoints. Joint G21.191 [Draft #2597](https://github.com/Trexzo/SpawnPK-RE/pull/2597) exact SHA `39eadd182cf8f0183055d4449189f562ab6b36f8`, [Actions #38569080983](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38569080983) SUCCESS **498/498** focused. No original client/game rules validated.

## G21.192 code: a narrowly different real World save path

Add the DEFAULT-OFF property `-Dspk.local.experimentalForceWorldAccountPostimage=true` on `FilePlayerRepository.saveForWorld` ONLY. `FilePlayerRepository.save` legacy/manual/forensic stays unchanged, including its historical fixed temp leaf/non-atomic fallback semantics. Snapshot the feature flag at save entry; dynamic changes during active sessions are NOT supported.

For World saves opted in: retain the existing G21.170 exclusively allocated World-owned temp, G21.172 pinned inode evidence and SHA-256 sealed witness, G21.39 exclusive publication lock, World account negative checks, and all existing World save hooks. After `Properties.store`, reverify same NOFOLLOW World temp object, use FileChannel WRITE+NOFOLLOW `force(true)` and reverify its identity. Under existing World publication lock, recheck exact temp fingerprint; publish via **ATOMIC_MOVE required**, **NO non-atomic fallback** in opt-in mode. Then force the parent directory using FileChannel READ+NOFOLLOW `force(true)` and verify exact postimage SHA-256 and same NOFOLLOW file identity. Only AFTER all these succeed increment the private test-observable forced-world count. If directory force or postimage readback fails AFTER rename, do **not** claim successful forced completion: throw IOException; existing persistence layer records an uncertain/failed save. Do not automatically reverse rename or clear any account review marker. The existing default and raw direct save code paths must remain unchanged.

This implementation intentionally adds its own exact World publication helper rather than silently replacing the unrelated G21.191 standalone temp-writing helper, to preserve existing World lock/hook/temp identity semantics. It does NOT make all filesystem write paths participate in one global crash-safe account epoch; uncooperative/RAW writers and destructive path replacement may still bypass.

## Real TCP regression

`G21192CoreSocketForcedWorldPostimageTest` sets opt-in only in its own disposable test JVM/root. It creates a first-time named account using genuine loopback LocalSession synthetic revision317/ISAAC, mutates World-owned extension A_FORCED, disconnects and waits for true final SaveTicket. Assert real World writer reported forced postimage and repository reads exact A_FORCED. Instantiate a **fresh World and repository** on same account, confirm it actually hydrates A_FORCED, mutate B_FORCED, disconnect and verify forced final publication again and independent repository readback. Confirm ordinary real World saves do not synthesize/retire a G21.188 ACTIVE marker. No external server/GUI compatibility claim.

**Verification required:** exact-head hosted Java11 major55, `G21192_REAL_WORLD_FORCE_PASS`, all **492/492** unique focused and full Gradle SUCCESS. This is NOT certified until actual CI passes. The forced World counter is only diagnostic: it does NOT constitute an account epoch, crash-recoverable receipt or payment/grant authority. Actual OS user-space process-kill and power-loss cutpoints around newly wired World force were NOT tested in this milestone.

## Critical nonclaims

- G21.190's ONE-SHOT active review marker is NEVER automatically retired, even if a G21.192 World snapshot was forced: a verified forced postimage digest alone is insufficient to resolve true session generation, cross-process incomplete final save, marker lifecycle or external writer races.
- No full crash-durable session lifecycle, automatic account reuse, user recovery, force across network filesystems, hostile unlink/remap/symlink/hardlink defenses, POSIX durable directory-force portability, genuine machine power-loss proof, or raw legacy writer exclusion.
- G21.187 killed-JVM stale BOOTSTRAP remains a DEFAULT-OFF negative. No settlement/replay/grants/cleanup/rollover/pruning/unlink/durable session release/ACK; no original v308/v309 GUI/client or historical SpawnPK reward policy.
- Keep core [#2540](https://github.com/Trexzo/SpawnPK-RE/issues/2540) and [#2559](https://github.com/Trexzo/SpawnPK-RE/issues/2559) OPEN. Chat 2 gameplay and Chat 3 client untouched. R25 #1847 and G21.87/G21.88 frozen. Draft/unmerged; no main release.
