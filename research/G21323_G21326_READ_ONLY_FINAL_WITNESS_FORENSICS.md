# G21.323–326: bounded read-only final-review witness operator audit

Last certified source [#2884](https://github.com/Trexzo/SpawnPK-RE/pull/2884), exact `9c8c143110a665b15f01d0dd5569c915db0e72ee`, hosted 757/757 focused regressions, Java11 major55 and complete Gradle.

## The gap
`LocalAccountForensicEvidence.inspect` reads an exact account and five known historical negative sidecars. It does not interpret the newer, write-once, **negative** G21.298 `.g21298-final-review-witness`. Operators previously had to read/parse its raw bytes manually to distinguish a canonical-looking record from a malformed, mismatched, missing-ACTIVE, oversized or forged one.

## New read-only class
`LocalFinalSaveWitnessForensicAudit.inspect(selectedPath, expectedUsername)` requires explicit selected account and valid canonical username. It preflights ancestry without creating the cooperating publication lock, observes only the exact `.g21298-final-review-witness` sibling with NOFOLLOW and a strict 4096-byte bound, checks single observed hardlink, double reads and compares inode, size, mtime and bytes across reads. The V1 parser requires 10 exact ordered ASCII fields and final newline, positive canonical generation/sequence and all three explicit false authority flags. It compares untrusted digest fields with fresh `LocalAccountForensicEvidence.inspect` selected account and ACTIVE read-only evidence and checks the existing ACTIVE format. Mismatch/missing/malformed/nonregular/oversize returns negative classifications; read races throw, never clean/retry or authorize. `MATCHES_UNTRUSTED_NEGATIVE` is STILL **negative only**: it does not validate authenticity, original ownership, durable ordering or grant.

## Four regressions
- G21323 publishes a real opt-in write-once witness with exact account+ACTIVE, checks canonical match is untrusted-negative, report false flags, and no file mutation/creation by auditor.
- G21324 edits account and ACTIVE bytes separately and removes ACTIVE externally; audits classify mismatch/missing negative with no cleanup.
- G21325 covers absent-is-unproven, malformed, oversize and nonregular negative leaves; original conservative login veto unchanged.
- G21326 alters a real selected witness inode in-place between the two bounded reads, with same size and restored original mtime; must fail. Foreign account churn does not affect the selected report.

No session, account writer, NPC, reward, recovery or admission production code modified. Four new dedicated JavaExec tasks; focused manifest 757→**761 unique**; 77 pre-existing G21.317 critical must-retain anchors preserved. Run exact-head hosted 761/761, G21323–26 PASS markers, Java11 major55 and full Gradle. All draft/unmerged; main untouched. #2540/#2559/#2587 durable owner-epoch, final ACK, consumer outbox remain unsolved.
