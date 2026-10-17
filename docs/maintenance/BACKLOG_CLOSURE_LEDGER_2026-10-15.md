# Backlog resolution ledger — 2026-10-15

## Current GitHub totals

Open **113 issues** and **323 pull requests** at latest creation-day-partitioned GitHub recheck, compared to initial indexed snapshot **149 issues / 344 PRs**. These are live snapshots: other workstreams may independently open/close items between counts; no assertion that one chat caused the entire net change. Issue enumeration uses the fourteen actual open-issue creation days; PR enumeration uses eleven creation days with a maximum of 100 per day, no day truncated. Index snapshots remain in the adjacent files.

## Accepted implementation tickets — narrowly scoped and NOT shipped to main

These open issues were closed `completed` only after a direct CI-green fix/selected descendant and exact verified **zero-behind Git ancestor** of the retained experimental server #2646, or in one specific case a documented composition that preserved the exact test and source scope. Every issue now has its own evidence comment and links.

| Technical family | Closed issue IDs |
|---|---|
| Monster Spawner lifecycle/UI and settlement retry | #1394, #1396, #1397, #1401, #1404, #1406, #1410 |
| SharedNpc transport and relay/packet81 context ownership | #1574, #1580, #1583, #1590, #1593, #1598, #1601, #1603, #1604 |
| Packet81 staged/unbatched transport/semantic atomics | #1542, #1550, #1554, #1560, #1561, #1570, #1572, #1573 |
| Region-stream combat tick and source/build evidence | #1830, #1833, #1836, #1839 |
| Walk-here cumulative source/documentation/CI (NOT external human acceptance) | #1740, #1742, #1747 |
| Guarded account session reads / strict write-ahead *negative* evidence | #2268, #2292, #2294, #2296 |

Acceptance of these **implementation** issue tickets means the experimental, retained cumulative review train contains the accepted code with demonstrated hosted checks. It does not assert that the implementation is already on `main`, that every actual-client/user-human test passed, or that positive account recovery is safe. The legitimate broader unresolved blockers below must remain open.

## Safely closed redundant PR reviews (superseded, UNMERGED)

The following were independently closed with explicit successor/evidence comments and preserved source branches. They were NOT merged, and their closure does not automatically resolve unrelated feature issues:

- #2609, #2611, #2615 — exact 0-behind ancestor of retained #2646
- #2625, #2628, #2630, #2631, #2634, #2636 — exact 0-behind ancestors of retained #2643 at prior cleanup
- #2638, #2640, #2641, #2642, #2643, #2644, #2645 — retained ancestry or exact donor Java/test and documentation blob identity, with selected successor #2646

**Do not bulk-close diverged donor PRs without byte-level preservation checks**. PR #2608 and several parallel Chat2 gameplay PRs diverge from the selected history, so mere similarity of test descriptions does not justify closing or deletion. Original branch history and all comments are retained.

## Existing and latest CI-green experimental review trains

- [#2646](https://github.com/Trexzo/SpawnPK-RE/pull/2646), commit `3673fa0b81713e515ee9f53988b9ff8dcd93a9e7`, [Actions #38725404483](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38725404483) SUCCESS, **513** unique focused Java11 + full Gradle. G21.205 core + G18.6.6 Chat2 joint baseline. Draft/unmerged.
- [#2648](https://github.com/Trexzo/SpawnPK-RE/pull/2648), commit `228701cc130ebad0e193e130b40e3f7f37c75f2b`, [Actions #38728601770](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38728601770) SUCCESS, **514** unique focused, Chat2 G18.6.7 donor extension on #2646. Do not close #2646 simply because this independent gameplay donor exists; future integration work may be concurrent.
- [#2647](https://github.com/Trexzo/SpawnPK-RE/pull/2647) was merged via documentation-only squash commit `31e2b048f74d5640fe9007007112b2c5e3f69931`. This does not include either runtime train.

## Do not close without real acceptance

- [#1847](https://github.com/Trexzo/SpawnPK-RE/pull/1847) older frozen R25 promotion: 319 changed files, requires actual human HOME Walk-here and minimap acceptance [#1106](https://github.com/Trexzo/SpawnPK-RE/issues/1106) on exact runtime. Hosted Java source passes do not substitute.
- [#2540](https://github.com/Trexzo/SpawnPK-RE/issues/2540), [#2559](https://github.com/Trexzo/SpawnPK-RE/issues/2559): durable across-process exact owner/epoch, all writer lanes and crash-safe final receipt + negative ACTIVE review policy unresolved. G21.20x in-memory positive/negative regressions do not grant account recovery or marker deletion.
- [#2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587): per-consumer replay-safe post-settlement outbox remains missing. Do not duplicate G1 drops/rewards.
- [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558): original SpawnPK kill-credit attacker/contributor authority unknown. No inferred historical rule.
- [#2547](https://github.com/Trexzo/SpawnPK-RE/issues/2547): explicit DMM World/session lifetime contract undecided. [#427](https://github.com/Trexzo/SpawnPK-RE/issues/427): exact-v308 UI/application contract gaps. [#1](https://github.com/Trexzo/SpawnPK-RE/issues/1) and [#3](https://github.com/Trexzo/SpawnPK-RE/issues/3): substantive architecture epics, not closed by regression count.
- [#2526](https://github.com/Trexzo/SpawnPK-RE/issues/2526) and [#392](https://github.com/Trexzo/SpawnPK-RE/issues/392) remain active cross-chat and release coordination nodes.

## Next execution

Continue per-issue source/test verification and per-PR ancestry/donor blob audit. Do not attempt one mass merge of hundreds of chained experimental PRs. Prioritize fixing real blockers and gaining user-tested R25 runtime acceptance, then promote verified source only after release checks and explicit authorization. Refresh this ledger after each bounded, evidence-backed wave.