# G21.287/288 — exact read-only account hardlink witness continuity

**Parent:** [Cumulative #2837](https://github.com/Trexzo/SpawnPK-RE/pull/2837), exact `0e27d11c841b70c0f6b9b875814e14fa497c57ca`, hosted 693/693 focused regressions, Java 11 major=55 and full Gradle build. This is a **draft-only** follow-up; `main` remains untouched.

## Source gap and narrowly scoped correction

`LocalAccountForensicEvidence.accountHardlinks` previously returned only `ONE_OBSERVED_NOT_AUTHORITY` or `MULTIPLE_ACCOUNT_LINKS_NEGATIVE`. When the selected inode acquired a third alias during its two bounded SHA-256 passes, before and after were both `MULTIPLE_ACCOUNT_LINKS_NEGATIVE`. The classification comparison therefore could not observe the exact `unix:nlink` 2→3 churn even when it happened at the deterministic package-only probe.

**G21.287:** The inspector now keeps a private per-scan `HardlinkWitness` of the existing qualitative state **and exact numeric count**. It checks exact continuity after the first digest and immediately before returning. Qualitative 1→2 still fails with the historical G21.285 marker, preserving its regression contract. Multiple→multiple changes throw `G21.287 ACCOUNT_HARDLINK_EXACT_COUNT_CHANGED_DURING_READ`. Unsupported or absent filesystems remain explicitly unproven/absent, never assumed a healthy single link. No hardlink or lock file is created, followed, deleted or repaired.

**G21.288:** Real unrelated 2→3 inode alias churn at the same deterministic seam must NOT globally invalidate a stable exact selected account report.

The operator's existing `accountHardlinks` report field retains its qualitative classifications and continues to state `recoveryAuthorized=false`, `loginAuthorized=false`, `grantAuthorized=false`, `finalSaveAck=false`, `ownerEpochProven=false`.

## Coverage and limits

- G21287: real selected nlink=2→3 at the first-digest seam; checks exact exception, preserved aliases/bytes, stable nlink=3 remains negative, no side effects.
- G21288: actual foreign nlink=2→3 during selected scan; selected stays scoped, exact bytes preserved, foreign inode remains negative.
- Core 693 + these two = 695 unique focused cases; JavaExec tasks also available independently.

**Explicit nonclaims:** This is a read-only best-effort filesystem observation, not an atomic multi-path snapshot, not cryptographic proof against ABA, not a session writer fence, durable account ownership epoch, crash-safe final save, login admission or recovery policy. Cross-process session ownership [#2559](https://github.com/Trexzo/SpawnPK-RE/issues/2559) and durable final-save acknowledgement [#2540](https://github.com/Trexzo/SpawnPK-RE/issues/2540) remain open.

**Gate:** independently hosted exact-head 695/695, G21285–G21288, Java11 bytecode major55 and full Gradle. Until that run finishes, this is committed source only.
