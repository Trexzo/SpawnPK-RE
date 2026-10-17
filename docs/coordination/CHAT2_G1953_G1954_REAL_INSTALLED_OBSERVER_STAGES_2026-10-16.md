# G19.5.3/54 — actual LocalLab installed G1/G14 observer failure boundaries

**Exact parent:** [G1951/52 #2815](https://github.com/Trexzo/SpawnPK-RE/pull/2815) `eb79a733f208871dcf1e3b543d5093d04f046451` (673 candidate); production MonsterSpawnerPvmRuntime records bounded RETURNED/THREW post-settlement observer attempts. The existing opt-in `LocalLabTerminalStageIsolation` separately records G4/G14/G15/G16/G18 outcome, but neither observation is an applied reward ACK.

**Gap in G1951/52 evidence:** synthetic test-only observers establish that failure recording works, not that an actual installed progression chain correctly reports failures. We need to distinguish an overall observer's RETURNED from *every consumer credited*.

## Existing real production fixture expanded

`G1884TerminalStageIsolationPropertySnapshotTest` already assembles actual `LocalLabMonsterSpawnerProvisioning.create(World)`, G1 terminal loop, real canonical monster death/ground settlement, malformed test-only G14 persistence, and healthy G15/G16/G18 active objectives. It tests that the isolation JVM property is sampled at graph composition, not live read after a flip. We retain every old assertion and add exact observer diagnostic checks, plus a package-local property-restoring one-case helper.

- **G1953, default sequential mode:** G1 and ground drop succeed, malformed G14 causes the installed observer to throw, G15/G16/G18 remain uncredited as before; `MonsterSpawnerPvmRuntime.TerminalObserverDiagnostics` records `THREW`, `IllegalStateException`, exact NPC/deathTick/recipient/spawnGeneration. Retrying finalization cannot repeat the callback.
- **G1954, explicitly isolated mode:** G14 fails but is contained and reported by stage isolation, G15/G16/G18 progress once, while the **overall installed observer reports RETURNED**. This is expected: `RETURNED` is not a proof of downstream stage credit. Stage diagnostic records G14 THREW and healthy later stages RETURNED. Repeated finalization is NOT_OWNED, no duplicates.

Two JavaExec regressions, manifest **673→675 unique**. Does not change gameplay production policy or enable isolated dispatch by default. Requires hosted exact-head **675/675**, both markers, Java11 major55 and full Gradle. Preserve all Chat1 account security as delivered in the parent. Draft, `main` untouched.

**#2587 remains unresolved:** durable per-consumer apply, persisted exactly-once or at-least-once ACK, restart-safe resumption, and failed-after-partial-mutation reconciliation are not provided. No claim of original SpawnPK rewards/killer attribution.
