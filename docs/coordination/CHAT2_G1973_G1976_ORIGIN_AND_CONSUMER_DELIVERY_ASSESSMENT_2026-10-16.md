# Chat 2 G19.7.3–76 — origin-class retention and six-consumer delivery triage

## Exact source parent
[Chat1+Chat2 draft cumulative #2854](https://github.com/Trexzo/SpawnPK-RE/pull/2854) exact `c6ad4b2abf8a74d9197e8579e392a50c854d4d5d` (711 candidate). Independent gameplay envelope donor [#2853](https://github.com/Trexzo/SpawnPK-RE/pull/2853) exact `0529b594a093040eae48df376242991737146194` has verified **709/709** hosted CI, Java11 major55 and full Gradle. Core final-save behavior (G21.294–297), 23 must-retain G21.293 anchors and original name-keyed progression policies are unmodified.

## Implemented production code
1. `LocalLabSequentialTerminalStageTrace` now counts each accepted `LocalLabSettledDeathEnvelope.OriginStatus` (CURRENT_SPAWN_RECIPIENT, SAME_NAME_REBOUND, NO_CURRENT_RECIPIENT, MISSING_SPAWN_ORIGIN, STALE_SPAWN_RECIPIENT) using saturating `long` counters independent of the 128-report detail ring. Counters increment **only** after an exact World and collector-owned capture has been accepted; rejected cross-World, foreign-collector or already-recorded captures do not change state. `Snapshot.originCounts` is immutable and detached. Historical snapshots stay unchanged as more deaths arrive.
2. New production `LocalLabTerminalDeliveryAssessment` constructs one detached, immutable per-consumer evidence map from an already-recorded real terminal report. It retains identity (`npcId`, `deathTick`) and observed origin class, and maps G1 plus G4/G14/G15/G16/G18 to `RETURNED_UNACKNOWLEDGED`, `THREW_MUTATION_UNCERTAIN`, `NOT_REACHED`, `SKIPPED_NO_CURRENT_RECIPIENT` or `NOT_OBSERVED`. There is **no** mutation, callback invocation or thread scheduling. `safeToAutomaticallyReplay()` and `hasDurableGrantAcknowledgement()` are always false. A returned callback may have no active objective, and an exception may follow partial mutation.

## Real-runtime regression coverage
- G1973 uses strengthened G1966 with 131 actual canonical NPC death/ground settlement events; verifies 131 current-origin outcomes after 3 detailed-report evictions, zero fabricated alternative classes and historical detached immutability.
- G1974 uses strengthened G1972/G1961/G1959: exact same-name replacement, retired original, missing-at-spawn recipient, and cross-World rejection. Asserts correct count without changing pre-existing name-based credits.
- G1975 exercises actual healthy and invalid G14 post-settlement terminal callbacks through G1884. Checks one immutable six-consumer assessment with G4 returned, G14 threw, G15/16/18 NOT_REACHED and **no** automatic replay or durable ACK.
- G1976 exercises actual settled G1 duplicate pending-respawn exception with genuine prior G1 return and an unrelated disconnected original recipient; checks distinct `THREW_MUTATION_UNCERTAIN`, `NOT_REACHED` and `SKIPPED_NO_CURRENT_RECIPIENT` outcomes with no grant authority.
- The underlying G1966/G1961/G1959/G1972/G1967/G1884 fixtures have real assertions added rather than only printing PASS markers. Focused manifest **711→715 unique**, four separate JavaExec tasks; G21.293 23 critical anchors retained.

## Safety and staged future architecture
This is **CUSTOM_LOCALLAB_OBSERVATION_ONLY**. `EntityId` and World identity are process-local. No persisted outbox event, cross-restart death ID, consumer-specific dedupe, atomic mutation+ACK, crash-safe final-save receipt, settlement replay or original SpawnPK killer authority is inferred or introduced. Existing G1847/G1863 name-keyed LocalLab credit behavior intentionally remains unchanged; no replay can be enabled from an assessment. The still-unsolved durable per-consumer transaction and replay design is in [#2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587).

## Certification
Require GitHub Actions on exact-head **715/715**, G1973/G1974/G1975/G1976 markers, Java11 major55 and complete Gradle; certify #2854's 711 head independently before cumulative promotion. All PRs draft/unmerged, `main` unchanged.
