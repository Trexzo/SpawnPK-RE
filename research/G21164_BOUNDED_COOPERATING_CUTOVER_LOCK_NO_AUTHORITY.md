# G21.164 — bounded cooperating lock acquisition for deny-only cutover (NO AUTHORITY)

**Parent:** certified G21.163 Draft PR #2506, exact head `3acf47d3d8f40c2a69af9dc7ffba44a6a90f17e2`, Actions #38386538582 SUCCESS on Java 11 (463/463 focused tests and full Gradle). Tracks #2507.

## Gap and narrow correction

G21.163 correctly composed the original G21.71 physical account/marker inspection and G21.162 cutover denial in one G21.39 account-local cooperating publication critical section. It used the unbounded `withExclusivePublication` API: a stuck participant holding the account lock could keep the *optional no-authority inspector* waiting forever.

G21.164 replaces **only that optional new cutover path's lock acquisition** with the pre-existing G21.73 `withExclusivePublicationBounded(pinned,3000L,operation)`. This preserves the same original G21.71 inspection **inside the same owned lock** and the exact no-authority disposition/root denial; it does not return a success token. G21.73 uses a per-account fair JVM monitor, bounded OS advisory tryLock polling, NOFOLLOW ancestry and lock-leaf checks and lease retirement on all normal/exceptional exits.

The **3,000-ms bound applies to acquiring the cooperating lock**. Inspection and other filesystem I/O inside the critical section are **not time-bounded** by this API; no hard wall-clock SLA is claimed.

## Real concurrent test

`G21164MailboxBoundedCutoverLockIntegrationTest` uses a genuine temp filesystem and one real cooperating G21.39 JVM+OS lock owner. While that owner holds the same account's lock, an independent account can still be inspected and denied, whereas same-account fresh cutover receives checked `RECOVERY_PUBLICATION_BUSY_NO_GRANT` rather than waiting indefinitely. After the holder releases, same-account inspection executes normally and still denies missing state. A nonregular directory lock leaf must trigger G21.73's `RECOVERY_LOCK_FILE_UNSAFE_NO_GRANT` without modification. Assertions include no account-file writes and zero leftover JVM publication leases/local watchers. There is no mock authority or bypass.

**Acceptance target:** 464/464 current-train focused Java 11 tests, dedicated `g21164MailboxBoundedCutoverLockRegression` PASS marker, major-55 bytecode and full hosted Gradle success **on the exact PR head**. Do not claim certification from earlier runs.

## Safety boundary

Cooperating/advisory locks do **not** exclude RAW/default/historical/future/uncooperative writers or prove authenticated historical provenance, independent trust-root provisioning, atomic multi-file crash/power-loss recovery or durable COMMIT. Existing production World/restart/settlement/grant callers are not newly wired to this optional API. No cleanup, rollover, pruning, unlink, reward grant, replay, session, release or ACK permissions; no G21.129 scanner, production writers or frozen R25 PR #1847/G21.87/88 changes. Keep Draft/unmerged.
