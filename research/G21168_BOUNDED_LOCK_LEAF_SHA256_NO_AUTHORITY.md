# G21.168 — bounded lock-leaf SHA-256 witness (NO AUTHORITY)

Certified parent G21.167 [Draft PR #2514](https://github.com/Trexzo/SpawnPK-RE/pull/2514), exact `adc0abc704b3a6c216d0038daecc66b97a4d5816`, [Actions #38399640895](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38399640895) success, Java 11 major55, 467/467 focused and full Gradle. Issue #2515.

## Targeted observation

G21.166/G21.167's optional deny-only, lock-held cutover rechecks the **path** of its G21.39 sibling advisory lock (fileKey, creationTime, size, mtime, optional POSIX `unix:ctime`). Windows/non-POSIX provider may omit `ctime`, and an external RAW same-inode same-size rewrite can restore mtime. G21.168 adds a second, independent **content evidence** dimension: bounded, NOFOLLOW SHA-256 of the lock **path** before/after cutover, using G21.72's existing `restartFingerprintPart`, with a **4096-byte cap**. Any physical byte mismatch produces checked `G21.168 CUTOVER_LOCK_BYTES_CHANGED_UNSAFE_NO_AUTHORITY`. Failed/beyond-cap physical read produces `G21.168 CUTOVER_LOCK_CONTENT_UNSAFE_NO_AUTHORITY`. The G21.167 ctime veto runs first when ctime is available; original G21.166 lock-path metadata and G21.165 seven-account-object witness remain in place.

A package-visible **read-only** helper `FilePlayerRepository.cutoverLockLeafContentWitness` wraps the exact same production primitive; its return is forensic only. No signer admission, file write, durable marker, settlement or recovery action is added.

## Deterministic real-file tests

New `G21168MailboxCutoverLockLeafDigestIntegrationTest` owns real temp accounts/lock and executes a direct raw same-inode same-size edit with original `mtime` restored **under the genuine G21.73 cooperating OS lock**. It verifies the two SHA witnesses disagree using the SAME method used by production **independently of `unix:ctime`**; integrated deny-only cutover catches same raw edit (via SHA or ctime where available); oversize 4097-byte attacker lock leaf vetoes before unbounded hashing and is left unmodified; healthy legacy account still denies, original account bytes remain unchanged, no JVM leases survive. No claim of an actual Windows test.

Acceptance: Java 11 bytecode major55, focused test count **468**, dedicated Gradle regression and exact-head hosted full Gradle pass. Keep Draft/unmerged until certified.

## Explicit non-claims

Before/after SHA detects **persisting** byte differences, NOT byte-identical ABA, arbitrary live raw/default/legacy/future writer exclusion or descriptor-to-path binding against every race. Missing independent historical custody/provenance and signer trust, atomic multi-file crash/power-loss durable epoch. No new production World, session or settlement call path; no cleanup, rollover, pruning, unlink, grant, replay, session, release, ACK. Frozen R25 #1847 and G21.87/88 untouched.
