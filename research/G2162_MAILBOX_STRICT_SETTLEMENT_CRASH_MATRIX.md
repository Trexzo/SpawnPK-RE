# G21.62 — real strict-settlement crash-window matrix and restart admission

Parent: hosted-certified G21.61 71a28fb2c105355fdd891d9911186b98f2145959, run #37939716380 SUCCESS / 361 Java11. Issue #2300.

## Scope
G21.23 strict file-operation tests and G21.31 negative restart-admission tests previously existed separately. The G21.62 integration regression exercises an immutable G21.25 PREPARED and hypothetical inventory+CLAIMED pair through real strict-account writes and actual World session persistence loading.

- Five failures before ATOMIC_MOVE: old PREPARED survives; no receipt; restart admits only the inert UNCLAIMED preimage.
- Two failures after atomic replace (BEFORE_DIRECTORY_FORCE and AFTER_DIRECTORY_FORCE): hypothetical inventory+CLAIMED file visible, writer emits UnconfirmedCommitException, restart refuses session hydration.
- Successful strict file-operation receipt for the hypothetical postimage still cannot upgrade settlement authority; the restart quarantines it.
- Assert live owner not credited, mailbox remains UNCLAIMED, independent account loads, no orphan temps.

This regression uses the isolated direct strict-writer fixture and makes no claim that a native World reward transaction is wired. Power-loss durability, autosave ordering, multi-process adversarial changes and positive settlement are NOT proven. It is a cross-layer test of safe refusal, not a transaction implementation.

Focused manifest 361 to 362 Java11; separate g2162MailboxStrictSettlementCrashMatrixRegression. Hosted exact-head CI and full build required before certification. DRAFT / unmerged. No widget32181 grant, replay, rollback, marker release or frozen R25 PR #1847 changes.
