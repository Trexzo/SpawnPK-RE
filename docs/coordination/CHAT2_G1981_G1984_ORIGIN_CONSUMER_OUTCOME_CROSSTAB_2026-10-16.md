# Chat 2 G19.8.1–84 — origin × six-consumer outcome cross-tab

## Exact source and authority
- Parent [Chat 1 PR #2867](https://github.com/Trexzo/SpawnPK-RE/pull/2867), exact `f3b72ee6ca801bcc4da63cf122217a7f0616c820`, 729 focused candidate; includes G21.308 **49 critical account/NPC test anchors** and G21304–07 final negative-witness/TCP regressions. Core 727 [#2864](https://github.com/Trexzo/SpawnPK-RE/pull/2864) independently hosted **727/727**, major55/full Gradle. Separate 729 branch may still be checking CI.
- This is **CUSTOM_LOCALLAB_OBSERVATION_ONLY**. No original SpawnPK credit authority, committed per-consumer outbox, durable ACK/replay or positive account final-save receipt exists.

## Actual production module and installed hook
- New `LocalLabOriginConsumerOutcomeTally` is a per-World collector, not a global static. It records one `originStatus × consumer × evidence` count for each accepted, settled, default-mode terminal.
- Explicit dimensions: five `LocalLabSettledDeathEnvelope.OriginStatus` categories (current, same-name rebound, no current, missing spawn, stale), six consumers (G1/G4/G14/G15/G16/G18), five evidence categories (returned-unacknowledged, threw-mutation-uncertain, not reached, skipped-no-current, not observed). All 150 combinations preinitialized to zero. `long` counters saturate rather than overflow.
- `LocalLabSequentialTerminalStageTrace.record` calls the tally only after foreign-World, wrong-collector and one-shot rejects; report and six-consumer assessment are constructed first. The collector stores only class enums and counts, never WorldPlayer references, mutable state, Throwable or event identities. Its snapshot is detached and nested-unmodifiable, including origin total margins. Earlier snapshot stays unchanged when later deaths settle.
- Aggregate relationship survives eviction from the existing 128-report detail ring, but **identities and per-event correlations of evicted deaths are irrecoverable**. `hasDurableGrantAcknowledgement()` and `safeToAutomaticallyReplay()` always return false, with no replay or grant method.
- Existing stage, origin and G1 marginal metrics remain; existing G1/G4/G14/G15/G16/G18 consumer logic, settlement, transaction and final-save production code are untouched.

## Real-runtime tests (four new entrypoints plus substantive fixture asserts)
- G1981 extends G1966: **131 actual canonical NPC deaths, settled ground items**, three detail reports evicted; the current-spawn-origin × six RETURNED_UNACKNOWLEDGED cells each retain 131, alternatives remain zero, historical snapshot and all nested maps resist mutation.
- G1982 extends G1961 + G1972: same-name rebound (retains established name-keyed LocalLab credit), original disconnect, foreign World and never-registered-at-spawn recipient remain distinct origin cohorts.
- G1983 extends G1967 + G1884: true post-ground G1 exception leads to THREW_MUTATION_UNCERTAIN plus five NOT_REACHED; independent healthy/throwing G14 returns and not-reached outcomes are matched to exact original/current spawn generation.
- G1984 extends G1965 + G1959: exact World and same-World capture owner fences reject cross-collector insertion and duplicate one-shot captures; no phantom origin × consumer statistics.
- Gradle focused manifest **729→733 unique** with four explicit JavaExec regressions; inherited **49/49 G21.308 critical anchor** names preserved and verified by parser, G21304–07 untouched.

## Gate and explicit limitations
Hosted GitHub Actions **exact-head 733/733** with G1981–G1984, Java11 major55 and complete Gradle. Parent #2867 must pass separately. No merging main without review; draft branch only.
This is NOT an outbox, restart-safe death ID, consumer ACK, permission for replay, or evidence of original server killer policy. Keep issue #2587 open.
