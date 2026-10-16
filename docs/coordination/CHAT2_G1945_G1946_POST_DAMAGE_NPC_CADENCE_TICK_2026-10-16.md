# G19.4.5/46 — LocalLab NPC click cadence anchored after long HP-admission callback

**Parent:** [Chat 2 G1943/44 #2806](https://github.com/Trexzo/SpawnPK-RE/pull/2806) at exact `a621981820efe7312099d8e75fbdb79bde5a64b4` (661 focused candidate; own hosted verification required). Parent contains G1941/42 unchecked mask terminalization on cumulative G21 core and real MonsterSpawner packet-failure regressions. Chat 1 account source unchanged.

## Source-level loophole

`LocalCanonicalNpcAttackHandler` obtains `attackTick=world.clock().tick()` before invoking `beforeResolution`. A hook, gameplay callback or externally running World ticker can advance time while the player-owned monitor is held. The subsequent HP mutation can occur many World ticks later, but the handler previously published cooldown as `attackTick + hit.nextAttackDelayTicks`. If enough ticks elapsed, the cooldown had **already expired** when the hit returned. A second handler bound to the same exact WorldPlayer could immediately land another current-tick click, defeating the intended post-hit cadence even though per-player monitor synchronization is correct.

## Minimal production correction

Immediately **after accepted authoritative HP mutation and packet-representability validation**, observe `completionTick=world.clock().tick()`, use `Math.max(attackTick,completionTick)` as the LocalLab player-owned next-attack cadence anchor, and add the unchanged `hit.nextAttackDelayTicks`. Do not modify the initial `attackTick` passed into damage/death/source witness, legacy `CombatState`, original client opcode72, NPC damage, or any account/World persistence. The guard ensures a synchronous long pre-resolution callback cannot make the ensuing newly published cooldown already expired. It is a local compatibility policy, **not recovered SpawnPK server attack-speed authority**.

This is not an atomic lease between WorldClock and WorldPlayer lock across all asynchronous tick advancement; clock can continue advancing after its last observation. The test proves a specific deterministic stale-admission-window bug, not full real-time concurrency serialization of unrelated World services.

## Executable regressions

- **G1945:** exact ISAAC decoded client opcode72, one WorldPlayer, two different canonical handler instances and shared actual scene. The first pre-resolution callback advances WorldClock **12 ticks** before HP mutation; one 10 HP hit commits, cooldown is strictly greater than the latest World tick, a second same-target click at that current tick is `CADENCE_BLOCKED` with no damage/packet, and later legitimate hit succeeds after additional ticks.
- **G1946:** same fixture but other handler targets a **different** canonical NPC. The second NPC must remain at 30 HP through the elapsed callback and shared cooldown; only a later legal click can damage that second target.

Manifest **661→663** mechanically unique focused classes; two JavaExec gates. Require independent exact-head hosted **663/663**, Java11 major55, G1945/G1946 markers and complete Gradle. Draft, main unchanged.

## Unresolved authority

No original SpawnPK cadence or kill-credit policy asserted, no durable progression/ACK inferred. This is only the LocalLab canonical NPC direct-hit timing owner; delayed-hit scheduling, actor lifecycle fencing, connection teardown, external WorldClock tick synchronization and consumer-grant delivery are separate.
