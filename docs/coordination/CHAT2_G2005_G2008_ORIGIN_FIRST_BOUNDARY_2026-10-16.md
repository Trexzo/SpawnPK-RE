# Chat 2 G20.0.5–G20.0.8 — origin × first terminal blocker

## Exact source provenance
- Git parent: [#2892](https://github.com/Trexzo/SpawnPK-RE/pull/2892), exact `20f629877642bd1065753d5ac714e44303401b0f` (**773/773 hosted certified**).
- Parallel certified cumulative [#2891](https://github.com/Trexzo/SpawnPK-RE/pull/2891), exact `ddeb6a76ccdab241a6d4ffb8f73d6b26b8a83d9b`, also **773/773** Java11 major55/full Gradle.
- This modifies only **CUSTOM_LOCALLAB** post-settlement gameplay diagnostics and source-backed regression tests; no original SpawnPK server code/policy or grant authority recovered.

## Source-backed problem
`LocalLabSequentialTerminalStageTrace` already recorded separate World-process-local monotonic origin counts, G1/stage callbacks, an origin × six-consumer evidence cross-tab, and first blocking stage tally. After 128 individual reports were evicted, these separate marginals could no longer tell whether **a particular spawn-origin class had a first G1, G4, G14, G15, G16, G18, recipient-absent, or no-throw boundary**. That correlation is important for distinguishing a disconnected recipient from a same-name-rebound player or exact-current player without silently changing established name-keyed LocalLab credit policy.

## Production implementation
New bounded-size `LocalLabOriginFirstFailureBoundaryTally` maintains exactly `OriginStatus.values() × Boundary.values()` saturating long entries. `Snapshot.crossCounts` is a deeply immutable and detached `EnumMap`-based map and `count(origin,boundary)` accessor. It stores **no event IDs, raw usernames, WorldPlayer objects, Thread, Throwable or mutable ground settlement state**.

The existing collector calls `LocalLabTerminalFailureBoundaryTally.classify(report)` to validate fail-fast chronology and typed failure evidence **before** any counters change. After exact source-World and collector-owned once-only checks, it records `originFailureTally.acceptValidated(report.originStatus, firstBoundary)` on the same accepted capture. Foreign World, wrong collector, previously recorded capture, malformed G1/stage failure type or impossible terminal timeline cannot affect the new counts. Individual detail ring remains 128. Reports from opt-in isolated mode still do not enter the default collector.

No callbacks, PvM receipts, ground settlements, G4/G14/G15/G16/G18 objectives, account persistence, packet or login-admission production files are changed. The cross-tab is read-only observational data, not a durable receipt or grant ledger.

## Real-runtime tests
- **G2005** strengthens G1966 with **131 real World canonical NPC death+ground settlements**, three detailed report evictions, `CURRENT_SPAWN_RECIPIENT × NO_THROW_RECORDED = 131`, no fabricated other buckets, and historic detached/immutable snapshots.
- **G2006** strengthens G1967 with two actual settled NPCs: healthy G1 then a genuine post-ground G1 pending-respawn exception; exact current-origin no-throw and G1-first-failure counts split 1/1, all derived consumers remain NOT_REACHED on G1 failure.
- **G2007** strengthens G1959 disconnected and G1961 same-name rebound: `NO_CURRENT_RECIPIENT × NO_CURRENT_RECIPIENT = 1` when retired, but `SAME_NAME_REBOUND × NO_THROW_RECORDED = 1` for intentional established username-based LocalLab credits. No fabricated or replayed grants.
- **G2008** strengthens G1884 real healthy vs malformed G14 default sequential NPC callbacks: `CURRENT_SPAWN_RECIPIENT × NO_THROW_RECORDED` for healthy; `CURRENT_SPAWN_RECIPIENT × G14` when malformed; G15/G16/G18 not reached on failure, no inferred durable ACK.
- Gradle focused manifest from **773→777 unique**; four dedicated JavaExec tasks and new **four-case critical MUST-RETAIN** family. Prior 89 critical anchors remain, now 93.

## Certification
Require independently hosted exact-head **777/777**, G2005–08 PASS markers, Java11 bytecode major55, complete Gradle and preserved critical cases. Draft/unmerged; main unchanged.

**Not implemented:** cross-restart event identity, durable per-consumer delivery queue, atomic idempotent grant ACK, automatic retry/replay, positive authenticated final-save owner epoch or original SpawnPK killer attribution. Do not infer a reward from `NO_THROW_RECORDED`. Issue #2587 remains open.
