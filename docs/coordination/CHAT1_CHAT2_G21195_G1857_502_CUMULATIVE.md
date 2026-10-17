# Chat 1 + Chat 2: G21.195 / G18.5.7 combined regression (502 focused)

## Source and exact-boundary chain

Chat 1 G21.195 external-JVM failed-save ACTIVE marker candidate [Draft #2611](https://github.com/Trexzo/SpawnPK-RE/pull/2611), exact branch base SHA 648e40c90a876d2b0768e3cd563200656d7a7df9. This source already includes G21.194/G18.5.6 [#2609](https://github.com/Trexzo/SpawnPK-RE/pull/2609), certified on SHA f8f6ea69cc5f066e42852d2f55c04e5d67c92c09 [Actions #38588884796](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38588884796), 501 focused/Java11/full Gradle.

Chat2's exact G18.5.7 cumulative [Draft #2612](https://github.com/Trexzo/SpawnPK-RE/pull/2612), SHA e87e5f69f4d3e46ada346600be3fe626c41b67ee, has the tested-source candidate G1847MonsterSpawnerNameReuseTerminalAttributionTest.java blob 5a79e449ffbfdce70a9874654b6a7da8d07c1261. Copy that exact blob and Chat2's two corresponding source/coordination notes; do not overwrite G21.195 or reimport all older gameplay edits. No build.gradle change; G21.195-focused manifest remains **502 unique**. All production classes and all Chat2 gameplay production unchanged.

## Tests and limits

G21.195: genuine TCP holder process, fully forced one-shot ACTIVE/BOOTSTRAP, deterministic before-replace final-save fault, then force-killed holder JVM. Fresh external JVM must find released OS lock reacquirable and surviving ACTIVE review negative; real TCP login cannot register stale account or create a lease. Test-only, default OFF, no marker retirement or power-loss proof.

G18.5.7: real installed LocalLab terminal observer receives one canonical NPC death with a deliberate invalid PvM-record extension. G1 returns INVALID_STATE and records no kill/receipt while later G18 DMM still progresses and due-time spawner respawns. This is independently observed CUSTOM_LOCALLAB semantics, not historical SpawnPK reward authority, public-client exploit, grants or production patch.

Fresh exact-head hosted CI required for both markers G21195_CROSS_JVM_FAILED_FINAL_REVIEW_PASS and G1857_G1_INVALID_PVM_STATE_PARTIAL_TERMINAL_PASS, Java11 major55, 502/502 focused/full Gradle. Both #2611 and #2612 were still separately checking when composed; never inherit their CI results. Leave all PRs DRAFT/unmerged, no main/client changes, no automatic account repair/replay/grants/marker unlink. Open issues: account #2540/#2559 and gameplay outbox #2587.