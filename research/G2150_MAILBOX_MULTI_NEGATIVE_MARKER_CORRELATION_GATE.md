# G21.50 — simultaneous negative-marker forensic correlation

**Parent:** exact G21.49 `e9411edb83948d92ec1a6f4c2cf73cec63180ce9`, hosted [#37917002642](https://github.com/Trexzo/SpawnPK-RE/actions/runs/37917002642) SUCCESS / 349 focused Java 11. [Issue #2276](https://github.com/Trexzo/SpawnPK-RE/issues/2276). Frozen R25 #1847 unchanged.

## Measured gap
G21.49 operator-only inspection chooses first available review marker (G21.47-B permanent → G21.48 intent → G21.47-A legacy → G21.32 review proposal). A valid first marker could be reported as matching the account snapshot even when another **existing** marker asserts a different strict snapshot SHA or has malformed data. Admission still fails closed, but misleading forensic conclusions impair manual review.

## Bounded G21.50 behavior
`MailboxFencedRestartForensics.inspect` now inventories only the four exact known negative sibling marker paths. For **two or more present**, it parses each current, bounded format; G21.47-B, G21.48 and legacy G21.47-A strict digests must match, and a G21.32 proposal marker may agree with either its exact PREPARED or hypothetical SHA. A conflicting digest, malformed/unreadable secondary marker, or a detected set/content change across the same existing FIFO account read returns separate `MULTIPLE_NEGATIVE_MARKERS_*_NO_AUTHORITY` states. All seven positive authorization flags stay false. No single-marker G21.33/G21.49 behavior is intentionally changed.

No filesystem lock is held during the observer read. The pre/post checks detect some, not all, races across distinct files and **do not establish an atomic snapshot or trustworthy provenance**. Marker checksums and strict hashes are unkeyed, so matching hashes are operator context, not a grant receipt.

## Regression and gates
`G2150MailboxNegativeMarkerCorrelationIntegrationTest` uses real file-backed World and original marker publishers to check:
- agreeing B permanent + write-ahead intent, and independent unfenced account;
- conflicting B permanent + intent, malformed companion, and checksum-valid but divergent legacy-A sidecar;
- actual G21.32 PREPARED proposal plus matching vs conflicting G21.47 strict checkpoint SHA, with no inventory grant or Mailbox CLAIMED;
- deterministic pause on the real persistence FIFO while a secondary marker is modified, resulting in the changed-evidence state, not an exact-match diagnostic;
- restart admission veto for every reviewed account, no tmp or publication lease leaks, all seven authorization flags false.

Focused Java11 manifest **349 → 350**, unique `g2150MailboxNegativeMarkerCorrelationRegression`. Exact-head hosted CI SUCCESS mandatory before certification. **NO_GRANT** remains authoritative for C2S185 widget32181. No positive transaction settlement, replay/rollback, auto marker release, or power-loss exactly-once claims. R25 PR #1847 untouched.
