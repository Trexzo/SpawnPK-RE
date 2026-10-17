# G21.160 — compose historical/current evidence delta with fail-closed signer admission (NO AUTHORITY)

**Certified parent:** [G21.159 Draft PR #2498](https://github.com/Trexzo/SpawnPK-RE/pull/2498), SHA `6e9f8801e5dda0b165dfc05e4d0392e5ca781614`, [Actions #38371000199](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38371000199) SUCCESS, Java 11 459/459 focused and full Gradle. Issue #2499.

## Actual composition gap

G21.159's separately introduced root-admission gate `MailboxProductionSignerTrustAdmissionV1` is **isolated** from the historical/current recovery-evidence comparison. It has zero independent enrolled production signer roots and ALWAYS vetoes an attempted signer-root admission. That gate alone says nothing about whether two retained G21.129 observations have changed between the historical report and the present report; meanwhile G21.144–156 observational MATCH is never proof of independently authenticated provenance.

G21.160 introduces `MailboxHistoricalRecoveryAdmissionV1`, a **read-only facade**, that composes the existing G21.144–156 seven-plane retained delta with the G21.159 trust gate. It deliberately **does not hook into existing live World/settlement/recovery callers**. Its `reviewCandidate` first checks canonical account syntax, then historical/current delta:
- observed concrete SHA/physical/portable drift returns `CHANGED_OBSERVED_EVIDENCE_NO_AUTHORITY` without evaluating a candidate signature;
- missing fileKey evidence returns `UNCERTAIN_OBSERVED_EVIDENCE_NO_AUTHORITY` without evaluating a candidate signature;
- only observational MATCH permits non-privileged G21.159 candidate signature review, returning `MATCHED_UNTRUSTED_CANDIDATE_REVIEW_NO_AUTHORITY` even if candidate/expected pin and detached P-256 signature are mathematically valid.

In all outcomes, no report includes cleanup, reward, release, replay, pruning, rollover, ACK or atomic-epoch authority. Root enrollment, independent provenance, hardware power-loss durability, original inode/fileKey identity and complete old/default/RAW/future writer exclusion remain **false**.

Its stricter `requireProductionRecoveryAdmission` has **no success return**: it vetoes invalid accounts, changed or uncertain retained delta, malformed candidate key, and otherwise delegates to the mandatory G21.159 production signer root check. Because G21.159 has **ZERO** independently enrolled keys, matched evidence + cryptographically valid self-signed candidate **still fails** with the underlying checked `NO_INDEPENDENTLY_ENROLLED_PRODUCTION_ROOT` diagnostic; a final unconditional `PRODUCTION_RECOVERY_AUTHORITY_NOT_IMPLEMENTED` veto provides defense in depth against future accidental key-registry population. The method never performs filesystem IO, obtains a lease, writes evidence or touches a grant.

## New focused regression

`G21160MailboxHistoricalRecoveryAdmissionIntegrationTest` constructs four synthetic G21.129 retained observations using six real NOFOLLOW file sources and internally valid v1/v2/v3 snapshots; a seventh real physical source provides deliberately changed current account bytes, SHA and file attributes. Two ephemeral P-256 signing keypairs represent an initial claimant and an attacker with their own key/pin. It confirms:

- 3 production root vetoes for original, attacker, and coherently self-signed forged history;
- 7 other checked vetoes (invalid account, bad key, false expected signer pin, wrong signature, observed current account drift, missing current fileKey, old signature on coherently re-hashed forged history);
- 3 mathematically valid but untrusted review-only signatures, including forged historical portable contents;
- 1 observed changed evidence review and 1 observed uncertain evidence review, both without candidate signature promotion;
- restored healthy match after test-only reflection mutations, zero active JVM leases or watches, and all authority flags false.

This is **not real capture-time provenance or durable recovery epoch**. Signatures are generated with test-only ephemeral keys; no actual signer credentials are enrolled. The root-denial API is still not connected to any production reward or settlement call path.

**Acceptance:** Java 11 major 55, dedicated `g21160MailboxHistoricalRecoveryAdmissionRegression`, full hosted Gradle and focused **460/460** on the exact final PR head. No certification until Actions succeeds.

**Unchanged:** G21.129 physical scanner, live writer/OS publication lease, production cleanup, rollover, grant/replay/session/release, client ACK, frozen R25 #1847 and G21.87/88. Draft/unmerged.
