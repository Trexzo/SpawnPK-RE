# Chat 2 G18.7.6 — healthy full consumer chain, one finalized event per NPC

Date: 2026-10-15.

**Exact certified cumulative parent**: [G21.215 + G18.7.5 draft #2679](https://github.com/Trexzo/SpawnPK-RE/pull/2679), SHA `86b3f6cec253f1032ab98cda61fd9feed0fa706d`, [hosted Actions #38754862186](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38754862186) SUCCESS, **532 unique focused**, Java11 major55/full Gradle. Parent contains the separate-JVM RAW writer exclusion **negative** G21.215 and corrected forward-only G18.7.5. G21.216 [#2678](https://github.com/Trexzo/SpawnPK-RE/pull/2678) and its 533-test combined #2680 were RED at this branch's creation, so this source deliberately does not incorporate their uncertified production changes.

## Why a positive control is necessary

Earlier installed-observer G18.7.2, G18.7.3 and G18.7.4 regressions separately show a TEST-ONLY malformed G14, G15 or G16 persistence snapshot causes one synchronous observer callback to fail **after the earlier consumers succeeded**, and leaves one or more later consumers uncredited despite NPC finalization and ground settlement. G18.7.5 characterizes forward-only progress after clean owner replacement. This new regression keeps **all G1, G4, G14, G15, G16, G18 legitimately initialized and valid** on one exact WorldPlayer, to prove the healthy control actually delivers each eligible stage in correct sequence.

1. Create isolated World and register player under one normalized name. Legitimately start G4 Monster Hunter ACTIVE0, G14 Daily Challenge progress0, G15 Task Scroll ACTIVE0, G16 Adventure ACTIVE0, and G18 Daily Money Making EASY0. No test-only malformed persistence, account file mutation, login or synthetic callback.
2. Spawn one genuine installed Monster Spawner NPC bound to this exact WorldPlayer and generation; apply real lethal lifecycle damage and finalize. Assert FINALIZED, one one-coin settlement, G1 kills1/receipt1, G4 complete1, G14 progress1, G15 progress1, G16 progress1, G18 progress1. Immediately call **finalizeIfOwned on the same already-finalized WorldNpc again**: expect `NOT_OWNED` and unchanged per-consumer progress/receipts; no additional ground settlement.
3. Execute actual G1 due-time respawn and verify new NPC ID and same exact live recipient generation. Genuinely kill/finalize this **second distinct** NPC. Assert FINALIZED, another one-coin settlement, G1 kills2/receipt2, G4 completions still1, G14 progress2, G15 progress2, G16 progress2, G18 progress2. Attempt duplicate `finalizeIfOwned` of the second NPC: expect `NOT_OWNED` and all counts unchanged, two coins total. Original NPC handles are no longer owned.

New expected hosted marker `G1876_HEALTHY_ALL_CONSUMERS_DUPLICATE_TERMINAL_SUPPRESSION_PASS`.

## Scope and nonclaims

Strictly **CUSTOM_LOCALLAB in-memory World control**, not original SpawnPK original-server kill authority, attacker/source attribution, an external reward or a cross-JVM authenticated login. Re-finalization `NOT_OWNED` is a **same-World ephemeral runtime guard**, not a durable consumer ACK/replay or exactly-once guarantee across process crash. This test does NOT implement a partial-failure repair mechanism, automatically retry previously missed G18 credit, or authorize any original reward grant. Per-consumer outbox [#2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587) remains OPEN and original policy [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558) UNKNOWN_SERVER_AUTHORITY.

**Exactly 3 files** versus green #2679: new independent `G1876AllConsumerSuccessAndDuplicateTerminalSuppressionTest.java`, `server/build.gradle` one focused class plus both count guards **532→533 unique**, and this source note. **ZERO production Java, G21.215/G21.216 account lease/OS lock, old G1872/3/4/5 tests, client/default/main code changes.**

**Fresh exact-head CI required** 533/533 unique focused, markers G1876/G1875/G21215/G21184, Java11 major55 and full Gradle. Parent SUCCESS does not certify this new SHA. Keep DRAFT/unmerged; core G21.216 failed prior run due to obsolete child test marker string and that issue was independently reported to Chat1 [#2678](https://github.com/Trexzo/SpawnPK-RE/pull/2678). Do not close account/ACTIVE review markers or merge main.