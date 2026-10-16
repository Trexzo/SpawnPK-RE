# Chat 2 G20.0.9–G20.1.2 — bounded first-failure exception kinds

## Exact Git source
- Exact parent: cumulative [#2896](https://github.com/Trexzo/SpawnPK-RE/pull/2896), commit `c2f9d14f222e8ad86c3fee03b41ca8621ec0a739`, **781 focused tests**. This includes Chat 1 G21331–34 read-only negative sixth final-review witness audit and Chat 2 G2005–08 origin × first blocking stage.
- Independently successful hosted cumulative [#2895](https://github.com/Trexzo/SpawnPK-RE/pull/2895) and [#2896](https://github.com/Trexzo/SpawnPK-RE/pull/2896), each 781/781 Java11 major55 and complete Gradle. Neither is merged.
- **Source authority: CUSTOM_LOCALLAB_OBSERVATION_ONLY.** Unknown original SpawnPK killer/recipient semantics, no account persistence mutation, drop settlement change, packet change, or automatic grant/replay.

## Real production capability
The existing World-local trace retained *which first stage failed* (G1/G4/G14/G15/G16/G18) after eviction of its oldest 128 event detail reports, but not *what type of exception* stopped that stage. The only exception class name lived in an evictable per-death report. New `LocalLabTerminalFirstFailureTypeTally` retains a fixed **8 first-boundary × 4 coarse-kind** matrix:
- `NO_EXCEPTION` for `NO_THROW_RECORDED` or `NO_CURRENT_RECIPIENT`
- `ILLEGAL_STATE` for an exactly matching `java.lang.IllegalStateException`
- `ILLEGAL_ARGUMENT` for an exactly matching `java.lang.IllegalArgumentException`
- `OTHER_EXCEPTION` for every other known nonblank class name.

No unbounded class-name strings are stored in aggregate data. Type classification occurs **after** exact source World, collector, once-only capture and typed fail-fast chronology validation, before any counter mutation. Invalid missing-type and impossible boundary/kind combinations throw; accepted rows saturate independently at `Long.MAX_VALUE`, and deep immutable detached snapshots survive detail ring eviction. Forensic data are **not** durable reward ACKs and cannot authorize automatic replay.

The existing `LocalLabTerminalEvidenceConsistencyAudit` now reconciles each coarse-kind row against first-stage boundary margins, rejects impossible boundary-kind cells, and compares retained-event category counts with the aggregate. On 0-eviction snapshots it requires exact equality; after eviction, detailed reports are only a subset.

## Real source-backed regression coverage
- G2009 strengthens G1966, which executes 131 genuinely settled canonical NPC deaths (three evicted reports) and asserts **131 `NO_THROW_RECORDED × NO_EXCEPTION`**, zero fabricated kinds, deep map immutability, and an untouched historical 1-event snapshot.
- G2010 strengthens G1967, which executes real healthy and second post-settlement G1 duplicate pending-respawn death. Confirms 1 normal `NO_EXCEPTION` and 1 `G1 × ILLEGAL_STATE`; five downstream consumers NOT_REACHED.
- G2011 strengthens G1884 and G1959 real invalid G14, genuine healthy G14 and genuine disconnected recipient. Confirms G14 IllegalState versus NO_CURRENT_RECIPIENT no-throw; no phantom grant.
- G2012 uses one genuinely finalized NPC with exactly one ground-item settlement and real PvM receipt, then **separate test-only** collectors on that same immutable terminal: deliberately throw `IllegalArgumentException` and `UnsupportedOperationException` from a diagnostic G4 callback to exercise ILLEGAL_ARGUMENT versus OTHER_EXCEPTION without repeating gameplay delivery or settlement. Explicit mismatched boundary-kind rejection leaves a fresh tally unchanged. Canonical item and player credit remain one.
- Four standalone JavaExec tasks and focused manifest **781→785 unique**. Original G21.293 23 critical-test anchors and G21331–34 account-security tests remain untouched.

## CI and strict limits
New exact-head hosted release gate **785/785**, explicit G2009–12 markers, Java11 bytecode major55, full Gradle build. Keep draft/unmerged; main unchanged. Does not create a persisted event key, durable outbox, atomic consumer grant/ACK, original SpawnPK authority, positive final-save owner epoch or safe restart recovery. These remain in [#2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587).
