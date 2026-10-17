# G21.339–340 — final read-only audit must recheck selected ACTIVE hardlink

**Parent:** [Draft #2897](https://github.com/Trexzo/SpawnPK-RE/pull/2897) exact `f696dd66e25d5dedb6f2a0c74d20cbd8d2bb29c0` (785 focused candidate); hosted CI must still certify G21335–38. Certified common [#2895](https://github.com/Trexzo/SpawnPK-RE/pull/2895) `af23dda1e126133183a45ba0015986bf604cbf96` 781/781.

## Specific gap
G21.335–38 check late account/ACTIVE SHA and selected inode identity, but **a late new hardlink name for selected ACTIVE can keep its bytes, inode identity, length and mtime unchanged**. The earlier `singleObservedHardlink(active)` was before the testable late seam. Re-run that exact NOFOLLOW unix:nlink negative observation at return boundary: if nlink != 1 or unsupported, throw `G21.339 FINAL_WITNESS_ACTIVE_LINK_CHANGED_DURING_AUDIT`. No positive authority on a single-link report, no account or review mutation.

G21339 creates a real hardlink for the selected ACTIVE **after initial validation** and verifies refusal, source bytes intact and no automatic login/recovery.
G21340 creates a real hardlink for **a foreign source** at the same seam, confirming exact selected canonical audit remains untrusted negative without a false global veto. Both invoke actual filesystem link operations.

Manifest **785→787 unique focused**, mandatory regression retention **101→103**, two individual JavaExec gates. Hosted exact-head 787/787, G21339/40, G21335–38, Java11 major55 and full Gradle; draft only, main untouched. This merely narrows observational races; still no atomic disk snapshot, durable authenticated owner epoch/final ACK, ACTIVE retirement, or reward outbox.
