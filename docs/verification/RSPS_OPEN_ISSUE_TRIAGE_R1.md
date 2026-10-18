# RSPS entire open-issue backlog — owner triage R1

Baseline canonical #2962 @ `20b703d8`; account branch #2963 @ `8a578e28` (914 focused CI Linux + Windows SUCCESS); real-client acceptance draft #2964 @ `93e58c8e`.

This is an inventory and execution plan, **NOT proof that every item is fixed**. Issues were retrieved live (74 open); no bulk closures or merges. PRs exceed a 100-result single-search page. Categorization is rough and must be refined by source-specific evidence. An issue moves to CLOSED only on exact-head verified acceptance, not because a cumulative title looks similar.

## Work order

1. P0: run real-v308 acceptance, preserve account data and eliminate ambiguous save admission (#2540/#2559), then consumer-specific NPC reward disposition (#2587/#2558).
2. P1: consolidate multiplayer/Trade/SharedNpc errors into deterministic sessions, test disconnects and ownership; fix first live gameplay blockers (#1106/#427/#1862); run pinned acceptance script.
3. P2: custom-asset pipeline, architecture refactors, remaining release hardening. No inferred original-server behavior.

## Open issues (all 74)

### P0 Account integrity (3)

- [#2276](https://github.com/Trexzo/SpawnPK-RE/issues/2276) — Gameplay G21.50: detect conflicting concurrent Mailbox negative markers in read-only forensics
- [#2540](https://github.com/Trexzo/SpawnPK-RE/issues/2540) — Core follow-up: durable fail-closed account admission after a failed final World save (design + regression)
- [#2559](https://github.com/Trexzo/SpawnPK-RE/issues/2559) — Core G21.180 follow-up: cross-World named-account writer ownership and stale successful publication

### P0 Reward correctness (3)

- [#2547](https://github.com/Trexzo/SpawnPK-RE/issues/2547) — Define Daily Money Making activity lifetime across player generation and World reconnect
- [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558) — [Chat 2 G18.4.6] Recover canonical kill-credit origin versus Monster Spawner spawn recipient
- [#2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587) — [Chat 2 G18.5.1] Terminal observer failure after drop settlement has no replay path

### P1 Multiplayer/runtime (43)

- [#1450](https://github.com/Trexzo/SpawnPK-RE/issues/1450) — Compose Trade root failure handling with deadlock-safe competing-root ownership
- [#1616](https://github.com/Trexzo/SpawnPK-RE/issues/1616) — Preserve retained SharedNpc context through runtime-binding rollback
- [#1619](https://github.com/Trexzo/SpawnPK-RE/issues/1619) — Make late LocalSession runtime registration rollback preserve prior Trade/runtime authority
- [#1629](https://github.com/Trexzo/SpawnPK-RE/issues/1629) — Fence terminal Trade peer-close against peer generation change
- [#1630](https://github.com/Trexzo/SpawnPK-RE/issues/1630) — Scope terminal SharedNpc presentation-event cleanup to exact source generation
- [#1638](https://github.com/Trexzo/SpawnPK-RE/issues/1638) — Fence ordinary Trade cancellation closes to exact current participant contexts
- [#1640](https://github.com/Trexzo/SpawnPK-RE/issues/1640) — Fail-close direct Trade accept-status writer on one-sided publication failure
- [#1641](https://github.com/Trexzo/SpawnPK-RE/issues/1641) — Prevent partial direct-writer Trade final commit publication
- [#1642](https://github.com/Trexzo/SpawnPK-RE/issues/1642) — Prevent partial direct-writer Trade offer postimage publication
- [#1643](https://github.com/Trexzo/SpawnPK-RE/issues/1643) — Do not retouch failed Trade writer during start-root rollback
- [#1644](https://github.com/Trexzo/SpawnPK-RE/issues/1644) — Fail-close terminal old peer during successful Trade replacement
- [#1647](https://github.com/Trexzo/SpawnPK-RE/issues/1647) — Fail-close terminal Trade writer when Offer-X or Remove-X prompt publication fails
- [#1648](https://github.com/Trexzo/SpawnPK-RE/issues/1648) — Align runtime binding failure regression with terminal player-options wrapper
- [#1651](https://github.com/Trexzo/SpawnPK-RE/issues/1651) — Fail-close writer if Trade start-root rollback restore or close fails
- [#1653](https://github.com/Trexzo/SpawnPK-RE/issues/1653) — Latch terminal ServerPacketWriter and stop LocalSession gameplay after remote retirement
- [#1659](https://github.com/Trexzo/SpawnPK-RE/issues/1659) — Retire full runtime when SharedNpc presentation-event publication fails terminally
- [#1674](https://github.com/Trexzo/SpawnPK-RE/issues/1674) — Release packet81 in-flight gate before post-commit SharedNpc relay
- [#1675](https://github.com/Trexzo/SpawnPK-RE/issues/1675) — [Chat 1] Align Trade generation-fence regression with queue-backed atomic offer policy
- [#1684](https://github.com/Trexzo/SpawnPK-RE/issues/1684) — Align SharedNpc rebind retractability fixture with Player81 option prepublication
- [#1687](https://github.com/Trexzo/SpawnPK-RE/issues/1687) — Fence successful competing-root Trade peer close to exact generation
- [#1689](https://github.com/Trexzo/SpawnPK-RE/issues/1689) — Fence successful Trade replacement old-peer close to exact generation
- [#1691](https://github.com/Trexzo/SpawnPK-RE/issues/1691) — Remove Trade cleanup generation-fence mutation-lock inversion
- [#1695](https://github.com/Trexzo/SpawnPK-RE/issues/1695) — Remove final Trade commit mutation-lock inversion
- [#1698](https://github.com/Trexzo/SpawnPK-RE/issues/1698) — Align Trade generation-fence races with registry linearization
- [#1701](https://github.com/Trexzo/SpawnPK-RE/issues/1701) — Refresh Trade lock-order comments after registry fencing
- [#1703](https://github.com/Trexzo/SpawnPK-RE/issues/1703) — Run Trade commit persistence callbacks outside TradeService locks
- [#1705](https://github.com/Trexzo/SpawnPK-RE/issues/1705) — Retire SharedNpc terminal runtime outside relay monitor
- [#1707](https://github.com/Trexzo/SpawnPK-RE/issues/1707) — Retire full runtime on terminal SharedNpc mirror sync failure
- [#1709](https://github.com/Trexzo/SpawnPK-RE/issues/1709) — Latch terminal SharedNpc event writer before relay unlock
- [#1711](https://github.com/Trexzo/SpawnPK-RE/issues/1711) — Make SharedNpc fail-close latch the exact writer terminal immediately
- [#1714](https://github.com/Trexzo/SpawnPK-RE/issues/1714) — Preserve SharedNpc terminal sentinel when ordinary unregister cleanup fails
- [#1715](https://github.com/Trexzo/SpawnPK-RE/issues/1715) — Make Player81 trade-request notification transport-safe
- [#1717](https://github.com/Trexzo/SpawnPK-RE/issues/1717) — Purge detached SharedNpc terminal sentinels on World close
- [#1720](https://github.com/Trexzo/SpawnPK-RE/issues/1720) — Make Player81 skill-update publication transport-safe
- [#1723](https://github.com/Trexzo/SpawnPK-RE/issues/1723) — Fix terminal SharedNpc relay no-retouch regression lock assumption
- [#1726](https://github.com/Trexzo/SpawnPK-RE/issues/1726) — Latch terminal Trade cleanup writers before PlayerRegistry unlock
- [#1728](https://github.com/Trexzo/SpawnPK-RE/issues/1728) — Latch ordinary ServerPacketWriter terminal on non-batched flush failure
- [#1730](https://github.com/Trexzo/SpawnPK-RE/issues/1730) — Abort failed LocalSession world-tick batch commit
- [#1732](https://github.com/Trexzo/SpawnPK-RE/issues/1732) — Latch session writer terminal on outbound socket drain failure
- [#1734](https://github.com/Trexzo/SpawnPK-RE/issues/1734) — Quiesce LocalSession world-tick callbacks before runtime teardown
- [#1736](https://github.com/Trexzo/SpawnPK-RE/issues/1736) — Gate LocalSession realtime pet callbacks during teardown
- [#1738](https://github.com/Trexzo/SpawnPK-RE/issues/1738) — Reject pet realtime callback entry after session writer terminal latch
- [#2336](https://github.com/Trexzo/SpawnPK-RE/issues/2336) — [Plugin runtime] In-flight callback failure diagnostic can race terminal cleanup in hosted CI

### P1 Playable-client (12)

- [#9](https://github.com/Trexzo/SpawnPK-RE/issues/9) — [Pipeline & Custom Content] Replace Voidglass R3 Compositor Trial with Custom Cache/Model Pipeline
- [#427](https://github.com/Trexzo/SpawnPK-RE/issues/427) — [Chat 2][Exact v308] Recover uncovered SpawnPK application/UI contracts
- [#1005](https://github.com/Trexzo/SpawnPK-RE/issues/1005) — [Chat 1 Release] Bind current release acceptance to a clean exact Git head before build
- [#1015](https://github.com/Trexzo/SpawnPK-RE/issues/1015) — [Chat 1 Release] Require final smoke cleanup before emitting release PASS
- [#1048](https://github.com/Trexzo/SpawnPK-RE/issues/1048) — [Chat 2 Runtime] Bind exact-v308 local-client build to one guarded input identity
- [#1050](https://github.com/Trexzo/SpawnPK-RE/issues/1050) — [Chat 2 Assets] Bind R13 profile build to one guarded exact-v308 client identity
- [#1096](https://github.com/Trexzo/SpawnPK-RE/issues/1096) — [Chat 2] R13 acceptance must build LocalLab server before launch
- [#1106](https://github.com/Trexzo/SpawnPK-RE/issues/1106) — [Chat 2 Acceptance] HOME Walk-here must route around blocked scenery
- [#1412](https://github.com/Trexzo/SpawnPK-RE/issues/1412) — Revoke Monster Spawner ownership when Item Library root 47500 opens
- [#1415](https://github.com/Trexzo/SpawnPK-RE/issues/1415) — Revoke Monster Spawner ownership when Comp Cape Customize root 63036 opens
- [#1417](https://github.com/Trexzo/SpawnPK-RE/issues/1417) — Revoke Monster Spawner ownership when Make-over Mage designer root 3559 opens
- [#1862](https://github.com/Trexzo/SpawnPK-RE/issues/1862) — [Playability R5] Recover exact loadout-editor target selection authority

### P1 Release verification (8)

- [#1](https://github.com/Trexzo/SpawnPK-RE/issues/1) — [Refactor] Reorganize Source Tree & Package Hierarchy (Separate Core & Tests)
- [#1006](https://github.com/Trexzo/SpawnPK-RE/issues/1006) — [Chat 1 Release] Bind final loopback smoke to the exact spawned server PID
- [#1010](https://github.com/Trexzo/SpawnPK-RE/issues/1010) — [Chat 1 Release] Bind final smoke to the cumulative-certified server JAR bytes
- [#1059](https://github.com/Trexzo/SpawnPK-RE/issues/1059) — [Chat 2 Release] Bind cumulative server SHA across wrapper→release in memory
- [#1063](https://github.com/Trexzo/SpawnPK-RE/issues/1063) — [Chat 2 Release] Bind Gradle PASS marker to invocation-owned output, not mutable log path
- [#1079](https://github.com/Trexzo/SpawnPK-RE/issues/1079) — [Chat 2 Release] Treat dialog-number-key sidecar as generated runtime state
- [#1098](https://github.com/Trexzo/SpawnPK-RE/issues/1098) — [Chat 2 Release] Prevent Issue #9 Python regressions from dirtying source tree
- [#1696](https://github.com/Trexzo/SpawnPK-RE/issues/1696) — [Chat 1] Compose current main docs/CI into the cumulative source frontier

### P2 Architecture/other (2)

- [#3](https://github.com/Trexzo/SpawnPK-RE/issues/3) — [Core Architecture] Implement Server Event Bus & Action Dispatcher
- [#1678](https://github.com/Trexzo/SpawnPK-RE/issues/1678) — Align runtime-binding X-prompt terminal regression with queue-backed offer policy

### Coordination (3)

- [#392](https://github.com/Trexzo/SpawnPK-RE/issues/392) — [Canonical] SpawnPK-RE integration and release queue
- [#1743](https://github.com/Trexzo/SpawnPK-RE/issues/1743) — [Chat 1] Bind current cumulative authority to latest main ancestry
- [#2526](https://github.com/Trexzo/SpawnPK-RE/issues/2526) — [Canonical] SpawnPK-RE gameplay and core-server coordination

## Required evidence per resolution

- Git SHA, changed production paths, exact test list/log, rollback/negative case, and real-v308 vs synthetic distinction.
- P0 durability: crash before and after account publication, two independent JVMs, no unsafe auto-recovery; NPC progression: per-consumer ACK and no duplicate grant.
- P1 gameplay: real client/hash, player-visible observed behavior, packet/client and server traces; session/network: disconnection, stale generation and concurrency.
- Source-only Windows CI cannot certify GUI or the 179 original-packet gate. No user credentials in logs.
