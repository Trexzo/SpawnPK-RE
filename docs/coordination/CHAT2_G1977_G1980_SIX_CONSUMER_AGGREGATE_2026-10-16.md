# Chat 2 G19.7.7–80 — detached six-consumer outcome summaries

## Exact source
- Exact parent: [#2860](https://github.com/Trexzo/SpawnPK-RE/pull/2860), `b323f1f886cee9cfd127108f641786429b45e52d` (721 focused candidate), retaining the new G21.302/303 conservative negative final witness restart-admission veto, G21.298–301 negative witness tests, G21.293 23 must-retain anchors and cumulative gameplay through G1976.
- Latest independently GitHub Actions certified combined source: [#2859](https://github.com/Trexzo/SpawnPK-RE/pull/2859), exact `d01d710180ebca88e59fe73fc23b3e5442bf713c`, **719/719**, Java11 major55 and complete Gradle.
- The source authority is **CUSTOM_LOCALLAB_OBSERVATION_ONLY**. No original SpawnPK credit logic, disk outbox or authenticated owner epoch is reconstructed.

## Implemented production
- New immutable `LocalLabTerminalDeliverySummary` provides detached six-consumer outcome counters from *already existing* `LocalLabSequentialTerminalStageTrace.Snapshot` aggregate G1 + G4/G14/G15/G16/G18 observations. No additional outcome collections, event IDs or background services; bounded report ring remains at 128. Snapshot has `observedTerminals`, `evictedDetailReports`, `retainedDetailReports`, immutable nested `consumerCounts`, and immutable origin category counts. Each of six consumer rows includes all five evidence categories with explicit zeros.
- G1 outcomes are mapped to `RETURNED_UNACKNOWLEDGED`, `THREW_MUTATION_UNCERTAIN` and `NOT_OBSERVED`. Downstream stage outcomes independently map to returned-not-ACKed, threw-possibly-mutated, not reached, skipped-no-current. There is **no per-death correlation after an event's detailed report has been evicted**.
- `hasObservedMutationUncertainty` flags any observed callback THREW. `totalObservationsFor(consumer)` uses saturating add to avoid overflow, but total may be saturated and is never a durable count. `hasDurableGrantAcknowledgement` and `safeToAutomaticallyReplay` always return false. No automatic replay action exists.
- No G1, ground settlement, recipient mutation, original progression, packet, login admission or persistence production file changed.

## Source-backed full-runtime regressions
- G1977 extends actual 131 spawned/dead/settled canonical NPC case G1966: 3 evicted detailed reports, all six consumers return unacknowledged 131 times, no phantom failure/skip, nested map immutability and retained earlier snapshots.
- G1978 extends real twice-settled pending-respawn G1 failure G1967: one G1 returned, one G1 threw after possible mutation, five derived consumers `NOT_REACHED`, no replay.
- G1979 extends real G1884 healthy vs malformed G14 terminal with actual progression state: G4 returned, G14 threw or returned, G15/G16/G18 not reached on failure, immutable six-consumer counts, no durable credit inference.
- G1980 extends real disconnected original recipient + foreign World fence G1959 and same-World capture G1965: five downstream `SKIPPED_NO_CURRENT_RECIPIENT`, no fabricated returned grant, no foreign observer mutation.
- Underlying G1966, G1967, G1884 and G1959 have substantive new assertions, in addition to four new focused entrypoints. Manifest **721→725 unique**, standalone Gradle JavaExec tasks, all inherited G21.293 critical anchors and G21302/03 core regressions retained.

## Gates
Require hosted GitHub Actions exact-head **725/725**, G1977–80 PASS, Java11 major55 and full Gradle. Core parent #2860 must pass independently. Draft, no main merge.

**Not implemented:** persisted event IDs, cross-restart outbox, safe replay, atomic per-consumer grant ACK, authenticated final-save success, original SpawnPK killer authority. Issue #2587 remains open. Do NOT grant credit on the basis of a callback RETURNED or an aggregate summary.
