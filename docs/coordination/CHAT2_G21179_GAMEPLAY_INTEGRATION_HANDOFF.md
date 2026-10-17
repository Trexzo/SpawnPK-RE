# Chat 2 ↔ Chat 1 — G21.179 gameplay integration handoff

Date: 2026-10-14  
**Canonical parent for this proposal:** Chat 1 [G21.179 core PR #2553](https://github.com/Trexzo/SpawnPK-RE/pull/2553), exact `439345dcf0092d345495e329b0968fbdbd94d570` (itself stacked on G21.178 core `d07f7680f730d2af93767cdf5e4223d6784d247f`).  
**Proposed Chat 2 source authority:** [G18.4.4 #2554](https://github.com/Trexzo/SpawnPK-RE/pull/2554), exact `17f4b865f40da0ca27db2183dcb76764e51769de`, stacked through #2551 and #2550 onto that same G21.178 base.

## Method

Combined through **GitHub Git tree objects**: copy the exact 14 Chat 2 gameplay/test/document blobs onto Chat 1's G21.179 tree, and compose `server/build.gradle` from the G18.4.4 manifest plus the **verbatim** new G21.179 core test entry and dedicated Gradle task.

G21.179 core's original first-session socket test `G21179CoreSocketFirstSessionFailedFinalSaveIntegrationTest.java`, research report, plus `WorldPlayerPersistence`, all World/session core assets and tests otherwise remain from Chat 1's source tree. No cherry-pick onto `main`; draft integration candidate only.

## 485-test composition

- G21.178 core **478**.
- G21.179 core adds **1** independent first-session bootstrap/failure test → **479**.
- Chat 2 G18.4, G18.4.1, G18.4.2, R5 fail-closed, and Monster Spawner disconnect/reconnect contribute **4** → **483**.
- Chat 2 G18.4.3 per-World username task-lifetime characterization contributes **1** → **484**.
- Chat 2 G18.4.4 opt-in exact WorldPlayer/generation DMM credit regression contributes **1** → **485**.

Expected verified marker *if exact-hosted run passes*: `GRADLE_CURRENT_TRAIN_FOCUSED_PASS count=485 bytecodeMajor=55`, `G1843_DMM_WORLD_LIFETIME_CHARACTERIZATION_PASS`, `G1844_DMM_EXACT_ORIGIN_CREDIT_PASS`, and G21.179's own core regression marker. No certification without exact SHA/run.

## Unresolved authority

Original SpawnPK server task-reward, persistence, reset and reconnect rules remain `UNKNOWN_SERVER_AUTHORITY`; EASY PvM is `CUSTOM_LOCALLAB`. The G18.4.4 overload **is NOT connected** to the terminal observer because the latter carries username-only recipient identity. It would be misleading to use terminal-time byName resolution as immutable origin evidence.

Chat 1 controls merge/promotion and #2540 durable failed-save design. Chat 2 can add gameplay and regressions only in separate draft branches. Chat 3 handles client source recovery. No changes to frozen R25 release pointer.
