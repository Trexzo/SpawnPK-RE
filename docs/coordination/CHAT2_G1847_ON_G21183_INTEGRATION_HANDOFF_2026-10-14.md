# Chat 2 G18.4.7 minimal increment on Chat 1 G21.183 integration

Date: 2026-10-14.

**Canonical intended parent:** Chat 1 [combined integration #2566](https://github.com/Trexzo/SpawnPK-RE/pull/2566), exact `8a4272b58bb1a5973be02cf1ab7ea0a0847c3733`, which already includes G21.183 lease guard and Chat 2 G18.4.6 gameplay, 489 focused tests. This proposal adds **only** G18.4.7 negative attribution characterization. The older alternative [Chat 2 #2567](https://github.com/Trexzo/SpawnPK-RE/pull/2567) contains equivalent runtime/source files but is **not an additional integration to merge**.

## Implementation
- Reuse exact corrected `G1847MonsterSpawnerNameReuseTerminalAttributionTest` blob from [#2565](https://github.com/Trexzo/SpawnPK-RE/pull/2565). It uses the **installed** `LocalLabMonsterSpawnerProvisioning.create` callback; old WorldPlayer A owns NPC's **spawn origin**; same-username B replaces A before synthetic lethal damage; terminal observer's name-only progression may credit B; opt-in exact generation method refuses stale A. Offline control has no current name **at terminal delivery** and reattaches a read-only observer user only *after* terminalization because the DMM snapshot API requires current player.
- Preserve all existing G21.183 socket/lease code and tests byte-for-byte. Add one focused class and dedicated Gradle task: **489→490 checked unique tests**. Carry G18.4.7 source handoff.
- Only test, Gradle, and coordination docs should differ from #2566.

## Hosted evidence status
- Prior first G18.4.7 run [#38543901978](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38543901978) **FAILED** on an offline test-only snapshot access before the control reattachment. It was not a source compilation failure.
- Fixed corrected G18.4.7 [#2565](https://github.com/Trexzo/SpawnPK-RE/pull/2565) and integrated [#2567](https://github.com/Trexzo/SpawnPK-RE/pull/2567) are under new hosted validation. This minimal branch also needs its own **exact-head** Java11 major55, `G1847_NAME_REUSE_TERMINAL_ATTRIBUTION_CHARACTERIZATION_PASS`, 490/490 focused and full Gradle **PASS**. No certification until Actions confirms.
- The original SpawnPK server reward rule is **UNKNOWN_SERVER_AUTHORITY**; the test is expected to characterize a **CUSTOM_LOCALLAB** hazard only, not real-client exploit, last-hit policy or remediation. No production progression, settlement, account lease, persistence or client behavior changed.

## Cross-chat promotion
Chat 1 alone owns merge and promotion. Accept this child increment on #2566 or another exactly verified equivalent, not both #2567 and this branch. Keep [#2547](https://github.com/Trexzo/SpawnPK-RE/issues/2547), [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558), [#2540](https://github.com/Trexzo/SpawnPK-RE/issues/2540), [#2559](https://github.com/Trexzo/SpawnPK-RE/issues/2559) open. R25 frozen. Chat 3 client recovery separate.
