# Open GitHub issue inventory — 2026-10-15

Repository: `Trexzo/SpawnPK-RE`. Snapshot of GitHub OPEN non-PR issues, collected in nonoverlapping exact creation-day searches to avoid 100-result truncation.

**Count at snapshot: 149 open issues.** Automatic lane guesses are triage aids **only**, not proof of resolution. The exact issue body, linked PR ancestry, acceptance criteria and current main must be checked before close.

**Live unresolved/coordination anchors:** #392 (release queue), #2526 (cross-chat handoff), #2540 (failed final-save durable safety), #2559 (cross-World owner epoch), #2587 (partial terminal observer/outbox), #2558 (original credit origin), #427 (exact-v308 application gaps), #3 (server event bus), #1 (source organization). Do not close by approximate title match.

## Group totals

- Other / architecture: 18
- Coordination / release gate: 2
- Exact client / UI: 16
- Core / consistency: 91
- Gameplay / attribution: 22

## Exact open issue index

| Issue | Created | Lane (tentative) | Title |
|---|---|---|---|
| [#1](https://github.com/Trexzo/SpawnPK-RE/issues/1) | 2026-09-19 | Other / architecture | [Refactor] Reorganize Source Tree & Package Hierarchy (Separate Core & Tests) |
| [#3](https://github.com/Trexzo/SpawnPK-RE/issues/3) | 2026-09-19 | Other / architecture | [Core Architecture] Implement Server Event Bus & Action Dispatcher |
| [#9](https://github.com/Trexzo/SpawnPK-RE/issues/9) | 2026-09-19 | Other / architecture | [Pipeline & Custom Content] Replace Voidglass R3 Compositor Trial with Custom Cache/Model Pipeline |
| [#392](https://github.com/Trexzo/SpawnPK-RE/issues/392) | 2026-09-21 | Coordination / release gate | [Chat 1] Integration + Release Queue |
| [#427](https://github.com/Trexzo/SpawnPK-RE/issues/427) | 2026-09-21 | Exact client / UI | [Chat 2][Exact v308] Recover uncovered SpawnPK application/UI contracts |
| [#1005](https://github.com/Trexzo/SpawnPK-RE/issues/1005) | 2026-09-28 | Core / consistency | [Chat 1 Release] Bind current release acceptance to a clean exact Git head before build |
| [#1006](https://github.com/Trexzo/SpawnPK-RE/issues/1006) | 2026-09-28 | Core / consistency | [Chat 1 Release] Bind final loopback smoke to the exact spawned server PID |
| [#1010](https://github.com/Trexzo/SpawnPK-RE/issues/1010) | 2026-09-28 | Core / consistency | [Chat 1 Release] Bind final smoke to the cumulative-certified server JAR bytes |
| [#1015](https://github.com/Trexzo/SpawnPK-RE/issues/1015) | 2026-09-28 | Core / consistency | [Chat 1 Release] Require final smoke cleanup before emitting release PASS |
| [#1048](https://github.com/Trexzo/SpawnPK-RE/issues/1048) | 2026-09-28 | Core / consistency | [Chat 2 Runtime] Bind exact-v308 local-client build to one guarded input identity |
| [#1050](https://github.com/Trexzo/SpawnPK-RE/issues/1050) | 2026-09-28 | Exact client / UI | [Chat 2 Assets] Bind R13 profile build to one guarded exact-v308 client identity |
| [#1059](https://github.com/Trexzo/SpawnPK-RE/issues/1059) | 2026-09-28 | Core / consistency | [Chat 2 Release] Bind cumulative server SHA across wrapper→release in memory |
| [#1063](https://github.com/Trexzo/SpawnPK-RE/issues/1063) | 2026-09-28 | Core / consistency | [Chat 2 Release] Bind Gradle PASS marker to invocation-owned output, not mutable log path |
| [#1079](https://github.com/Trexzo/SpawnPK-RE/issues/1079) | 2026-09-29 | Core / consistency | [Chat 2 Release] Treat dialog-number-key sidecar as generated runtime state |
| [#1096](https://github.com/Trexzo/SpawnPK-RE/issues/1096) | 2026-09-29 | Exact client / UI | [Chat 2] R13 acceptance must build LocalLab server before launch |
| [#1098](https://github.com/Trexzo/SpawnPK-RE/issues/1098) | 2026-09-29 | Core / consistency | [Chat 2 Release] Prevent Issue #9 Python regressions from dirtying source tree |
| [#1106](https://github.com/Trexzo/SpawnPK-RE/issues/1106) | 2026-09-29 | Other / architecture | [Chat 2 Acceptance] HOME Walk-here must route around blocked scenery |
| [#1394](https://github.com/Trexzo/SpawnPK-RE/issues/1394) | 2026-10-01 | Core / consistency | Retire disconnected Monster Spawner session after final tracked NPC finalizes |
| [#1396](https://github.com/Trexzo/SpawnPK-RE/issues/1396) | 2026-10-01 | Core / consistency | Linearize Monster Spawner widget commit and callback with session lifecycle |
| [#1397](https://github.com/Trexzo/SpawnPK-RE/issues/1397) | 2026-10-01 | Gameplay / attribution | Give Monster Spawner settlement-pending state a World-owned retry path |
| [#1401](https://github.com/Trexzo/SpawnPK-RE/issues/1401) | 2026-10-01 | Core / consistency | Compose Monster Spawner widget lifecycle atomicity with WorldPulse settlement retry |
| [#1404](https://github.com/Trexzo/SpawnPK-RE/issues/1404) | 2026-10-01 | Exact client / UI | Require open Monster Spawner UI before accepting its widget actions |
| [#1406](https://github.com/Trexzo/SpawnPK-RE/issues/1406) | 2026-10-01 | Core / consistency | Linearize Monster Spawner UI-close revocation with widget transaction |
| [#1410](https://github.com/Trexzo/SpawnPK-RE/issues/1410) | 2026-10-01 | Exact client / UI | Revoke Monster Spawner UI ownership when another root interface replaces 41000 |
| [#1412](https://github.com/Trexzo/SpawnPK-RE/issues/1412) | 2026-10-01 | Gameplay / attribution | Revoke Monster Spawner ownership when Item Library root 47500 opens |
| [#1413](https://github.com/Trexzo/SpawnPK-RE/issues/1413) | 2026-10-01 | Exact client / UI | Compose Dev Panel equipment/death root publication with Monster Spawner ownership |
| [#1415](https://github.com/Trexzo/SpawnPK-RE/issues/1415) | 2026-10-01 | Gameplay / attribution | Revoke Monster Spawner ownership when Comp Cape Customize root 63036 opens |
| [#1417](https://github.com/Trexzo/SpawnPK-RE/issues/1417) | 2026-10-01 | Gameplay / attribution | Revoke Monster Spawner ownership when Make-over Mage designer root 3559 opens |
| [#1418](https://github.com/Trexzo/SpawnPK-RE/issues/1418) | 2026-10-01 | Gameplay / attribution | Revoke Monster Spawner when Dev Panel root opens from command route |
| [#1419](https://github.com/Trexzo/SpawnPK-RE/issues/1419) | 2026-10-01 | Gameplay / attribution | Revoke Monster Spawner ownership when bank overlay root 5292 opens |
| [#1421](https://github.com/Trexzo/SpawnPK-RE/issues/1421) | 2026-10-01 | Gameplay / attribution | Compose Item Library root ownership with Dev Panel command root ownership |
| [#1423](https://github.com/Trexzo/SpawnPK-RE/issues/1423) | 2026-10-01 | Exact client / UI | Close Item Library widget ownership when root 47500 is replaced |
| [#1424](https://github.com/Trexzo/SpawnPK-RE/issues/1424) | 2026-10-01 | Gameplay / attribution | Revoke Monster Spawner on Completionist cape Customize root |
| [#1426](https://github.com/Trexzo/SpawnPK-RE/issues/1426) | 2026-10-01 | Gameplay / attribution | Revoke Monster Spawner on immediate and deferred Bank root open |
| [#1428](https://github.com/Trexzo/SpawnPK-RE/issues/1428) | 2026-10-01 | Gameplay / attribution | Revoke Monster Spawner on Make-over Mage designer root |
| [#1431](https://github.com/Trexzo/SpawnPK-RE/issues/1431) | 2026-10-01 | Other / architecture | Retire hidden Dev Panel ownership when a competing root replaces it |
| [#1435](https://github.com/Trexzo/SpawnPK-RE/issues/1435) | 2026-10-01 | Other / architecture | Retire hidden BankState ownership when a non-bank root replaces 5292 |
| [#1437](https://github.com/Trexzo/SpawnPK-RE/issues/1437) | 2026-10-01 | Other / architecture | Retire hidden Completionist cape ownership on competing root replacement |
| [#1439](https://github.com/Trexzo/SpawnPK-RE/issues/1439) | 2026-10-01 | Other / architecture | Retire hidden Make-over designer ownership on competing root replacement |
| [#1441](https://github.com/Trexzo/SpawnPK-RE/issues/1441) | 2026-10-01 | Core / consistency | Give Trade UI two-session root ownership and safe competing-root retirement |
| [#1443](https://github.com/Trexzo/SpawnPK-RE/issues/1443) | 2026-10-01 | Core / consistency | Make Trade start replacement failure-atomic against existing live trades |
| [#1444](https://github.com/Trexzo/SpawnPK-RE/issues/1444) | 2026-10-01 | Core / consistency | Make session root replacement atomic for Monster Spawner and Item Library ownership |
| [#1445](https://github.com/Trexzo/SpawnPK-RE/issues/1445) | 2026-10-01 | Core / consistency | Make two-session Trade root opening failure-atomic |
| [#1447](https://github.com/Trexzo/SpawnPK-RE/issues/1447) | 2026-10-01 | Core / consistency | Fail closed if Trade confirmation root publication fails |
| [#1450](https://github.com/Trexzo/SpawnPK-RE/issues/1450) | 2026-10-01 | Core / consistency | Compose Trade root failure handling with deadlock-safe competing-root ownership |
| [#1452](https://github.com/Trexzo/SpawnPK-RE/issues/1452) | 2026-10-01 | Gameplay / attribution | Make Item Library root publication state commit failure-atomic |
| [#1454](https://github.com/Trexzo/SpawnPK-RE/issues/1454) | 2026-10-01 | Other / architecture | Make Bank root target-state commit failure-atomic |
| [#1455](https://github.com/Trexzo/SpawnPK-RE/issues/1455) | 2026-10-01 | Other / architecture | Make command-driven Dev Panel target publication failure-atomic |
| [#1463](https://github.com/Trexzo/SpawnPK-RE/issues/1463) | 2026-10-01 | Core / consistency | [Chat 1] Compose green Trade, Item Library, and Bank target atomicity |
| [#1542](https://github.com/Trexzo/SpawnPK-RE/issues/1542) | 2026-10-01 | Exact client / UI | Make batched packet-81 transform commit/abort aware |
| [#1550](https://github.com/Trexzo/SpawnPK-RE/issues/1550) | 2026-10-01 | Core / consistency | Close packet-81 transport/semantic commit ownership race |
| [#1554](https://github.com/Trexzo/SpawnPK-RE/issues/1554) | 2026-10-01 | Exact client / UI | Fail closed when batched packet-81 semantic preparation is rejected as stale |
| [#1560](https://github.com/Trexzo/SpawnPK-RE/issues/1560) | 2026-10-01 | Core / consistency | Make unbatched packet81 transport and semantic publication atomic |
| [#1561](https://github.com/Trexzo/SpawnPK-RE/issues/1561) | 2026-10-01 | Core / consistency | Linearize packet-81 staging with writer batch lifetime |
| [#1570](https://github.com/Trexzo/SpawnPK-RE/issues/1570) | 2026-10-01 | Exact client / UI | Freeze packet81 NO_CONTEXT classification across concurrent registration |
| [#1572](https://github.com/Trexzo/SpawnPK-RE/issues/1572) | 2026-10-02 | Exact client / UI | Fail closed if prepared packet81 transform throws |
| [#1573](https://github.com/Trexzo/SpawnPK-RE/issues/1573) | 2026-10-02 | Exact client / UI | Do not surface post-commit packet81 relay failure as packet publication failure |
| [#1574](https://github.com/Trexzo/SpawnPK-RE/issues/1574) | 2026-10-02 | Core / consistency | Linearize packet81 prepared commit with writer-context retirement |
| [#1580](https://github.com/Trexzo/SpawnPK-RE/issues/1580) | 2026-10-02 | Core / consistency | Linearize SharedNpc relay flush with viewer-context retirement |
| [#1583](https://github.com/Trexzo/SpawnPK-RE/issues/1583) | 2026-10-02 | Core / consistency | Linearize SharedNpc remote-pet/canonical sync with viewer-context retirement |
| [#1586](https://github.com/Trexzo/SpawnPK-RE/issues/1586) | 2026-10-02 | Other / architecture | Make remote pet mirror add-plus-mask publication failure-atomic |
| [#1590](https://github.com/Trexzo/SpawnPK-RE/issues/1590) | 2026-10-02 | Other / architecture | Preserve FIFO ordering after a remote mirror mask is deferred |
| [#1593](https://github.com/Trexzo/SpawnPK-RE/issues/1593) | 2026-10-02 | Core / consistency | Make deferred remote mirror mask retry transport-retractable |
| [#1598](https://github.com/Trexzo/SpawnPK-RE/issues/1598) | 2026-10-02 | Core / consistency | Make remote mirror add publication transport-retractable |
| [#1601](https://github.com/Trexzo/SpawnPK-RE/issues/1601) | 2026-10-02 | Core / consistency | Make remote mirror movement and removal publication transport-retractable |
| [#1603](https://github.com/Trexzo/SpawnPK-RE/issues/1603) | 2026-10-02 | Core / consistency | Make SharedNpc presentation-event mask delivery transport-retractable |
| [#1604](https://github.com/Trexzo/SpawnPK-RE/issues/1604) | 2026-10-02 | Core / consistency | [Chat 2] Compose mirror add/move/remove retractability onto selected #1599 |
| [#1612](https://github.com/Trexzo/SpawnPK-RE/issues/1612) | 2026-10-02 | Core / consistency | Preserve SharedNpc rebind authority across retractable cleanup failure |
| [#1616](https://github.com/Trexzo/SpawnPK-RE/issues/1616) | 2026-10-02 | Core / consistency | Preserve retained SharedNpc context through runtime-binding rollback |
| [#1617](https://github.com/Trexzo/SpawnPK-RE/issues/1617) | 2026-10-02 | Core / consistency | Make Player81 + SharedNpc runtime rebind one failure-atomic binding transaction |
| [#1619](https://github.com/Trexzo/SpawnPK-RE/issues/1619) | 2026-10-02 | Core / consistency | Make late LocalSession runtime registration rollback preserve prior Trade/runtime authority |
| [#1623](https://github.com/Trexzo/SpawnPK-RE/issues/1623) | 2026-10-02 | Gameplay / attribution | [Chat 1] Restore retryable SharedNpc retention regression on #1621 cumulative |
| [#1625](https://github.com/Trexzo/SpawnPK-RE/issues/1625) | 2026-10-02 | Core / consistency | Make terminal runtime-binding failure retire broken-writer authority without further writes |
| [#1627](https://github.com/Trexzo/SpawnPK-RE/issues/1627) | 2026-10-02 | Core / consistency | Retire peer runtime if terminal Trade cleanup close also fails |
| [#1629](https://github.com/Trexzo/SpawnPK-RE/issues/1629) | 2026-10-02 | Core / consistency | Fence terminal Trade peer-close against peer generation change |
| [#1630](https://github.com/Trexzo/SpawnPK-RE/issues/1630) | 2026-10-02 | Gameplay / attribution | Scope terminal SharedNpc presentation-event cleanup to exact source generation |
| [#1631](https://github.com/Trexzo/SpawnPK-RE/issues/1631) | 2026-10-02 | Core / consistency | Fail-close terminal Trade peer on competing-root retirement |
| [#1632](https://github.com/Trexzo/SpawnPK-RE/issues/1632) | 2026-10-02 | Gameplay / attribution | Preserve terminal SharedNpc writer sentinel during later unregister |
| [#1635](https://github.com/Trexzo/SpawnPK-RE/issues/1635) | 2026-10-02 | Core / consistency | Fail-close terminal writer when ordinary Trade cancellation close fails |
| [#1637](https://github.com/Trexzo/SpawnPK-RE/issues/1637) | 2026-10-02 | Core / consistency | Do not retouch terminal Trade writer after confirmation-root publication failure |
| [#1638](https://github.com/Trexzo/SpawnPK-RE/issues/1638) | 2026-10-02 | Core / consistency | Fence ordinary Trade cancellation closes to exact current participant contexts |
| [#1640](https://github.com/Trexzo/SpawnPK-RE/issues/1640) | 2026-10-02 | Core / consistency | Fail-close direct Trade accept-status writer on one-sided publication failure |
| [#1641](https://github.com/Trexzo/SpawnPK-RE/issues/1641) | 2026-10-02 | Core / consistency | Prevent partial direct-writer Trade final commit publication |
| [#1642](https://github.com/Trexzo/SpawnPK-RE/issues/1642) | 2026-10-02 | Core / consistency | Prevent partial direct-writer Trade offer postimage publication |
| [#1643](https://github.com/Trexzo/SpawnPK-RE/issues/1643) | 2026-10-02 | Core / consistency | Do not retouch failed Trade writer during start-root rollback |
| [#1644](https://github.com/Trexzo/SpawnPK-RE/issues/1644) | 2026-10-02 | Core / consistency | Fail-close terminal old peer during successful Trade replacement |
| [#1647](https://github.com/Trexzo/SpawnPK-RE/issues/1647) | 2026-10-02 | Core / consistency | Fail-close terminal Trade writer when Offer-X or Remove-X prompt publication fails |
| [#1648](https://github.com/Trexzo/SpawnPK-RE/issues/1648) | 2026-10-02 | Core / consistency | Align runtime binding failure regression with terminal player-options wrapper |
| [#1651](https://github.com/Trexzo/SpawnPK-RE/issues/1651) | 2026-10-02 | Core / consistency | Fail-close writer if Trade start-root rollback restore or close fails |
| [#1653](https://github.com/Trexzo/SpawnPK-RE/issues/1653) | 2026-10-02 | Core / consistency | Latch terminal ServerPacketWriter and stop LocalSession gameplay after remote retirement |
| [#1659](https://github.com/Trexzo/SpawnPK-RE/issues/1659) | 2026-10-02 | Core / consistency | Retire full runtime when SharedNpc presentation-event publication fails terminally |
| [#1674](https://github.com/Trexzo/SpawnPK-RE/issues/1674) | 2026-10-02 | Core / consistency | Release packet81 in-flight gate before post-commit SharedNpc relay |
| [#1675](https://github.com/Trexzo/SpawnPK-RE/issues/1675) | 2026-10-02 | Core / consistency | [Chat 1] Align Trade generation-fence regression with queue-backed atomic offer policy |
| [#1678](https://github.com/Trexzo/SpawnPK-RE/issues/1678) | 2026-10-02 | Core / consistency | Align runtime-binding X-prompt terminal regression with queue-backed offer policy |
| [#1684](https://github.com/Trexzo/SpawnPK-RE/issues/1684) | 2026-10-02 | Core / consistency | Align SharedNpc rebind retractability fixture with Player81 option prepublication |
| [#1687](https://github.com/Trexzo/SpawnPK-RE/issues/1687) | 2026-10-02 | Core / consistency | Fence successful competing-root Trade peer close to exact generation |
| [#1689](https://github.com/Trexzo/SpawnPK-RE/issues/1689) | 2026-10-02 | Core / consistency | Fence successful Trade replacement old-peer close to exact generation |
| [#1691](https://github.com/Trexzo/SpawnPK-RE/issues/1691) | 2026-10-02 | Core / consistency | Remove Trade cleanup generation-fence mutation-lock inversion |
| [#1695](https://github.com/Trexzo/SpawnPK-RE/issues/1695) | 2026-10-02 | Core / consistency | Remove final Trade commit mutation-lock inversion |
| [#1696](https://github.com/Trexzo/SpawnPK-RE/issues/1696) | 2026-10-02 | Other / architecture | [Chat 1] Compose current main docs/CI into the cumulative source frontier |
| [#1698](https://github.com/Trexzo/SpawnPK-RE/issues/1698) | 2026-10-02 | Core / consistency | Align Trade generation-fence races with registry linearization |
| [#1701](https://github.com/Trexzo/SpawnPK-RE/issues/1701) | 2026-10-02 | Core / consistency | Refresh Trade lock-order comments after registry fencing |
| [#1703](https://github.com/Trexzo/SpawnPK-RE/issues/1703) | 2026-10-02 | Core / consistency | Run Trade commit persistence callbacks outside TradeService locks |
| [#1705](https://github.com/Trexzo/SpawnPK-RE/issues/1705) | 2026-10-02 | Core / consistency | Retire SharedNpc terminal runtime outside relay monitor |
| [#1707](https://github.com/Trexzo/SpawnPK-RE/issues/1707) | 2026-10-02 | Core / consistency | Retire full runtime on terminal SharedNpc mirror sync failure |
| [#1709](https://github.com/Trexzo/SpawnPK-RE/issues/1709) | 2026-10-02 | Gameplay / attribution | Latch terminal SharedNpc event writer before relay unlock |
| [#1711](https://github.com/Trexzo/SpawnPK-RE/issues/1711) | 2026-10-02 | Gameplay / attribution | Make SharedNpc fail-close latch the exact writer terminal immediately |
| [#1714](https://github.com/Trexzo/SpawnPK-RE/issues/1714) | 2026-10-02 | Gameplay / attribution | Preserve SharedNpc terminal sentinel when ordinary unregister cleanup fails |
| [#1715](https://github.com/Trexzo/SpawnPK-RE/issues/1715) | 2026-10-02 | Core / consistency | Make Player81 trade-request notification transport-safe |
| [#1717](https://github.com/Trexzo/SpawnPK-RE/issues/1717) | 2026-10-02 | Gameplay / attribution | Purge detached SharedNpc terminal sentinels on World close |
| [#1720](https://github.com/Trexzo/SpawnPK-RE/issues/1720) | 2026-10-02 | Core / consistency | Make Player81 skill-update publication transport-safe |
| [#1723](https://github.com/Trexzo/SpawnPK-RE/issues/1723) | 2026-10-02 | Gameplay / attribution | Fix terminal SharedNpc relay no-retouch regression lock assumption |
| [#1726](https://github.com/Trexzo/SpawnPK-RE/issues/1726) | 2026-10-03 | Core / consistency | Latch terminal Trade cleanup writers before PlayerRegistry unlock |
| [#1728](https://github.com/Trexzo/SpawnPK-RE/issues/1728) | 2026-10-03 | Exact client / UI | Latch ordinary ServerPacketWriter terminal on non-batched flush failure |
| [#1730](https://github.com/Trexzo/SpawnPK-RE/issues/1730) | 2026-10-03 | Core / consistency | Abort failed LocalSession world-tick batch commit |
| [#1732](https://github.com/Trexzo/SpawnPK-RE/issues/1732) | 2026-10-03 | Core / consistency | Latch session writer terminal on outbound socket drain failure |
| [#1734](https://github.com/Trexzo/SpawnPK-RE/issues/1734) | 2026-10-03 | Core / consistency | Quiesce LocalSession world-tick callbacks before runtime teardown |
| [#1736](https://github.com/Trexzo/SpawnPK-RE/issues/1736) | 2026-10-03 | Core / consistency | Gate LocalSession realtime pet callbacks during teardown |
| [#1738](https://github.com/Trexzo/SpawnPK-RE/issues/1738) | 2026-10-03 | Core / consistency | Reject pet realtime callback entry after session writer terminal latch |
| [#1740](https://github.com/Trexzo/SpawnPK-RE/issues/1740) | 2026-10-03 | Exact client / UI | [Chat 1] Replay exact-v308 Walk-here radius-2 onto current cumulative frontier |
| [#1742](https://github.com/Trexzo/SpawnPK-RE/issues/1742) | 2026-10-03 | Core / consistency | Update external-runtime docs for Walk-here replay hashes |
| [#1743](https://github.com/Trexzo/SpawnPK-RE/issues/1743) | 2026-10-03 | Other / architecture | [Chat 1] Bind current cumulative authority to latest main ancestry |
| [#1747](https://github.com/Trexzo/SpawnPK-RE/issues/1747) | 2026-10-03 | Core / consistency | Run hosted contract checks for external-runtime doc changes |
| [#1830](https://github.com/Trexzo/SpawnPK-RE/issues/1830) | 2026-10-04 | Core / consistency | Fence legacy combat tick accounting behind region-stream commit |
| [#1833](https://github.com/Trexzo/SpawnPK-RE/issues/1833) | 2026-10-04 | Other / architecture | Bind private cumulative focused-count claims to the live manifest |
| [#1836](https://github.com/Trexzo/SpawnPK-RE/issues/1836) | 2026-10-04 | Other / architecture | Stop describing the moving focused manifest as frozen #431 |
| [#1839](https://github.com/Trexzo/SpawnPK-RE/issues/1839) | 2026-10-04 | Other / architecture | Normalize source-contract line endings for Player81 NO_CONTEXT regression |
| [#1862](https://github.com/Trexzo/SpawnPK-RE/issues/1862) | 2026-10-05 | Other / architecture | [Playability R5] Recover exact loadout-editor target selection authority |
| [#2162](https://github.com/Trexzo/SpawnPK-RE/issues/2162) | 2026-10-07 | Exact client / UI | Gameplay G18.4: bind exact Track widget to LocalLab Easy PvM activity |
| [#2268](https://github.com/Trexzo/SpawnPK-RE/issues/2268) | 2026-10-09 | Core / consistency | Gameplay G21.48: write-ahead strict checkpoint intent fences crash gap before account replacement |
| [#2274](https://github.com/Trexzo/SpawnPK-RE/issues/2274) | 2026-10-09 | Core / consistency | Gameplay G21.49: reconcile divergent G21.47–48 Mailbox durability branches before promotion |
| [#2276](https://github.com/Trexzo/SpawnPK-RE/issues/2276) | 2026-10-09 | Core / consistency | Gameplay G21.50: detect conflicting concurrent Mailbox negative markers in read-only forensics |
| [#2278](https://github.com/Trexzo/SpawnPK-RE/issues/2278) | 2026-10-09 | Core / consistency | Gameplay G21.51: bound all negative Mailbox marker diagnostic reads before allocation |
| [#2280](https://github.com/Trexzo/SpawnPK-RE/issues/2280) | 2026-10-09 | Core / consistency | Gameplay G21.52: detect single-marker admission-sidecar membership races in Mailbox read-only forensics |
| [#2282](https://github.com/Trexzo/SpawnPK-RE/issues/2282) | 2026-10-09 | Gameplay / attribution | Gameplay G21.53: verify single negative-marker content across the entire forensic read |
| [#2284](https://github.com/Trexzo/SpawnPK-RE/issues/2284) | 2026-10-09 | Core / consistency | Gameplay G21.54: detect account path resolver drift during read-only Mailbox forensics |
| [#2286](https://github.com/Trexzo/SpawnPK-RE/issues/2286) | 2026-10-09 | Core / consistency | Gameplay G21.55: anchor inner Mailbox forensic marker lookup to outer account path |
| [#2288](https://github.com/Trexzo/SpawnPK-RE/issues/2288) | 2026-10-09 | Core / consistency | Gameplay G21.56: anchor live strict PREPARED write-ahead and uncertainty markers to the exact account file |
| [#2290](https://github.com/Trexzo/SpawnPK-RE/issues/2290) | 2026-10-09 | Core / consistency | Gameplay G21.57: pin guarded normal World-save review fences to the replacement account path |
| [#2292](https://github.com/Trexzo/SpawnPK-RE/issues/2292) | 2026-10-09 | Core / consistency | Gameplay G21.58: pin file-backed World session admission and account load to one negative-fenced path |
| [#2294](https://github.com/Trexzo/SpawnPK-RE/issues/2294) | 2026-10-09 | Core / consistency | Gameplay G21.59: serialize admitted World account reads with cooperating marker and account publication |
| [#2296](https://github.com/Trexzo/SpawnPK-RE/issues/2296) | 2026-10-09 | Core / consistency | Gameplay G21.60: reject swapped, deleted or symlinked account files during admitted World session reads |
| [#2336](https://github.com/Trexzo/SpawnPK-RE/issues/2336) | 2026-10-10 | Core / consistency | [Plugin runtime] In-flight callback failure diagnostic can race terminal cleanup in hosted CI |
| [#2526](https://github.com/Trexzo/SpawnPK-RE/issues/2526) | 2026-10-13 | Coordination / release gate | CHAT 2 HANDOFF · gameplay ownership and core-server boundary (live GitHub coordination) |
| [#2540](https://github.com/Trexzo/SpawnPK-RE/issues/2540) | 2026-10-13 | Core / consistency | Core follow-up: durable fail-closed account admission after a failed final World save (design + regression) |
| [#2547](https://github.com/Trexzo/SpawnPK-RE/issues/2547) | 2026-10-13 | Gameplay / attribution | Define Daily Money Making activity lifetime across player generation and World reconnect |
| [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558) | 2026-10-14 | Exact client / UI | [Chat 2 G18.4.6] Recover canonical kill-credit origin versus Monster Spawner spawn recipient |
| [#2559](https://github.com/Trexzo/SpawnPK-RE/issues/2559) | 2026-10-14 | Core / consistency | Core G21.180 follow-up: cross-World named-account writer ownership and stale successful publication |
| [#2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587) | 2026-10-14 | Gameplay / attribution | [Chat 2 G18.5.1] Terminal observer failure after drop settlement has no replay path |

## Close criteria

Use `completed` only after verified accepted implementation in retained canonical branch/`main` AND acceptance proof; `duplicate` only after explicit equivalence and linking canonical issue; `not_planned` for explicitly retired or superseded scopes only with documentation of any still-open behavior gap. Experimental negative regression proof does not itself solve a recovery or gameplay semantics issue. Preserve #392/#2526 and unresolved #2540/#2559/#2587/#2558.