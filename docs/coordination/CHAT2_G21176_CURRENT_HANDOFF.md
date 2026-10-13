# Chat 2 — G21.176 cumulative server gameplay handoff

**Date:** 2026-10-13. **Preferred integration branch:** `chat2/g21176-cumulative-g1841-r5-integrated`.
**Strict parent:** Chat 1's [G21.176 PR #2538](https://github.com/Trexzo/SpawnPK-RE/pull/2538), exact `41c4f18851c969fa4f90f6483101b235867dacb9`.
**Ownership:** Chat 1 owns cumulative release integration, World/session architecture and `main`. Chat 2 owns gameplay implementation and server-client semantic boundaries, under isolated draft PRs. Chat 3 owns exact client deobfuscation in [SpawnPK-Client](https://github.com/Trexzo/SpawnPK-Client).

## The single additive Chat 2 integration delta

Integrates **three features into the actual G21.176 source**, not simply a documentation pointer or an old parent:

1. **G18.4 Daily Money Making Easy PvM Track:** exact current C2S185 widget **55002** activates/reuses World-owned `CUSTOM_LOCALLAB` Easy PvM objective and publishes S2C126 target 26 `LocalLab Easy PvM: N/3`. The existing G18.3 persisted difficulty snapshot remains restored **after canonical account login**. Unselected/Medium/Hard/Teleport fail closed.
2. **G18.4.1 WorldPlayer generation race fence:** Track activation passes the **original WorldPlayer object, exact generation and player name**. The World-owned runtime checks the three values under one current-player mutation lock. An old UI cannot retarget a newer session that reused the username. Legacy `activateEasy(String)` continues to work by delegating through the exact owner path; no new original-server policy.
3. **R5 in-game loadout save safety:** separate exact C2S185 widgets **33006** and **33009** coexist with C2S103 `cld1` (28 inventory entries) and `cld2` (14 equipment entries). The server refuses to choose a `PlayerLoadoutId` or version from the pair; the established `LOADOUT_EDITOR_TARGET_ADAPTER_UNPROVEN` boundary remains fail closed. Source evidence connecting widget IDs to client behavior remains archived candidate only until exact pinned v308 verification, tasked to [SpawnPK-Client issue #1005](https://github.com/Trexzo/SpawnPK-Client/issues/1005).

This branch preserves the G21.176 real-socket duplicate-account ownership regression (`G21176CoreSocketNamedProfileDuplicateOwnershipIntegrationTest`). **Focused manifest = 476 G21.176 tests + 3 independent Chat 2 tests = 479**, each with a dedicated Gradle regression task and Java 11 class-major-55 requirement. The changed production Java classes are limited to `LocalSession.java`, `LocalSessionUiActionHandler.java`, `LocalDailyMoneyMakingUiHandler.java`, and `LocalLabDailyMoneyMakingRuntime.java`. No Chat 1 persistence/World core class was modified.

## Standalone hosted evidence (historical, not a substitute for current-branch testing)

| Draft PR | Exact SHA | Hosted proof |
|---|---|---|
| [#2528 G18.4 integrated with persistence](https://github.com/Trexzo/SpawnPK-RE/pull/2528) | `4dff618f698efade3f4b3414d7be519ebcfa3e32` | [Actions #38453791007](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38453791007) 473/473 SUCCESS |
| [#2533 R5 targetless save guard](https://github.com/Trexzo/SpawnPK-RE/pull/2533) | `0921694e7a36fdcbb2c4fe4dfe647856f7492c63` | [Actions #38457346387](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38457346387) 475/475 SUCCESS |
| [#2536 G21.175 combined](https://github.com/Trexzo/SpawnPK-RE/pull/2536) | `785b01e0aba34eca3d8e9ef75668a2419b5ade52` | [Actions #38459225971](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38459225971) 477/477 SUCCESS |
| [#2539 G18.4.1 exact-session race fence](https://github.com/Trexzo/SpawnPK-RE/pull/2539) | `853fecbf64fa0ba5e631be509e54264b6a001248` | [Actions #38460367180](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38460367180) 478/478 SUCCESS |

**The current 479-test G21.176 integration branch has separate GitHub Actions authority.** Do not mark it certified before its exact-head CI completes, or reuse any historical parent certification for the new commit.

## Related evidence directly available in GitHub

- [Chat 1 integration queue #392](https://github.com/Trexzo/SpawnPK-RE/issues/392): current integration coordination and certification comments.
- [Server R5 #1862](https://github.com/Trexzo/SpawnPK-RE/issues/1862): target identity remains unknown; no default-loadout fabrication.
- [Client exact v308 R5 #1005](https://github.com/Trexzo/SpawnPK-Client/issues/1005): Chat 3 exact client callsite/target-lifetime request.
- [Archived R5 candidate research PR #2530](https://github.com/Trexzo/SpawnPK-RE/pull/2530).
- [Original cross-chat handoff and 242-PR / 145-issue **historical** inventory snapshot](https://github.com/Trexzo/SpawnPK-RE/pull/2528/files): metadata snapshot is not a current count; issue statuses are not blanket closure claims.
- [Application-family umbrella #427](https://github.com/Trexzo/SpawnPK-RE/issues/427): latest source-backed distinction between present CUSTOM_LOCALLAB subsystems and original SpawnPK server policy unknowns.
- [Event Bus umbrella #3](https://github.com/Trexzo/SpawnPK-RE/issues/3): sync event infrastructure exists, full semantic taxonomy/dispatcher coverage remains incomplete.

## Chat 1 release/integration rules

- Treat this **new** G21.176 branch as the preferred Chat 2 integration candidate after exact-head hosted green. The preceding Chat 2 PRs document incremental proofs and should **not** also be merged as parallel changes.
- If Chat 1 has advanced G21 again, compare exact sources, preserve new core tests, recalculate focused manifest, and rerun complete Java 11 CI for that merged source.
- **No Chat 2 PR has been merged or any issue closed** by authoring this handoff. Human exact client UI, live-world acceptance and original server source-authority claims remain separate.
