# Chat 2 G21.191 + G18.5.3 cumulative integration — exact source handoff

Date: 2026-10-14. Draft/unmerged.
**Parent Chat 1 core:** [G21.191 + G18.5.2 joint #2597](https://github.com/Trexzo/SpawnPK-RE/pull/2597) at exact `39eadd182cf8f0183055d4449189f562ab6b36f8`, [Actions #38569080983](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38569080983) **SUCCESS**, Java11 major55, 498/498 focused, full Gradle. This includes experimental G21.190 forced ACTIVE review intent on admission and G21.191 UNUSED forced account postimage helper/three external-JVM file cutpoints. The helper remains UNWIRED to actual World saves, real session account receipts, review-intent retirement or general recovery. Default-off one-shot login marker denies repeated logins; no crash-durable final save/re-entry fix, original client or reward authority.

**Parent Chat 2 gameplay:** [G18.5.3 #2595](https://github.com/Trexzo/SpawnPK-RE/pull/2595) at exact `7d9dc74dbe93d9dcd0474ca62357bf8794ffbe0c`, [Actions #38568045353](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38568045353) **SUCCESS** Java11 major55, 497 focused, full Gradle. Both tracks diverged *only* from shared core `4211f07162c2bb494db23fa625856d0f711fb3f6`, with NO common changed path.

## Exact composition and source authority

Start from Chat 1 **exact G21.191 joint tree**. Import ONLY these two exact Git blobs from Chat 2 #2595:
- `server/src/spk/local/G1847MonsterSpawnerNameReuseTerminalAttributionTest.java` SHA `ec18dd5d9b9441a402471ebad29cd50030156eba` (existing 498-test covered class).
- `docs/coordination/CHAT2_G1853_G1_RECEIPT_INSTANCE_LIFETIME_2026-10-14.md` SHA `f5810903ec0c16e4a5509bed72c95b781608ac7d`.
Add this handoff. **Exactly three files** relative to Chat 1 core; all production Java, OS/session locks, account snapshot helpers, LocalSession initializer, persistence, and `server/build.gradle` exact/unchanged. Checked focused remains **498 unique**.

## G18.5.3 test evidence (CUSTOM_LOCALLAB, not historical rules)

The existing canonical installed LocalLab Monster Spawner observer finalizes one NPC; G1 PvM kill counter =1 with one service-local dedup receipt. Replaying same `(npcId, deathTick)` against **original** `world.pvmRecords()` returns `replayed`; kill count remains 1. A **manually constructed new `PvmRecordService(world)` instance on the same live World** has a fresh service-local receipt map while sharing the current `WorldPlayer` namespace counter; it accepts the exact same kill identity and increases kills **1→2**, then deduplicates its own subsequent duplicate. This demonstrates a *service-instance-lifetime receipt scope* hazard relevant to any future idempotent per-consumer outbox [#2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587).

**Do not claim** that actual production recreates this service during a running World, that a JVM restart/disk reload was tested, that a real client exploits this, or that original SpawnPK server kill/reward/receipt policy is known. Original kill authority [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558) stays UNKNOWN_SERVER_AUTHORITY.

## Hosted gate and promotion

Require **fresh combined exact-head** GitHub Actions G1853 + G1852 consumer replay + G21191 forced publication + G21190 one-shot marker, Java11 major55, **498/498 unique focused**, full Gradle. Green component runs do not certify combined head until new Actions completes. Draft/unmerged; Chat 1 owns promotion, no duplicate-import of #2595 or older Chat 2 stacked slices if this cumulative bridge accepted. R25 release pointer remains frozen; Chat 3 client deobf independent.
