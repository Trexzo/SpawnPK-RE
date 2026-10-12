# G21.130 — cross-capture physical metadata drift (NO EPOCH)

Certified parent G21.129 [PR #2438](https://github.com/Trexzo/SpawnPK-RE/pull/2438) exact `0f1916edfd231491732f936e6f3daa92dc640a42`, hosted Actions #38244906490 SUCCESS 429/429 Java11. Tracks #2439.

The G21.129 seven-plane continuous G21.100 lease protects against **cooperating** writers during the observation. It compares independent v1/v2/v3 archive capture, canonical account, PREPARED journal and disk COMMIT hashes. Since advisory locks do not constrain RAW writers, unchanged bytes and SHA-256 can obscure a physical rewrite.

G21.130 adds **negative-only physical identity checks** to the three fixed bounded account/journal/COMMIT planes. Each NOFOLLOW read verifies a stable regular-file fileKey, size, creationTime and lastModifiedTime as well as exact bytes. The two scans under the SAME existing G21.100 lease now require their respective fileKey/size/creationTime/lastModifiedTime observations to remain equal. Mismatches fail closed even when all digest pins match. No archive capture, writer enrollment or default publication changes.

The new standalone `g21130MailboxContinuousPhysicalIdentityRegression` exercises a genuine four opt-in result-bound account, the original baseline, same-byte RAW writes plus deliberately advanced lastModifiedTime for **each of account/PREPARED/COMMIT** and restores the files after each. An in-place RAW rewrite that restores the exact old lastModifiedTime remains *invisible*, intentionally demonstrating the limitation. Existing G21.129 foreign-JVM and seven-plane tests remain inherited. Focused manifest 429→430; hosted CI must certify exact commit.

**Absolute negative boundary:** This is a best-effort observed-metadata drift veto, not full raw-writer detection: metadata may be spoofed/restored or the underlying filesystem may report coarse/incomplete physical identity. It does not authenticate independent pins, establish one atomic cross-file epoch or historical/future writer census, guarantee power-loss resilience, authorize turnover, pruning, cleanup, unlink or any live grant/replay/session/release/ACK. Preserve frozen R25 #1847/G21.87/88.
