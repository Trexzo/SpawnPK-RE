# Chat2 G20.2.1–24 — experimental refusal and attempt-state evidence

## Exact source and authority
Parent: [#2912](https://github.com/Trexzo/SpawnPK-RE/pull/2912) exact `aeccf8cc0263b4da09780e6e308fac044e251ee7`, independently hosted **813/813**. This is only the standalone, UNWIRED `LocalLabExperimentalTerminalAttemptFence`: no automatic gameplay grant, no callback invocation, no per-user persistent write, no disk outbox, no retry or original SpawnPK authority. Main and account persistence remain untouched.

## Production module
Extend the existing process-local World-bound attempt fence with saturating `Refusal` counters covering duplicate consumer, conflicting death evidence, capacity fail-closed, foreign fence, already observed and unknown reservation. Existing exact World, already-settled validation, 256 limit and no-eviction behavior remain intact. New detached `Snapshot.refusals` and `Snapshot.observations` maps are deeply immutable, calculated without mutable token exposure; prior snapshots do not change after subsequent token observation/refusal. Counters track diagnostic refused calls; they are **not** durable or evidence that any reward callback actually ran.

The producer still rejects invalid foreign World envelopes before its reservation accounting, so this refusal map is **not** a global audit of all rejected requests. It never authenticates cross-restart death identity or supplies a grant ACK.

## Source-backed tests
- G2021 invokes strengthened genuine G2017 test: six consumers reserved from one actual canonical NPC death; one RESERVED, four RETURNED_UNACKNOWLEDGED, one THREW_MUTATION_UNCERTAIN, six duplicates recorded.
- G2022 invokes strengthened G2018: foreign World refused and foreign-fence attempt recorded only by the rejecting fence, original token stays usable; already-observed completion remains denied.
- G2023 strengthens actual G2019: failure type retained by class name only; failed consumer has one uncertain outcome and one duplicate refusal, later independent consumer still returns unacknowledged.
- G2024 strengthens G2020: 43 genuine NPC deaths, 256 capacity-limited tokens, two refused additional stages, no eviction, initial reservation and old duplicate still rejected.
- Four dedicated JavaExec tests, 813→**817 unique** focused manifest, existing critical retention gate unchanged.

## Mandatory release check
Only certify when exact-head hosted **817/817**, explicit G2021–24 markers, Java 11 bytecode major55 and full Gradle pass. Draft only, no merges. #2587 durable outbox and per-consumer atomic apply/ACK remains unresolved.
