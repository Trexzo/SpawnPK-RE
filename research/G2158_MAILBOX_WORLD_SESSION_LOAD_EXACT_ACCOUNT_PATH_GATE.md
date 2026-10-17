# G21.58 — one account-file identity for admitted World session loads and negative markers

**Parent:** G21.57 `b233564fd4f554bf74deb3cc67d7b9fa558e1a32`, hosted [CI #37931785706](https://github.com/Trexzo/SpawnPK-RE/actions/runs/37931785706) SUCCESS / 357 focused Java11. [Issue #2292](https://github.com/Trexzo/SpawnPK-RE/issues/2292). Frozen R25 PR #1847 untouched.

## Measured source defect

`WorldPlayerPersistence.LoadTask` previously invoked `FilePlayerRepository.hasUnresolvedMailboxReviewFence(account)` before and after `repository.load(account)`, each re-resolving the file independently. Each B permanent, B transient-intent, G21.32 review, and legacy-A presence probe in turn independently resolved the file again. An abnormal A→B resolver could check unrelated B negative markers, read A snapshot bytes, and incorrectly hydrate a quarantined session or report a false veto. This read-path problem is distinct from the G21.56 strict-save and G21.57 ordinary World-save fixes.

## Narrow correction

- New `FilePlayerRepository.loadForWorldSession(account)` selects one normalized account file, constructs an account-scoped fixed-path marker resolver and uses that *same* path for every G21.32/legacy-A, G21.47-B permanent and G21.48 transient admission check before and after reading snapshot bytes.
- `loadExactFile(username,file)` contains the unchanged properties-file decoder; the existing public `repository.load` still calls it directly and remains **nonadmitting** for read-only forensics.
- After post-read negative-marker check, repeat original account path resolution and **reject** if it differs from the pinned file (`G21.58 MAILBOX_SESSION_ACCOUNT_PATH_CHANGED`). A path change is never interpreted as permission to hydrate a different file. These checks are not an atomic multi-file snapshot or a defense against instantaneous resolver ABA.
- The World `LoadTask` uses the guarded file-backed session entry only when `enforceAdmission=true`. Non-file PlayerRepository adapters and explicitly unadmitted forensic observations use their existing read path. The independent G21.31 PREPARED-journal admission check remains.
- Optional package-scoped `afterWorldSessionRead` test seam proves a marker published between file read and second negative check fails closed. No transaction/recovery/writer/claim API changed.

## Focused real World regression

`G2158MailboxWorldSessionLoadPathIntegrationTest` uses actual A and B directories. An adversarial repository resolver points to A on its first call and B thereafter. Existing A G21.47-B permanent, legacy-A, G21.48 intent, and G21.32 each block session load before reading; a missing account with a marker also blocks. A marker published at A via `afterWorldSessionRead` after snapshot decoding but before second admission check must deny hydration and persist. An unfenced A→B resolver transition must deny admission, including when B alone contains a negative marker. Stable-path A accounts still load; stable missing account returns empty; a B-only marker doesn't block stable A. Direct raw `repository.load` remains accessible to forensics, not session hydration. No file bytes or markers are cleaned or rewritten; no temp or publication lease leaks.

Focused manifest **357→358 unique Java11** and dedicated `g2158MailboxWorldSessionLoadPathRegression` task. Require exact-head hosted `GRADLE_CURRENT_TRAIN_FOCUSED_PASS count=358 bytecodeMajor=55` and complete `BUILD SUCCESSFUL` before certification.

**Boundaries:** The G21.58 session check does not establish a cross-file atomic snapshot, guarantee against resolver ABA, mitigate uncooperative writers, prove hardware power-loss integrity or authorize exactly-once inventory COMMIT. Native C2S185 widget32181 **NO_GRANT**, no claim grants, replay, rollback, or marker release. Keep stacked PR Draft/unmerged.
