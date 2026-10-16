# Chat 2 G19.7.1/72 — immutable post-settlement death identity envelope

## Exact code and authority
- Exact parent: [Chat1+Chat2 cumulative #2851](https://github.com/Trexzo/SpawnPK-RE/pull/2851), `286f5b084b8544d1e80d62c82b5193d32191c587` (707 focused candidate). This retains G21.294/295 final-save thread-interruption behavior and G21.293 critical regression retention.
- The new production `LocalLabSettledDeathEnvelope` is **CUSTOM_LOCALLAB_OBSERVATION_ONLY**. It is an immutable, in-process identity validation/snapshot boundary, not an original SpawnPK protocol or recovered original server contract.
- New envelope captures `npcId`, `definitionId`, `deathTile`, `deathTick`, `recipientRef`, exact original captured recipient generation (or -1), observed current generation (or -1), and one explicit origin classification. It does not expose a mutable WorldPlayer or World object in its detached fields.
- Before capture, it requires the exact producing World object, a genuinely `FINALIZED` result with non-null canonical finalization and ground settlement, and exact matching NPC identity/definition/tile/death tick/recipient between finalization and ground receipt. A foreign World and a mismatch fail closed rather than manufacturing an outbox candidate.

## Production integration
`LocalLabSequentialTerminalStageTrace.Capture` now constructs the envelope once, at real installed dispatch time. Existing `spawnOriginMatchedAtDispatch` and observed generation are computed from the immutable envelope's exact origin state. Detached `Report` gains death definition, tile and explicit origin status, and uses the envelope rather than re-reading mutable current user state. G1 and G4/G14/G15/G16/G18 callbacks, player credits, settlement ordering and failure propagation are unchanged.

The classifications are:
- `CURRENT_SPAWN_RECIPIENT` — same exact registered WorldPlayer and generation as spawn
- `SAME_NAME_REBOUND` — same username, different WorldPlayer
- `NO_CURRENT_RECIPIENT` — original captured but now absent
- `MISSING_SPAWN_ORIGIN` — no current player at the original spawn moment
- `STALE_SPAWN_RECIPIENT` — original player object still present but generation/ref/ownership no longer matches

**Important:** these are observational classifications, not authorization rules. Existing G1847/G1863 name-keyed LocalLab credits intentionally remain unchanged.

## Real source-backed regressions
- **G1971** uses actual settled NPC terminal events and strengthens G1965/G1963: finalization/ground identity, exact current generation, two separate same-World collectors, and no duplicate settlement.
- **G1972** extends G1961/G1959 and performs a new genuinely unregistered-at-spawn recipient death: same-name replacement classification (without changing existing credited outcomes), disconnect classification, cross-World fail-closed capture, missing spawn-origin classification, no task delivery without current recipient, one ground drop and no replay.
- Gradle focused manifest **707→709 unique** plus explicit G1971/G1972 JavaExec tasks, while preserving all G21.293 critical coverage anchors and the inherited G21.294/295 final-save tests.

## Delivery limitations
An `EntityId` is process-local. This envelope does **not** allocate a cross-restart event ID, persist an outbox entry, perform atomic consumer grant/ACK, recover partial rewards, replay consumers, guarantee the account final save, or establish original SpawnPK killer policy. No spontaneous reward migration. Issue #2587 remains open. A future durable outbox needs a separately designed writer + crash consistency and consumer-owned idempotency before replay.

## Release gate
Require hosted exact-head **709/709**, G1971/G1972 markers, Java11 major55, all G21 critical anchors and complete Gradle. Parent #2851 needs its own exact-head success. Draft only; `main` unchanged.
