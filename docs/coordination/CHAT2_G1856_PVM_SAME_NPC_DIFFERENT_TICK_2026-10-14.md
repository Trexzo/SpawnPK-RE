# Chat 2 G18.5.6 — same canonical NPC, different manually supplied deathTick

Date: 2026-10-14. Immutable hosted-green direct parent: [Chat 2 G18.5.5 #2606](https://github.com/Trexzo/SpawnPK-RE/pull/2606) commit `f15219ba0ce1914d4232014e588421d338b44ae7`, [Actions #38578892484](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38578892484) **SUCCESS**. Parent inherits G21.193/G18.5.4 joint [#2605](https://github.com/Trexzo/SpawnPK-RE/pull/2605), 500 unique focused tests and Java 11 major55/full Gradle.

## Source-backed boundary — current CUSTOM_LOCALLAB only

`PvmRecordService.recordIfCurrent(player,generation,npcId,deathTick)` deduplicates using `KillIdentity(npcId,deathTick)` in its **service-local** map. For a previously unseen pair, the service validates current WorldPlayer mutation ownership and persisted `pvm-record` extension, but has no NPC-lifecycle lookup or proof that the second `deathTick` belongs to a second genuine death. This is an internal service boundary; the canonical terminal observer supplies the actual death pair and remains unchanged.

New test-only method `pvmSameNpcSecondDeathTickCountsAsNewReceipt()` in the already checked `G1847MonsterSpawnerNameReuseTerminalAttributionTest`:

1. Register a real isolated WorldPlayer and provision the installed LocalLab Monster Spawner observer.
2. Spawn and lethally finalize exactly **one** canonical NPC at deathTick 181; verify exactly **one** G1 kill receipt, one PvM counter increment, and one settled ground item.
3. Directly invoke the **package-internal** `recordIfCurrent` test API with the same `npcId,181`; verify `REPLAY` and no additional increment.
4. Invoke that internal method manually with the **same already-dead npcId** and deathTick 182, without any new lifecycle death or terminal event. Observe `APPLIED`, kills **1→2**, receipts **1→2**, while ground-item count remains **1**. The duplicate (npcId,182) then correctly returns `REPLAY`.

Marker: `G1856_PVM_SAME_NPC_DIFFERENT_TICK_RECORD_IDENTITY_PASS`.

### Interpretation and guardrails

This is a **negative API-contract characterization**, NOT a claim of production automatic replay, a client-accessible injection, a security exploit, extra actual kill, duplicate drop, or original SpawnPK server reward semantics. The service is responsible for a local bookkeeping primitive; no source here proves it is supposed to independently validate lifecycle authority. The result reinforces [#2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587): future replay/outbox designs must bind immutable canonical terminal event identity **and** recipient/generation authority, not merely trust arbitrary API event fields. Keep original kill attribution [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558) as UNKNOWN_SERVER_AUTHORITY.

Changes: **one test-only Java file and this document**, zero production Java, zero Gradle/manifest changes, focused test manifest **500 unique**. Exact-head fresh hosted validation required for new marker, G1855/G1854/G1853/G1852, G21193 and Java11 major55/full Gradle. Draft/unmerged for Chat 1 integration review; no main/release changes or speculative gameplay fix. In particular, default-off G21.190 ACTIVE intent remains non-retireable even after forced G21.192 healthy World save.
