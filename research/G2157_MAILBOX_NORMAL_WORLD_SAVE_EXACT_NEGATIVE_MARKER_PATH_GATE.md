# G21.57 — pin normal World-save negative review fences to the actual replacement file

**Parent:** certified G21.56 `a0a4cd48b66bc2b0d6b183fb590d952f6725fbd5`, [CI #37930643560](https://github.com/Trexzo/SpawnPK-RE/actions/runs/37930643560) SUCCESS / 356 focused Java11; tracked by [issue #2290](https://github.com/Trexzo/SpawnPK-RE/issues/2290).

## Measured source gap

The real file-backed normal `FilePlayerRepository.saveForWorld` selects a normalized file at the beginning of `saveInternal`, then serializes into a temp and atomically replaces *that specific file* inside the G21.38 exclusive account publication lock. But its `requireUnfencedWorldSave(account)` before writing and inside that lock previously delegated to `hasUnresolvedMailboxReviewFence(account)`: each negative sidecar class independently resolved the account path again. After one A resolution for the account file, a drifting resolver could incorrectly inspect B, permitting an overwrite of a fenced A account or falsely vetoing clean A due to a B-only sidecar.

## G21.57 narrow correction

The guarded normal World save constructs one account-scoped, normalized, fixed-path `guardedMarkerPaths` from the same `file` already selected for replacement. Both preflight and under-lock checks now use that resolver for all four negative names:

- G21.32 proposal and G21.47-A legacy through `MailboxDurableReviewFence.present`;
- G21.47-B permanent through `MailboxStrictUncertainFence.present`;
- G21.48 strict write-ahead intent through `MailboxStrictWriteIntentFence.present`.

The public/read-only `hasUnresolvedMailboxReviewFence` / permanent/in-flight session checks remain unchanged; the direct manual `save()` path still bypasses this World-only check as before. The current World admission snapshot semantics, G21.38 publication lock and the historical normal-save atomic-move compatibility remain unchanged. No new write or cleanup primitive.

## Real file-backed guarded World-save regression

`G2157MailboxNormalWorldSaveMarkerPathIntegrationTest` creates primary account files in A and injects a resolver that returns A on its first call but B on every subsequent call. For separate cases: existing A G21.47-B permanent, checksum-opaque G21.47-A legacy, A G21.48 transient, and A G21.32 review each reject normal World save **without changing A account bytes**. A fifth fixture publishes a B-permanent marker at A using the existing pre-locked `BeforeWorldReplace` seam, so the under-lock recheck—not preflight alone—must refuse replacement and preserve A bytes.

A B-only negative marker does not falsely deny an otherwise clean A save, and an independently clean A save works. For all attempts the underlying repository resolver is consulted exactly once; no alternate-path marker is touched or automatically cleared. Fresh file-backed World refuses the A-marked accounts while independent healthy accounts load. Every operation remains NO_GRANT; no temp or publication lock leaks. Current-train test count **356 → 357 unique Java11 classes**, dedicated `g2157MailboxNormalWorldSaveMarkerPathRegression`.

**Certification:** must pass exact-head hosted full Gradle, including `GRADLE_CURRENT_TRAIN_FOCUSED_PASS count=357 bytecodeMajor=55`. Draft / unmerged.

**Boundaries:** this protects cooperating normal World-save negative *presence checks* against drifting path resolvers; it does not make existing unguarded `save()` crash-safe, guarantee cross-process atomicity/hardware power-loss recovery, settle a native Mailbox reward, or prove exactly-once positive inventory COMMIT. C2S185 widget32181 remains **NO_GRANT**. R25 PR #1847 frozen.
