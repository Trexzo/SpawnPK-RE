# G21.163 — retain G21.39 cooperating lock through fresh cutover denial (NO AUTHORITY)

**Certified parent:** [G21.162 Draft PR #2504](https://github.com/Trexzo/SpawnPK-RE/pull/2504), exact `21f412e041a355abf7c2fe2eb1808f81e14bac61`, [Actions #38383569266](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38383569266) SUCCESS, Java11 focused **462/462** and full Gradle. Issue #2505.

## Source-confirmed cooperating-writer gap

G21.162 `MailboxFreshFileCutoverAdmissionV1.requireFreshProductionCutover` invokes `FilePlayerRepository.inspectRestartRecoveryReadOnly`. The original G21.71 method holds the G21.39 cooperating JVM+OS publication lock for disk reads and marker comparisons, **then unlocks before** the new cutover disposition/root denial. A cooperating marker writer could act between the two phases. Although all existing outcomes are already NO AUTHORITY, no future design should presume their cutoff represents one continuous cooperating critical section.

G21.163 adds a *narrow* `FilePlayerRepository.requireFreshCutoverUnderPublicationLock` that holds the existing account-local `MailboxAccountPublicationCoordinator.withExclusivePublication` lock over **both** (1) original `inspectRestartRecoveryLocked` with its NOFOLLOW account/marker/bytes checks and (2) `MailboxFreshFileCutoverAdmissionV1.requireObservedCutoverInsidePublicationLock`, which requires all seven missing positive proofs, the existing G21.79 state disposition and the G21.161 deny-only signer-root gate. The public-facing G21.162 `requireFreshProductionCutover(repository,account,key)` delegates to this method. There is no intermediate returned Decision, no extra lock acquisition, and no success/authority branch. A terminal that is physically coherent is still uncommitted, with **zero** enrolled signer roots, and will be vetoed. Defensive unconditional denials remain in both layers even if a future change were to return from a root check.

The repository also provides a package-scoped **deterministic test-only** callback invoked **after physical inspection while the same lock remains held and before the cutover denial**. It is never invoked from a production entry point. A canonical account path is checked again after this callback. No original World/session/settlement/grant caller is changed or connected to this API.

## Real filesystem concurrency regression

New `G21163MailboxLockedCutoverContinuityIntegrationTest` preserves the inherited G21.162 actual repository states (missing, legacy, post-marker manual hold, explicit test-fixture marker deletion, coherent terminal), signing-key candidate checks, immutable source-byte checks and unclaimed isolated-World reward.

It then pauses the deny-only procedure in the test-only callback while G21.39's cooperating account lock is still owned. A second thread attempts a genuine `withExclusivePublication` on the **same account** to write a negative manual-review marker. Assertions ensure the publisher **cannot enter during the locked inspection-to-denial window**, then enters after denial exits and the lock releases. Its marker remains intact; a separate fresh read now sees `REVIEW_MARKER_MANUAL_HOLD`. This is an actual concurrency test, not merely an in-memory report mutation. Count: **6 real-file reviews**, **3 marker-transition refreshments**, **6 negative-state vetoes**, **1 signer-root veto**, **2 malformed-key/account vetoes**, original account bytes unchanged, zero active JVM leases/local watches.

**Acceptance:** dedicated `g21163MailboxLockedCutoverContinuityRegression`, Java11 bytecode major55, **463/463** focused regressions, full Gradle exact-head hosted Actions success. Do not certify without logged result.

## Hard restrictions that remain

An account-local advisory JVM/OS lock only coordinates **participating** writers. It does **not** exclude original RAW/default/historical/future writers that ignore it; does not freeze other files outside the protocol; does not establish a trusted original signer/capture-time custody chain, atomic multi-file crash recovery or hardware power-loss durability; and does not authorize COMMIT, cleanup, rollover, pruning, unlink, grants, replay, session, release or ACK. The new facade remains optional, not mandatory for existing production World or restart callers. Frozen R25 PR #1847 and G21.87/88 untouched. Keep Draft/unmerged.
