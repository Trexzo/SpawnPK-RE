# Chat 1 + Chat 2 G21.194/G18.5.6 cumulative integration (501 focused)

## Exact origin

This additive integration is stacked on **Chat 2 G18.5.6 [Draft #2607](https://github.com/Trexzo/SpawnPK-RE/pull/2607)** SHA `d0654ae17656e0aadcb40590fbffc1039570ed6b`; it includes certified [G18.5.5 #2606](https://github.com/Trexzo/SpawnPK-RE/pull/2606) SHA `f15219ba0ce1914d4232014e588421d338b44ae7` and previous G21.193 Chat1 account experiment. #2607 exact-head CI [#38584749001](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38584749001) requires independent green verification; do not represent its check as already approved until confirmed.

Import byte-for-byte exact Chat1 [G21.194 core #2608](https://github.com/Trexzo/SpawnPK-RE/pull/2608) Java regression blob `8ab7b13781de79c4df4f0f6596aca719114e7f54`, research note blob `1240a676fb003cba1be6ef32c7a7276395f2bd05` and its `server/build.gradle` blob `88e72b33230bf4c80d1f3e425e9fee4e83fd2281`. Compared to G18.5.6 parent these additions are one test + docs + manifest increment **500→501**. All existing Chat2 G18.5.6 and G18.5.5 PvM tests unchanged; no production Java modifications.

## Narrow experimental result

One-shot forced ACTIVE marker and forced BOOTSTRAP are enabled only for a disposable real TCP test account. Inject a deterministic final-save IOException **after** forced World-owned temp preparation and **before** replacement; previous BOOTSTRAP account and ACTIVE marker remain unchanged and unsaved World mutation is not published. A new World/repository then denies actual authenticated synthetic TCP session. No marker retirement, replay, account recovery, durable session epoch, machine power-loss proof, post-rename cutpoint evidence, raw writer exclusion or historical SpawnPK gameplay authority.

## Acceptance

Fresh exact-head hosted Java11 major55, `G21194_FORCED_FAILURE_REVIEW_QUARANTINE_PASS`, Chat2 `G1856_PVM_SAME_NPC_DIFFERENT_TICK_RECORD_IDENTITY_PASS` and previous markers, **501/501** focused and full Gradle. Draft/unmerged until green. No changes to Chat2 gameplay production, client or main. Preserve #2540/#2559/#2587 open; frozen R25 #1847/G21.87/G21.88.
