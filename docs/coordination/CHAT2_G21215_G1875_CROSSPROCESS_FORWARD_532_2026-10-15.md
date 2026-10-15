# Chat 2 G21.215 + G18.7.5 — 532 unique focused: separate JVM writer gap and gameplay forward-only credit

Date: 2026-10-15.

## Source and independent CI facts

**Exact G21.215 source parent:** Chat1 [core #2676](https://github.com/Trexzo/SpawnPK-RE/pull/2676) SHA `22bb79561a5bee77a38a7e41d745434a87b6b512`, 531 focused, [Actions #38753730685](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38753730685) **IN PROGRESS at initial composition**. G21.215 is a negative ACTUAL two-JVM test of a direct RAW account write bypassing a held OS session lock despite all opt-in RAW writer JVM gates ON. Passing this regression confirms a remaining cross-process hole, **not** a cross-process fix, epoch, atomic commit or crash recovery.

**Independently hosted-green gameplay donor:** [G18.7.5 #2673](https://github.com/Trexzo/SpawnPK-RE/pull/2673) exact corrected SHA `28af6313b271659099f69b763bc604fc9e5fac77`, [Actions #38752478542](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38752478542) SUCCESS, 527 focused, Java11 major55/full Gradle. Corrected G1875 test Git blob `a11c33400823367c3461c9f36bdbdff7d51f39ec` and original source note blob `f143fa0724d0d76a8cf0761528e5a29ec532ebcf` imported unchanged. The original G1875 run failed because the test wrongly expected World/name-scoped G14 progress to reset after player replacement; this has been fixed and independently certified.

**Other combined 531 candidate:** [Chat2 #2677](https://github.com/Trexzo/SpawnPK-RE/pull/2677) uses hosted-green G21.214 core and the exact green G1875 donor but does NOT contain newer G21.215 test. This 532 candidate starts instead from exact G21.215 parent and overlays ONLY the new G1875 test, one Gradle manifest entry and both hardcoded unique counts **531→532** and the source note, plus this independent combined handoff. Do not transplant either 531-test Gradle file wholesale.

## Gameplay stage and lifetime facts

G18.7.5 uses original installed unmodified Monster Spawner terminal observer, real canonical NPC lifecycle, immutable spawn origin and two separate genuine NPC deaths. A has deliberately malformed **test-only** G16 Adventure persistence, so G1 receipts2/coins2 and G14/G15 progress2 commit but G18 EASY misses both downstream deliveries (remains0). A unregisters; clean same-name B in SAME World with fresh generation activates G16 and sees **no automatic backfill** of historical G18 or G1 receipts/coins. G14 is still World/name-scoped at2, not reset0. Real G1 due-time ticket produces THIRD distinct NPC at B origin; only that NEW finalization advances G18 to1, B G16 to1, G14/G15 to3, receipts/coins to3. No replay of the old NPC deaths. Historical missed G18 progression remains unrecovered.

Neither the G18 test nor G21 account tests prove original SpawnPK server kill attribution, remote corruption reachability, authenticated TCP relog, cross-process ownership, durable outbox, stage ACK/idempotency, automatic grants, or crash-safe account recovery. [Original credit #2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558), [gameplay outbox #2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587), account #2540/#2559 and ACTIVE review remain OPEN.

## Integration invariant and required hosted proof

Diff vs G21.215 EXACTLY FOUR additive files: G1875 dedicated Java test + original note, `server/build.gradle` one new focused manifest entry and BOTH expected-count assertions **531→532**, and this joint note. Existing G21215, G21214, G21213 and G1872/3/4 tests are preserved, no production Java, session flag, account RAW/OS lock, other gameplay, default/main/client changes.

**Fresh 532-test exact-head hosted CI REQUIRED**: G1875, G1874, G21215, G21214, 532/532 focused, Java11 major55 and full Gradle. A certified donor does not certify the combined head, and if parent #2676 remains pending, do not claim it green. All PRs DRAFT/unmerged. No main merge, ACTIVE marker retirement, retry of ground settlement or asserted original server reward policy.