# Chat 2 G19.6.7/68 — installed G1 pre-stage terminal diagnosis

## Source provenance and exact authority
- Branch is based on cumulative Chat 1 integration [#2840](https://github.com/Trexzo/SpawnPK-RE/pull/2840) exact initial parent `0ee7c9e3c140e2e7a4ed912d63cb09d9768f5604` (697-focused candidate). No G21/account/security production code modified.
- Scope is read-only **CUSTOM_LOCALLAB** gameplay forensic observation. Original SpawnPK kill-credit attribution and grant semantics remain unknown.
- Source gap: `LocalLabMonsterSpawnerProvisioning.create` called `G1MonsterSpawnerPvmLoopService.onFinalized` before `LocalLabSequentialTerminalStageTrace.begin`. When G1 throws (e.g. a second canonical NPC death while its owner has a pending G1 respawn ticket), the already-settled terminal is marked THREW by the outer runtime yet **zero downstream stage reports** are recorded. That absence is ambiguous and not evidence of credit.

## Actual production correction
1. `LocalLabSequentialTerminalStageTrace.Report` gains a detached `G1Outcome` = `NOT_OBSERVED|RETURNED|THREW` and class-name-only `g1FailureType`. Existing stage enum still comprises only G4/G14/G15/G16/G18 and the previous bounded aggregate outcome ring remains unchanged.
2. In the installed DEFAULT (non-isolation) LocalLab observer, a normal G1 return is recorded on the existing capture. The original sequential G4-G18 callbacks and fail-fast policy stay identical.
3. On a G1 RuntimeException/Error for a genuinely FINALIZED/settled terminal, create an invocation-local stage capture, record `G1=THREW` and all five `NOT_REACHED` stages, then rethrow the **same original G1 exception**. If diagnostic code also fails, its error is merely suppressed on the original. No consumer callback, G1 record or ground settlement is repeated.
4. Opt-in isolation mode and nonfinalized events retain their prior dispatch semantics. Neither G1=RETURNED nor the stage outcomes are proof of a reward application, persistence, transaction receipt, ACK or restart-safe progress.

## Regression evidence required
- G1967: genuine connected player/spawner owner, two canonical NPC deaths. First terminal creates a pending G1 respawn ticket. Second settled death's installed G1 throws due to duplicate ticket. Check `G1=THREW`, five stages `NOT_REACHED`, exactly two settled drops, unchanged non-replay, diagnostic aggregate counts and outer observer failure.
- G1968: genuine healthy single NPC terminal, `G1=RETURNED` and G4/G14/G15/G16/G18 all RETURNED, exactly one ground item and no invented failure.
- Manifest from 697 to **699 unique**, with two dedicated JavaExec gates, inherited G1847/G1863 same-name account/player compatibility and core G21.287/288 retained.

**Release gate**: independently hosted GitHub Actions exact-head 699/699, explicit G1967/G1968 PASS markers, Java11 bytecode major55 and complete Gradle success. Draft only. Do not infer a durable reward ledger or merge without Chat 1 review.

## Follow-up beyond this diagnostic
- A real replayable post-settlement outbox must be persisted before consumer dispatch, anchored to a stable authoritative death/recipient/World identity, and updated atomically with each consumer's mutation and ACK.
- Existing G4/G14/G15/G16/G18 callbacks frequently alter objective state before a persistence operation. Retrying them based on a THREW/RETURNED trace is unsafe and can double-credit.
- G1 record, respawn and settlement are distinct; their partial failure semantics require source-backed replay policy, not blanket observer retries.
