# G21.152 — per-family retained evidence namespace limits (NO AUTHORITY)

**Certified parent:** [G21.151 Draft PR #2482](https://github.com/Trexzo/SpawnPK-RE/pull/2482), exact `f4ae3bb9e985ae9ee463585e8ae56c1906b777c4`, [Actions #38305902958](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38305902958) SUCCESS, Java 11 focused 451/451 and full Gradle. Issue #2483.

## Source-supported gap

The genuine G21.129 `MailboxSevenPlaneContinuousPhysicalWitnessV1.evidenceLeafMetadata` scans distinct account-scoped globs with capacities **32 v1 + 32 v2 + 4 v3**. G21.151's retained in-memory comparator checks only **68 total**, plain names and SHA/size; a caller can coherently forge retained historical and current snapshots with 33 v1 or 5 v3 entries yet stay below 68, or insert wrong versions/unknown family prefixes with benign characters. Both reports might still appear MATCH.

**G21.152** adds negative-only validation of three known suffix families, correct stage extensions (v1/v2 begin or complete, v3 result), the four distinct known G21.12x result-family prefixes, a common captured filename basename within each observation, and independent **32/32/4** ceilings. Checked `IOException` reasons: `UNKNOWN_EVIDENCE_FAMILY`, `EXCESS_EVIDENCE_FAMILY`, `MIXED_EVIDENCE_BASENAME`. It retains G21.151's total <=68 and control-character/path exclusions. The family check deliberately only validates broad suffix shape: it is **not** yet an exact canonical UUID parser, file content/leaf parser, proof that a basename belongs to the supplied account, or a substitute for authenticated historical provenance. It does not require exactly four v3 map entries from an untrusted report; a genuine G21.129 scan still separately requires four real results.

## Regression and inherited compatibility

New `G21152MailboxEvidenceFamilyIntegrationTest` isolates **8 malformed family/quota variants × 4 captured positions = 32** checked failures, plus **8 coordinated four-capture** corruptions. Three quota cases use 33 v1, 33 v2, and 5 v3; five invalid cases use unknown G21.99 namespace, wrong v1 version, mismatched basename, mismatched v3-kind prefix and unsupported v2 stage. All fixture modifications occur only in *already captured synthetic in-memory reports* constructed from six real NOFOLLOW file attributes and bounded self-hashed test-only portable snapshots. Restore exact private fields in `finally` and require healthy MATCH after every failure. Assert zero active JVM leases/watches and no World grant.

To preserve the inherited G21.151 maximum-total **68-entry negative control**, its synthetic entries now use the balanced family distribution 32 v1 + 32 v2 + 4 v3. A well-shaped synthetic 68-entry report still returns only observational MATCH with all grant/cleanup/etc. flags false. The original real four-bound World regression remains in the focused manifest from G21.148.

**Acceptance:** `g21152MailboxEvidenceFamilyRegression`, Java 11 bytecode major 55, full hosted exact-head Gradle build and **452/452** focused tests.

## Persistent restrictions

No original physical scanner, OS lease, writer, archive, protocol, account file, grant, replay, session, release or client ACK changed. Nothing authenticates original historical source/signing, excludes RAW/default/historical/future writers, constructs an atomic multi-file crash/power-loss epoch, or authorizes pruning/rollover/cleanup/unlink/reward settlement. Frozen R25 #1847 and G21.87/88 unaffected. Keep Draft/unmerged pending CI.
