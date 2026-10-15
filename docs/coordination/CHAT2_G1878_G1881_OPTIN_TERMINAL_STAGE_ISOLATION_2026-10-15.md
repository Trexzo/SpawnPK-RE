# Chat 2 — G18.7.8–G18.8.1: opt-in LocalLab terminal stage isolation

## Status and base

- **Repository:** `Trexzo/SpawnPK-RE`
- **Base:** G21.217 + G18.7.6/7 Chat 2 cumulative draft #2686, exact parent `39b3272b04e4fa19a4c6f4f827af011da02be0d8` (536 focused).
- Parent exact-head GitHub Actions #38761953067: **SUCCESS**, 536 focused, Java 11 major 55 / full Gradle. This does **not** substitute for this branch's own CI.
- **This branch:** opt-in production fault containment and four new real canonical NPC tests, focused manifest **536→540**.
- **Default:** OFF. The existing sequential `creditTerminalProgression` path is unchanged when the switch is absent.
- **Authority:** `CUSTOM_LOCALLAB_TERMINAL_STAGE_ISOLATION_EXPERIMENT`, **NOT original SpawnPK client/server policy**.
- All work **DRAFT, unmerged** pending exact-head Java11/full Gradle and upstream owner review.

## What source actually showed before this change

`MonsterSpawnerPvmRuntime.settlePending` commits ground items, removes the canonical NPC entry, then invokes the installed terminal observer. `LocalLabMonsterSpawnerProvisioning` invokes G1 first and G4, G14, G15, G16, G18 in a single sequential `creditTerminalProgression` method. A G14, G15, or G16 IllegalStateException can abort later consumers after the NPC is permanently finalized. The runtime catches the observer error but does not roll back settlement or offer per-stage retry.

Previously certified G18.7.2/3/4/7 tests demonstrate those missing-stage boundaries. They must remain green in their default-off form.

## Implemented scope

New `LocalLabTerminalStageIsolation` is an **opt-in, World-local** post-G1 dispatcher:

1. It receives the actual `FINALIZED` `FinalizeResult` **after** G1's installed `onFinalized` has returned. It requires a ground-settlement receipt. It never manufactures a dead-NPC event or retries G1/drops.
2. It captures the WorldPlayer occupying the terminal `recipientRef` at dispatch and the current generation. Before **each** of G4, G14, G15, G16, G18, it checks that the same object/generation is still registered, otherwise marks `SKIPPED_NO_CURRENT_RECIPIENT`.
3. Each stage is invoked **at most once** within this callback. `RuntimeException` from one stage is reported as `THREW`, and the next stage is invoked if the captured recipient remains current.
4. It records a bounded 128-item **memory-only** diagnostic history: canonical NPC identity, death tick, recipient name, observed generation, spawn-origin match boolean, and each stage outcome (`RETURNED`, `THREW`, `SKIPPED_NO_CURRENT_RECIPIENT`).
5. It does not acquire the global World lock or hold the diagnostics monitor while invoking gameplay callbacks. It does not make downstream name-based updates atomic with the generation check.
6. The existing `LocalLabMonsterSpawnerProvisioning.create(World)` activates this only when `-Dspk.locallab.monsterSpawner.isolateTerminalStages=true` is supplied **before** runtime composition. It reads the property once at `create`, not on individual kills. An additional package-local `create(World, LocalLabTerminalStageIsolation)` allows explicit deterministic test injection without global JVM flags.
7. Opt-in false calls the **same original sequential method**. No existing source authorities, reward rules, persistence layouts, GamePacket semantics, network code, or client changes.

An observer exception *outside* the five progression consumers (including G1) still aborts the callback. `Error` from a stage is not caught by the experimental dispatcher and falls through to the outer runtime's existing catch boundary. This is intentionally a narrow containment mechanism, not a blanket swallow/retry system.

## Four focused real-runtime regressions

| Test | Fixture / genuine runtime assertion | Diagnostic assertion |
| --- | --- | --- |
| `G1878OptinG14FaultIsolationContinuesLaterConsumersTest` | two genuinely distinct NPC deaths with invalid **test-only** G14; G1 + G4 + G15/G16/G18 continue; two real drops; old NPC duplicate rejected | two reports; G14 `THREW`, other G4/G15/G16/G18 `RETURNED` |
| `G1879OptinG16FaultIsolationPreservesG18Test` | two genuinely distinct NPC deaths with invalid **test-only** G16; G1/G4/G14/G15/G18 continue; no old NPC replay | two reports; G16 `THREW`, G18 `RETURNED` |
| `G1880OptinG15FaultIsolationContinuesG16G18Test` | owner A spawns first NPC, B replaces A and has malformed test-only G15; G14/G16/G18 progress on two real NPC deaths by current LOCAL LAB name policy | first report explicitly records spawn-origin **mismatch**, second records matching B origin; G15 `THREW` each |
| `G1881OptinTerminalNoLiveRecipientSkipsStagesTest` | NPC is genuinely killed after its only owner leaves; ground drops still settle once, G1 records no kill, old NPC cannot re-finalize | one report; all five stages `SKIPPED_NO_CURRENT_RECIPIENT` |

All four are included once in `currentTrainFocusedTests`, which now checks **540 unique** test class names. Dedicated Gradle JavaExec tasks allow each to be run independently.

## Explicit nonclaims and unresolved blockers

- **No durable per-consumer ACK/outbox.** `RETURNED` only means the method returned normally, *not* that a reward was applied or durably persisted. `THREW` does **not** prove no side effects; a consumer can mutate before throwing. Reports are lost when the World/JVM closes. Replaying from them is unsafe and forbidden.
- **No recovery of missed G14.** The opt-in dispatcher limits *downstream starvation*, not the failing consumer's loss.
- **No source-backed original kill-credit rule.** First death under A and terminal under B retains the existing LOCAL LAB name-based behavior; the origin-mismatch field diagnoses that fact but does not authorize B by original game evidence.
- **No atomic generation fence across internal consumer callbacks.** The pre-stage WorldPlayer/generation check can race a disconnect between check and name-based mutator. Durable and authenticated origin policy remains open at #2558.
- **No record/drop exactly-once guarantee across process crash or across two JVMs**, no account ACTIVE marker cleanup, durable owner epoch, or recovery grant. G21 core locks remain an independent Chat 1 workstream.
- **No claim of original SpawnPK server policy.** No live-server authority was inferred.

## Required next gates

1. Fresh exact-head GitHub Actions **SUCCESS** for this branch: Java 11 major55, **540/540** focused, full Gradle, all four markers.
2. Confirm all old default-OFF G18.7.2–7 G1/Slayer/Task/Adventure/DMM tests retain their original semantics.
3. Audit stage callback idempotency and progress persistence across JVM/World close before proposing *any* durable replay. Stage attempts need stable canonical `npcId/deathTick`, immutable authority and recipient generation, durable state transitions and crash-consistent ACKs; simply replaying `THREW` is unsafe.
4. Chat 1 can cherry-pick only the two production files, four new tests, manifest increment and this note onto the latest certified G21 core after CI. Do not indiscriminately merge unrelated draft lineage or modify `main`.

**Related open design issues:** #2587 failure/retry outbox; #2558 original kill credit; #2547 Daily Money Making policy; G21 #2540/#2559 account authority. These are not resolved by this candidate.
