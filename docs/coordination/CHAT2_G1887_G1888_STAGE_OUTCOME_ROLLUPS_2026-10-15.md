# G18.8.7 / G18.8.8 — immutable all-time terminal stage control-flow rollups

## Source parent, authority and scope

- Exact CI-GREEN Chat2 [#2705](https://github.com/Trexzo/SpawnPK-RE/pull/2705), SHA `2722b74de68b90ba2f13b3c3cf651f4ff305393f`, [Actions #38772829944](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38772829944) SUCCESS, 552 unique focused, Java11 major55 and full Gradle. Its source itself builds on independently certified cumulative 550 [#2697](https://github.com/Trexzo/SpawnPK-RE/pull/2697).
- **Authority:** `CUSTOM_LOCALLAB_TERMINAL_STAGE_ISOLATION_EXPERIMENT`, default OFF, NOT recovered SpawnPK original kill/credit, packet or historical reward policy.
- Existing G18.8.5 exposed a detached immutable 128-report snapshot to trusted server-package code, with total observed and evicted counts. **Problem:** once a malformed G14/G15/G16 callback leaves the bounded report ring, an operator cannot distinguish all-time `RETURNED`, `THREW` or `SKIPPED_NO_CURRENT_RECIPIENT` without collecting/serializing each earlier snapshot externally. This patch keeps a fixed-size **5-stage × 3-outcome observational rollup** in the same World-local dispatcher.

## Narrow production change

Only `server/src/spk/local/LocalLabTerminalStageIsolation.java` production source changes:
- Private `EnumMap<Stage,long[3]>` initialized for G4/G14/G15/G16/G18, **no callbacks or external handles**.
- After each real report is finalized and appended, update a saturated nonnegative counter for each stage's observed control-flow `RETURNED`, `THREW` or `SKIPPED_NO_CURRENT_RECIPIENT`, inside the existing synchronized ring update. The diagnostic rollup and last-128 report ring share a single synchronization boundary; no World lock or gameplay callback is invoked under the ring lock.
- Snapshot copies every counter into immutable `OutcomeTotals{returned,threw,skipped}` instances in an unmodifiable `Map<Stage,OutcomeTotals>`; package-local `snapshot.stage(Stage)` read method. No caller gets a mutable counter array, map, callback reference or reward persistence object.
- Counts survive **report detail eviction**, but are **not durable across World/JVM close/restart**. Saturation at `Long.MAX_VALUE` deliberately avoids signed overflow and does not imply an event sequence identifier. This is NOT an idempotency ledger, original credit authority, applied-objective counter or event outbox.
- Unchanged: existing default-OFF switch, G1, NPC/ground settlement, World sessions, G4/G14/G15/G16/G18 delivery, DMM objective, original client, G21 account/OS/RAW lock, ACTIVE marker, main and frozen R25.

## New hosted regression gates

| Test | Genuine installed runtime behavior | Required marker |
| --- | --- | --- |
| **G1887** | Real public opt-in factory with 130 different actual NPC deaths, actual G1 scheduled respawn and 130 G1 record receipts/ground coins. One malformed **test-only G14** throws on every terminal while healthy callback paths G4/G15/G16/G18 return normally even without active objectives. After the first 2 detailed reports are evicted, total `G14.THREW=130` and `G18.RETURNED=130` still reported. Returned all-time map unmodifiable; second World starts zero. | `G1887_ALLTIME_STAGE_OUTCOMES_SURVIVE_128_REPORT_EVICTION_PASS` |
| **G1888** | First NPC genuinely dies and settles under live owner, stage outcomes all RETURNED and one G1 receipt. G1 real delayed respawn creates a **different** NPC, original WorldPlayer unregisters before second death, no successor name; actual second death still settles a coin while G1 does not credit absent recipient and all five stages are SKIPPED. Snapshot exactly RETURNED1/SKIPPED1/THREW0 per stage; old NPC duplicate veto, two settled coins, old snapshot unchanged. | `G1888_TERMINAL_STAGE_RETURNED_SKIPPED_ROLLUP_PASS` |

Two new Java test classes, one production class, `server/build.gradle` ONE new test entry per class and both guards **552→554 unique**, two targeted JavaExec tasks, this source note. **Fresh hosted exact-head Java11 major55/full Gradle, 554/554 focused, both markers** needed; remain DRAFT/unmerged until certified.

## Nonclaims and open issues

- A callback **RETURNED** does not mean a reward was granted (G1883 proves it can be no-op without an active objective). **THREW** can be after state mutation. **SKIPPED** only describes current player/generation at dispatch. Therefore totals **cannot safely direct replay, give item/XP grants or clear an account review marker**.
- This bounded memory observer intentionally cannot supply durable event identity across process crash, per-stage atomic objective mutation+ACK, global exactly-once semantics, or authenticated attacker reward attribution.
- [Durable per-consumer event/outbox #2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587) and [original SpawnPK kill attribution #2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558) remain OPEN. G21 account hardening is Chat1-owned and is not altered here.
