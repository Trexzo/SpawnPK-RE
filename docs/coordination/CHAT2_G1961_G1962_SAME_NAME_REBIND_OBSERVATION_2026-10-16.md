# G19.6.1/62 — source-backed same-name terminal provenance, NO silent policy rewrite

**Exact parent:** [#2828](https://github.com/Trexzo/SpawnPK-RE/pull/2828) at `79960adb2bc5a2174e2ccfdeffa61eb4c2036c70`, certified 687/687 (cumulative Chat1 G21.284 + Chat2 G1960). Contains existing G1847/G1863 World-local username task-continuity regressions.

## Critical correction to superseded G1961 policy attempt

An earlier attempt [#2831](https://github.com/Trexzo/SpawnPK-RE/pull/2831) changed `G1MonsterSpawnerPvmLoopService`, default sequential consumer execution and experimental isolated delivery to **block** credits when the original spawn recipient WorldPlayer had been replaced by another player of the same username. The exact-head hosted regression failed at **G1847MonsterSpawnerNameReuseTerminalAttributionTest / G1863**, where the established LocalLab compatibility test explicitly requires replacement B to complete A's active World-owned G4 task, receive B's G1 receipt and advance the existing World-local DMM objective. The branch failed at focused test 277/689, before reaching G1961/62. The failure is source evidence of an existing deliberate *name-keyed continuity* policy, not a flaky CI failure. Do **not** delete/change G1847 assertions or promote an invented spawn-origin kill-credit rule. The original SpawnPK server policy remains unknown.

## Implemented read-only enhancement

Only `LocalLabSequentialTerminalStageTrace` production code changes: its report now stores `spawnOriginMatchedAtDispatch` and `observedCurrentGeneration`, calculated from existing `MonsterSpawnerPvmRuntime.RecipientOrigin` and the current WorldPlayer at the installed default trace's `begin`. Exact original spawn generation is already stored. This mirrors the existing isolated `LocalLabTerminalStageIsolation.Report.spawnOriginMatchedAtDispatch` observation so operator evidence now distinguishes "delivered under same name but different WorldPlayer" from "spawn-origin matched" in either mode.

**No credit, kill, task, respawn or grant behavior changes.** No execution gate changes; no stage-retry, persisted ACK, duplicate grant, JWT/account security or durable event key is implemented. The provenance field is **not** authority to replay or compensate for grants.

## Real regressions

**G1961** recreates actual G1847 policy: WorldPlayer A starts a World-owned G4 Monster Hunter objective and spawns an NPC. A retires; different WorldPlayer B registers with the **same name** and activates G15/G16/G18. B completes the old canonical NPC death via actual lifecycle/finalization/ground settlement. Existing name-keyed G1 and progression are still delivered to B exactly once; default stage trace records `spawnOriginMatchedAtDispatch=false`, exact A generation and B observed generation, and existing five-stage RETURNED outcomes.

**G1962** uses the genuine opt-in isolated progression graph with the same rebind and current-delivery semantics. Existing opt-in stage report must show `spawnOriginMatchedAtDispatch=false` while all five stages RETURN. Neither path duplicates NPC settlement on repeated finalization.

Two JavaExec regressions, manifest **687→689 unique**, with all earlier G1847 historical controls still in the suite. Mandatory hosted exact-head **689/689**, both markers, Java11 major55, full Gradle before certification. Draft, main untouched.

## Remaining authority decisions

Is username continuity correct relative to original SpawnPK? **Unknown**; original kill attribution still #2558. Is it safe for account security and terminal-reward continuity under a genuinely new user claiming that name? **Unproven**, dependent on authenticated account ownership/epoch. Fixing it requires explicit identity and policy decisions, migration plan for already recorded World-owned objectives, and coordinated revision of G1847 behavior; not a stealth patch. Durable per-consumer apply/ACK/replay #2587 remains unresolved.
