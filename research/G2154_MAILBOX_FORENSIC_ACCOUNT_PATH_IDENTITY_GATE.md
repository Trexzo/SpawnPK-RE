# G21.54 — account-path identity for read-only Mailbox review forensics

**Parent:** hosted-green G21.53 `824752ae8ff9885a515d238a71cffa9c1dc8daba` ([run #37922777497](https://github.com/Trexzo/SpawnPK-RE/actions/runs/37922777497), 353 focused Java11), [issue #2284](https://github.com/Trexzo/SpawnPK-RE/issues/2284). Frozen R25 #1847 remains unchanged.

## Measured issue

G21.53 compares four negative marker names' presence and bounded bytes before and after inspecting an account, but never records the **absolute normalized account-file path** returned by `FilePlayerRepository.PathResolver`. The original G21.50 census also independently resolves the G21.32 proposal path, potentially mixing sidecars from two directories in a *single* evidence set. A migrating, erroneous or mutable resolver may return byte-identical marker records from distinct account directories; identical bytes do not prove that a record was observed at one consistent account-file location.

## Narrow G21.54 change

- `MailboxFencedRestartForensics.inspectMarkerSet` resolves the normalized account path exactly once per census. All four sibling sidecars, including G21.32, are derived from that **same path**.
- The G21.32 record parser adds a read-only `inspectExactMarkerPath(account, selectedPath)` overload, retaining `inspect(account)` for historic callers. The multi-marker census uses the exact previously selected path, rather than making another resolver call.
- Each `MarkerSet` carries `accountFile`. Before/after account paths must match **regardless of zero/one/multiple marker count or marker byte equality**; otherwise report `NEGATIVE_MARKER_ACCOUNT_PATH_CHANGED_NO_AUTHORITY`. All seven authorization flags are false. Historic G21.33–53 marker state classifications are preserved for unchanged paths.
- No filesystem mutation, path migration, reward grant, marker auto-clear, account hydration or live World admission rule is changed.

## File-backed World regression

`G2154MailboxForensicAccountPathIdentityIntegrationTest` builds two independent account directories. It tests identical account snapshot and permanent marker bytes in both locations with a resolver switching between pre/post census, an empty-marker account switching paths, and a B-permanent-primary plus G21.32-review-secondary split so cross-root evidence cannot appear consistent. Stable B permanent, stable G21.32+B duo and empty accounts remain classified as before; independent healthy account can load. All seven authorization flags false, live fenced accounts refused, no temp/lease leaks, no marker cleanup. Manifest **353→354** unique focused Java11 tests; dedicated `g2154MailboxForensicAccountPathIdentityRegression` Gradle task.

**Acceptance:** exact-head GitHub-hosted full Gradle CI reports `GRADLE_CURRENT_TRAIN_FOCUSED_PASS count=354 bytecodeMajor=55` and `BUILD SUCCESSFUL`. This is a nonauthorizing read-only observation; no true atomic snapshot, protection against resolver ABA, hardware power-loss durability, crash-durable positive COMMIT or exactly-once inventory delivery is claimed. C2S185 widget32181 **NO_GRANT**; R25 #1847 frozen. Keep Draft/unmerged.
