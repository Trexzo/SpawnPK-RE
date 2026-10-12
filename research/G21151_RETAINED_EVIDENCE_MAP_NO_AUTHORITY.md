# G21.151 — bounded retained evidence map with no recovery authority

**Exact certified parent:** G21.150 [Draft PR #2480](https://github.com/Trexzo/SpawnPK-RE/pull/2480), commit `84ee008fa05601268303bdb07988536be8dfa821`, [Actions #38303007271](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38303007271) SUCCESS, Java 11 focused 450/450 and full Gradle. Issue #2481.

## Distinct boundary defect

The genuine G21.129 physical leaf reader scans three watched evidence namespaces and limits their original entry counts to at most **32 v1 + 32 v2 + 4 v3 = 68**. Its evidence-map keys come from `Path.getFileName()`. The post-lease `MailboxSevenPlaneHistoricalIdentityDeltaV1` (G21.144–150) validates physical digests/sizes but not a retained map's cardinality or its filename sanity. A caller controlling both historical and current in-memory report objects can insert identical impossible map structures and still receive a matching observational result. This is in-memory report trust, not an observed write.

G21.151 makes the retained report preflight fail with checked `IOException` for **more than 68** entries (`EXCESS_EVIDENCE_LEAVES`) and for each empty, **over-200-character**, path-separator or ASCII control-character key (`BAD_EVIDENCE_NAME`). Keep the previous `NULL_EVIDENCE_MAP`, `NULL_EVIDENCE_NAME`, SHA, size, portable self-hash, original-key uncertainty and internal BEFORE/AFTER drift diagnostics. The 200-character key limit exceeds the bounded canonical account filename plus protocol suffix lengths. This is a conservative shape check, NOT a parser/authenticator of evidence provenance or proof that a <=68-name set follows the exact 32/32/4 namespace allocation.

## Acceptance

Standalone `G21151MailboxEvidenceMapShapeIntegrationTest` constructs four synthetic reports backed by six real NOFOLLOW file attribute fixtures and self-consistent bounded test-only portable SHA bodies. Using test-only reflection on retained observation map fields, cover **8 invalid map variants × 4 single-capture positions = 32** checked cases, then **8 variants** with all four report captures coherently altered. Seven malformed names: empty, forward/backward path separator, newline, tab, DEL control, and length 201; eighth variant uses **69 entries**. For every rejection restore original field in `finally`, assert immediate healthy observational MATCH and no authority. A coordinated **68-entry shape-only** boundary case should not be rejected by this size-only check and must still have all authority flags false; this does not authenticate its contents. No World publication or grants, zero JVM leases/watches. Inherited real four-bound World regression from G21.148 remains in focused manifest.

**Gate:** `g21151MailboxEvidenceMapShapeRegression`, Java 11 bytecode (major 55), hosted exact-head Gradle build and **451/451** focused tests. Uncertified until exact-head CI succeeds.

**No authority:** No on-disk change or new lock; no source authenticity, independently signed pins, complete RAW/default/historical/future writer census, atomic crash/power-loss epoch, archive rollover/pruning/cleanup/unlink, World grant, replay, session, release or ACK. Frozen R25 #1847 and G21.87/88 unchanged. Draft and unmerged.
