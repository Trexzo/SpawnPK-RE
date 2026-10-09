# G21.52 — reject single-negative-marker membership races in read-only forensics

**Stacked parent:** certified G21.51 `856261e45cc71aa2b1f19b4963b8008f2e68cacb`, hosted [run #37920599649](https://github.com/Trexzo/SpawnPK-RE/actions/runs/37920599649) SUCCESS, 351 focused Java11. [Issue #2280](https://github.com/Trexzo/SpawnPK-RE/issues/2280). Frozen R25 PR #1847 stays untouched.

## Source-derived defect
`MailboxFencedRestartForensics.inspect` used to compare the before/after four-marker evidence only when `before.count >= 2 || after.count >= 2`. If there were zero or one, changing marker **membership** across the inspection (appear, disappear, switch format) bypassed the final check. This permitted stale operator-facing `NO_FENCE_NO_AUTHORITY` or exact-snapshot classifications even though the marker-set membership changed. Production World admission maintains independent negative-marker vetoes and is NOT being relaxed.

## Tight correction
- Compare `before.present` vs `after.present` **unconditionally** across the four known negative sidecar names. Any mismatch produces a NON-AUTHORIZING changed-evidence classification. When either set contains two or more, preserve existing `MULTIPLE_NEGATIVE_MARKERS_CHANGED_NO_AUTHORITY`; otherwise use distinct `SINGLE_NEGATIVE_MARKER_MEMBERSHIP_CHANGED_NO_AUTHORITY`.
- Preserve G21.33 legacy single-marker **content**-change report classifications, G21.49 single-marker diagnostics and G21.50 multi-marker bytes/digest conflict checks. No new publisher, cleanup, positive transaction, game tick or authorization APIs.
- As before, the observed before/after state is NOT an atomic cross-file snapshot or power-loss proof; very fast ABA marker replacements cannot be ruled out.

## Regression
`G2152MailboxSingleMarkerMembershipRaceIntegrationTest` uses the real file-backed World and native sidecar publisher:
- exactly timed negative-marker publication **after an initial no-fence census**, with an unchanged account and subsequent live login veto;
- a permanent sidecar **removed while the persistence FIFO is blocked**, and a one-marker **family switch** to a transient intent during that block;
- stable permanent and empty account controls; unrelated account loading; all seven forensic authorization flags remain false; no auto-release; no temp files or JVM publication leases.
- 351 → **352 distinct current-train focused tests**, `g2152MailboxSingleMarkerMembershipRegression`.

**Certification gate:** exact-head hosted 352/352 Java11 and full Gradle `BUILD SUCCESSFUL`, not merely compilation or a predecessor run. Still no claim/reward/rollback/replay/auto-release; native C2S185 widget32181 NO_GRANT. R25 PR #1847 frozen.
