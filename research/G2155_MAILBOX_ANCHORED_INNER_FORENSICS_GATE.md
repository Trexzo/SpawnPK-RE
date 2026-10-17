# G21.55 — anchor inner negative-marker inspection to the same account-file path

**Certified parent:** G21.54 `3009d144368ad43811544b44fd6c35c32ad96eb1`, hosted [run #37927700013](https://github.com/Trexzo/SpawnPK-RE/actions/runs/37927700013) SUCCESS / 354 focused Java 11. Tracked in [issue #2286](https://github.com/Trexzo/SpawnPK-RE/issues/2286).

## Source-derived inconsistency
G21.54 pins exactly one absolute normalized account file path in each **outer** before/after sidecar census and checks that those paths match. The inner `inspectSingleMarker` nevertheless called `fence.accountFileForStrictReview(account)` again and, for G21.32, independently called `fence.present(account)` and `fence.inspect(account)` twice. A path resolver changing between these calls could combine three different sidecar directories into one diagnostic result. Such a result is nonauthorizing, but it may mislead an operator with a false exact-match or invalid label from the wrong account path.

## Narrow fix
- The outer first census passes its `accountFile` to the inner inspection; B permanent, B transient intent, and A legacy sidecar locators use this pinned path, not an independent resolver result.
- G21.32 presence and recheck use `markerPresent(pinnedProposalPath)` and `fence.inspectExactMarkerPath(account,pinnedProposalPath)`. No call to `fence.present(account)` or `fence.inspect(account)` from the inner observer.
- The outer after census still independently resolves the path exactly once. Different first/last paths yield G21.54 `NEGATIVE_MARKER_ACCOUNT_PATH_CHANGED_NO_AUTHORITY`; same-root before/after probes retain G21.33–54 classifications.
- Two resolver lookups per complete stable observation (one before, one after). Earlier G21.52–54 **deterministic test injection positions** are adjusted to reflect this new call contract, not relaxed.
- No new marker writers, path redirects, atomicity claims, positive grant, auto-release, session admission or account mutation.

## Regression
`G2155MailboxAnchoredInnerForensicsIntegrationTest` uses a file-backed World and two independent account directories. For B permanent, the primary sidecar has the account's correct SHA while the secondary has a wrong SHA; for G21.32, the primary contains a checksum-valid proposal record and the secondary contains malformed evidence. A resolver alternates **A, B, A** on successive accesses; G21.54 previously could read B during the inner inspection even when outer paths would match at A. G21.55 does **only** before A/after B and records explicit path drift, with no incorrect exact or invalid marker classification. A stable resolver proves both native B and G21.32 inner checks require precisely two resolver invocations while preserving their native stable forensic states; unfenced healthy account still loads and all seven report authorizations remain false. No automatic marker release, temp/lock leaks, reward grants, replays or rollback.

Focused current-train manifest **354→355** unique Java11 tests and dedicated `g2155MailboxAnchoredInnerForensicsRegression`. Full **exact-head GitHub Actions SUCCESS** with `GRADLE_CURRENT_TRAIN_FOCUSED_PASS count=355 bytecodeMajor=55` is required to certify.

This is negative-only operator evidence; does **not** guarantee atomic multi-file or cross-JVM snapshot, prevent ABA after the final observation, prove power-loss durability, or authorize crash-durable positive COMMIT / exactly-once inventory settlement. Native C2S185 widget32181 stays **NO_GRANT**. Frozen R25 PR #1847 untouched; PR stays Draft/unmerged.
