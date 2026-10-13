# Chat 2 — preferred cumulative integration pointer (2026-10-13)

**Main operating rule:** Chat 1 owns integration and `main`. Chat 2 makes independent branches and draft PRs in `Trexzo/SpawnPK-RE`; Chat 3 owns exact client deobfuscation in `Trexzo/SpawnPK-Client`. This Markdown handoff exists in GitHub, not in a private chat paste.

## Current preferred Chat 2 branch

`chat2/cumulative-g21175-g184-r5-verified-integration` was created at the **exact Chat 1 G21.175** parent commit `2fe77f187684cb1f4c5b1f255728be733e6b403a` ([Chat 1 PR #2535](https://github.com/Trexzo/SpawnPK-RE/pull/2535)).

Purpose: combine two independent, separately hosted-green Chat 2 features into **one additive integration review** without cherry-picking competing historic Gradle manifests. `server/build.gradle` focused count is **475 + 2 = 477 unique Java11 regressions** in this branch.

### Imported G18.4 gameplay code (exact original certified files)
From [PR #2528](https://github.com/Trexzo/SpawnPK-RE/pull/2528), isolated exact SHA `4dff618f698efade3f4b3414d7be519ebcfa3e32`, [Actions #38453791007](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38453791007) SUCCESS:
- `LocalDailyMoneyMakingUiHandler.java` (preserves G18.3 persisted difficulty, stale session generation fence, EASY-only World-owned `CUSTOM_LOCALLAB` PvM Track widget 55002, S2C126 target 26).
- `LocalSessionUiActionHandler.java` (post-login adapter installed once).
- `LocalSession.java` (install after canonical WorldPlayer login restore).
- `G184DailyMoneyMakingLiveTrackIntegrationTest.java` (ISAAC wire, idempotent progress, persisted Medium rehydration, stale player, unconfigured Medium/Hard and Teleport fail-closed).
- Dedicated `g184DailyMoneyMakingLiveTrackRegression` Gradle task.

### Imported R5 fail-closed safety fixture
From [PR #2533](https://github.com/Trexzo/SpawnPK-RE/pull/2533), isolated exact SHA `0921694e7a36fdcbb2c4fe4dfe647856f7492c63`, [Actions #38457346387](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38457346387) SUCCESS:
- `R5LoadoutEditorButtonPairNoTargetRegressionTest.java`: model opcode185 widget33006 + separate `cld1/cld2` typed pair + widget33009, bounded ISAAC and targetless server fail-closed checks. No client-only implicit ID/version; no guessed loadout mutation.
- Dedicated `r5LoadoutEditorButtonPairNoTargetRegression` Gradle task.
- Still **not** exact JAR proof of widget-to-command sequencing. That question belongs to Chat 3 [client issue #1005](https://github.com/Trexzo/SpawnPK-Client/issues/1005).

### Shared documentation retained in this integration branch
- `docs/coordination/CHAT1_CHAT2_HANDOFF_2026-10-13.md`: architecture/ownership and issue reconciliation, originally written for earlier G21.172 branch.
- `docs/coordination/OPEN_PR_ANCESTRY_2026-10-13.tsv` and `OPEN_ISSUE_TRIAGE_2026-10-13.tsv`: point-in-time **242 PR / 145 issue** snapshot at collection time. They are not a live count or automatic closure directive.
- `research/R5_LOADOUT_EDITOR_TARGET_ARCHIVE_EVIDENCE_2026-10-13.md`: archived candidate client callsites and evidence limits (from research [PR #2530](https://github.com/Trexzo/SpawnPK-RE/pull/2530)).

## Certification and merge instruction

- This combined branch **must pass its own exact-head GitHub Actions**. Earlier green runs cannot be transferred to a new cumulative SHA. Update certification details in the PR description or issue #392 only after the run actually completes.
- Chat 1's later G21 successors may advance Gradle manifests independently. When promoting, replay both test entries/tasks atop the current cumulative branch, recompute count and rerun Java11 focused+full Gradle on the merged exact head.
- This is intentionally a **draft additive integration branch**: no merges, no issue closures, no overwrites of Chat 1's G21 recovery/persistence source.
- Do not merge both this branch and historic #2528/#2533 separately; the latter become superseded integration references only after this combined branch is verified and selected.
- Outstanding external human client UI/Walk-here acceptance remains distinct from hosted CI; `UNKNOWN_SERVER_AUTHORITY` remains unknown.

## Additional apparently implemented-but-open issue evidence

Code reviewed on G21.174/G21.175 and posted in the issue threads:
- [#1397](https://github.com/Trexzo/SpawnPK-RE/issues/1397): WorldPulse Monster Spawner settlement retry.
- [#1586](https://github.com/Trexzo/SpawnPK-RE/issues/1586): remote pet deferred mask/no duplicate mirror.
- [#1830](https://github.com/Trexzo/SpawnPK-RE/issues/1830): region stream combat-clock commit fence.
- [#1404](https://github.com/Trexzo/SpawnPK-RE/issues/1404): UI-open Monster Spawner widget ownership and post-close rejection.
Require latest exact cumulative CI/ancestry review before closures.

**This document is the single preferred Chat 2 handoff pointer for this cumulative integration branch.**
