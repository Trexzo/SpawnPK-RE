# Chat 2 G20.1.7–20 — experimental settled NPC consumer-attempt fence

## Exact parent and separation
Source is cumulative [PR #2906](https://github.com/Trexzo/SpawnPK-RE/pull/2906) exact `1eae3b4bd8c4de59a47df36d581cc8f33f903d77` (hosted 803/803 successful; G2013–16 triple cross-tab and G21345–48 account witness inode replacement tests). This branch does NOT edit account, LocalSession, player persistence, World lifecycle, G1/G4/G14/G15/G16/G18 consumer implementations, drop settlement or the default terminal observer.

**Authority:** `CUSTOM_LOCALLAB_OBSERVATION_ONLY`. Nothing in this branch recovers original SpawnPK server credit semantics.

## New production module
`LocalLabExperimentalTerminalAttemptFence` is a **standalone, unwired** package-local in-memory rehearsal of future idempotent delivery contracts:
- Constructor binds to the **exact World object**. Every `reserve(terminal,consumer)` validates a genuinely FINALIZED + settled canonical terminal through `LocalLabSettledDeathEnvelope.observe` and rejects a foreign World before recording anything.
- In-process key combines NPC `EntityId`, death tick and recipient ref, all inside the exact World/fence instance. The same key may have only one reservation per G1/G4/G14/G15/G16/G18. Repeated key with changed definition/death tile/spawn recipient generation/origin class fails rather than silently relabeling the original death.
- Distinct per-consumer tokens are owned by the exact fence instance, checked on completion. Marking `returned` is explicitly `RETURNED_UNACKNOWLEDGED`; marking `threw` retains only exception class and becomes `THREW_MUTATION_UNCERTAIN`. Failure before either mark stays `RESERVED_UNCERTAIN`, without guessed state. Duplicate stage or completion is denied. No `Runnable`/`Callback` action is invoked by the module.
- The in-process table is bounded at 256 **attempt reservations**, not 256 NPCs. At capacity the fence refuses all new reservations while retaining all earlier keys; **nothing is evicted or retried**. Detached snapshots are immutable and never show a durable ACK/replay authority.
- The key is **not durable or globally unique**, because `EntityId` is process-local, World object identity is not a cross-restart epoch, and no disk operation is performed. The fence is **not wired to gameplay** and must not be used as a positive grant dedupe or recovery journal.

## Real runtime tests
1. G2017: installed World, genuinely connected player, canonical NPC spawn/lethal damage/finalization and ground receipt. Six consumer tokens reserved once, duplicate denied, returned vs threw/uncertain results and historical detached snapshot. The real gameplay G1 and settlement remain one-shot.
2. G2018: two distinct Worlds, a real settled terminal, foreign World reservation and foreign fence token completion rejected; exact source token remains unaffected; double completion denied.
3. G2019: real terminal, null exception refusal without consuming token, class-only exception evidence and no per-stage retry, second consumer independent.
4. G2020: 43 genuinely finalized canonical NPCs × 6 consumers = 258 possible reservations, 256 accepted and last 2 strictly fail closed; initial event's reservation still blocks duplicates after capacity. Original World receiptCount and ground amount equal 43. Immutable snapshots retained.

## What is NOT implemented
No persisted death event, authenticated cross-process epoch, crash-consistent outbox publication, per-consumer atomic grant plus ACK, callback wiring, retries, durable delivery, safe crash recovery, or changes to existing LocalLab name-keyed credit policy. Reusing a fresh fence after restart would forget ALL reservations. **Do not enable rewards or replay from this module.** Issue [#2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587) remains the architectural blocker; Chat 1 owns account final-save witness security independently.

## CI certification
Run exact-head hosted **807 unique focused classes**, explicit G2017–20 PASS markers, Java11 bytecode major55, complete Gradle. No merge into `main` until coordinated review. All changes are draft.
