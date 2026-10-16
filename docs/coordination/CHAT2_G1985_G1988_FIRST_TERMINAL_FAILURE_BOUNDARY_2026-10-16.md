# Chat 2 G19.8.5–88 — first observed terminal blocker

## Provenance
- Exact parent: Chat1+Chat2 [cumulative #2872](https://github.com/Trexzo/SpawnPK-RE/pull/2872) `4802322b02b25a11c1ec79e033c0dbbe851655cd`, independently hosted **737/737**, Java11 bytecode major55 and full Gradle. Includes G19.8.1–84 World-local origin × consumer cross-tab and Chat1 G21.311/312 real forced World SaveTicket postmove evidence.
- Scope **CUSTOM_LOCALLAB_OBSERVATION_ONLY**. No original SpawnPK server credit contract, mutation policy, account write, delivery callback, or network packet changed.

## Actual production
1. New `LocalLabTerminalFailureBoundaryTally` classifies a DEFAULT real, already-settled terminal's first observed blocker. Enum: `NO_THROW_RECORDED`, `NO_CURRENT_RECIPIENT`, `G1`, `G4`, `G14`, `G15`, `G16`, `G18`. These are observational evidence categories, not credit or reward completion.
2. A recorded G1 exception **must** leave all derived consumers NOT_REACHED. After the first derived THREW, every later derived consumer must remain NOT_REACHED. Partial disconnected-recipient skips and simultaneous skipped/throwing outcomes are rejected; the exact offending stage is reported. The validation happens BEFORE the existing origin × consumer cross-tab or origin/marginal counters mutate, preventing partial evidence publication on malformed reports.
3. On accepted exact-World, exact-collector, one-shot captures, the tally increments a saturating `long` per first-boundary class, surviving 128 detailed reports' eviction. It exposes immutable detached snapshots. Existing `LocalLabSequentialTerminalStageTrace.Snapshot` now includes `failureBoundaries`; `LocalLabTerminalDeliverySummary` exposes deeply detached `firstBoundaryCounts`. Neither method can grant or replay.
4. No persistence, outbox, durable death identity, atomic consumer ACK, automatic retry, final-save confirmation or original server authority is created. Caller outcome RETURNED still may mean no active task; THREW may have already partially mutated.

## Real-runtime regression evidence
- G1985 strengthens G1966: **131** genuinely spawned/settled NPC deaths, 3 ring evictions, 131 NO_THROW_RECORDED first-boundary observations and immutable historical snapshots.
- G1986 strengthens G1967: two actual canonical deaths, first G1 RETURNED, second G1 THREW after ground settlement, five derived stages NOT_REACHED and exactly one G1 first blocker.
- G1987 strengthens G1884 and G1959: healthy actual G14 vs forced malformed G14 with downstream G15/16/18 NOT_REACHED, plus a genuinely disconnected origin whose five derived stages are SKIPPED_NO_CURRENT_RECIPIENT and not incorrectly described as a throwing consumer.
- G1988 strengthens G1965's one-shot, same-World exact-collector negative: a synthetic **invalid observation derived from a genuinely settled NPC** (G1 THREW yet G4 RETURNED) is rejected BEFORE changing any boundary, cross-tab or marginal counter; no NPC re-finalization or grant.
- 4 focused entrypoints, strengthened underlying real-world fixture assertions, explicit Gradle JavaExec gates; manifest **737→741 unique**; all G21.308 49 critical regression anchors preserved.

## Release gate
Hosted exact-head **741/741**, G1985/86/87/88 PASS markers, Java11 major55 and complete Gradle. Base #2872 is independently 737/737 certified. Draft only, no merge to main. Persisted per-consumer apply/ACK and safe replay remain unresolved in [issue #2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587).
