# Chat 2 G19.5.9/60 — default stage trace recipient and source-World fences

**Exact parent:** [#2823](https://github.com/Trexzo/SpawnPK-RE/pull/2823), `9e0884db120a604930eb5832dfeb72da77550389`, 679 focused candidate. This parent introduces `LocalLabSequentialTerminalStageTrace` for actual default G4→G14→G15→G16→G18 fail-fast progression evidence, with zero award/retry authority. This PR adds **only** two Java regressions, focused Gradle registrations and this note; no additional runtime production behavior.

## G1959: recipient no longer exists when terminal settles

Compose an actual `LocalLabMonsterSpawnerProvisioning.create(world,null)` World-local graph, register the player and verify spawn recipient origin identity/generation captured at spawn. Retire the exact WorldPlayer before the canonical NPC receives lethal damage. Perform genuine lifecycle death, spawner finalization, canonical teardown and the owner-name ground drop. The installed sequential stage observer can return normally, but all five G4/G14/G15/G16/G18 attempts must be `SKIPPED_NO_CURRENT_RECIPIENT`, not RETURNED or credited. Overall observer RETURNED is **not** a reward ACK. Confirm no G1 player kill receipt and that a second finalization attempt is NOT_OWNED. This is a non-reconnect case; reassigning to a *new* session of the same username is a distinct policy/provenance issue and is not claimed safe by this test.

## G1960: reject foreign World evidence

Construct two distinct isolated World objects. Complete an actual canonical death and ground settlement in World A. A trace explicitly bound to World B must reject both `begin(terminalA)` and `record(captureA)` with `SEQUENTIAL_TERMINAL_STAGE_SOURCE_WORLD_MISMATCH` before inserting any report, while the correct source-World trace remains able to record its own independent observation. No account writes, compensating grants or cross-world contamination.

Focused manifest **679→681 unique**; two JavaExec tests. Exact-head hosted **681/681**, G1959/G1960 markers, Java11 major55 and complete Gradle mandatory; neither the parent nor another PR run certifies this branch. Draft, `main` unchanged.

**Unresolved:** original SpawnPK kill credit [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558), and durable idempotent per-consumer credit+ACK [#2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587). A trace is an in-process forensic instrument only; it is not a recovery queue.
