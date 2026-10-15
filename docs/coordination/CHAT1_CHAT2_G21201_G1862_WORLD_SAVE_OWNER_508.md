# Chat 1 G21.201 + Chat 2 G18.6.2 — cumulative owner-guard and gameplay-reuse test (508 focused)

## Exact source

Chat 1 [G21.201 core Draft #2630](https://github.com/Trexzo/SpawnPK-RE/pull/2630), exact SHA `3603c9376468b7a0f410f3cf9002449e8de10f2c`, based on certified [G21.200/G18.6.1 #2628](https://github.com/Trexzo/SpawnPK-RE/pull/2628), SHA `3c17ff39884dee2aac995f68e3edee2afa8d7d89` / [Actions #38618035483](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38618035483) SUCCESS (507 focused / Java11/full Gradle). G21.201 adds narrow opt-in JVM owner+generation check in existing normal/final `WorldPlayerPersistence.SaveTask` worker before `FilePlayerRepository.saveForWorld`; new test raises manifest 507→508. Its own Actions need independent success.

Chat 2 [G18.6.2 Draft #2629](https://github.com/Trexzo/SpawnPK-RE/pull/2629), SHA `45c7942350db8db0b87944c068bdfd97b4d651d6`, same certified parent, contributes existing permanent G1847 terminal test **exact Git blob `1f2eb00d8b82232f414fe0e9156a6c19d517011e`** and original coordination note **exact Git blob `e02711f70921877d837b032e1d86fd14b536642a`**. Do not duplicate earlier stacked gameplay tests.

## Distinct boundaries

G21.201: actual TCP WorldPlayer session's optional held in-process account lease has exact owner/generation. Normal and final captured `SaveTask` worker validates that owner and file path against the held token before repository save. The final SaveTask must be permitted after unregister, using captured generation comparison not `owner.accepts`. Test manual preflight rejects foreign player/stale generation/foreign account; counter captured after test probes must increase during actual forced final save. No durable owner epoch, no arbitrary checkpoint/strict writer coverage, no raw writer fencing, no automatic ACTIVE review retirement/recovery.

G18.6.2: a separate direct World gameplay test (NOT TCP or disk persistence) starts G4 ACTIVE task on WorldPlayer A, then unregisters A and re-registers a same-name B before one immutable NPC terminal event. Due to different identity semantics, G4 World/name-level task advances on B, while original A's saved extension remains ACTIVE; G1 kill credits B, G18 DMM advances and one ground settlement occurs. This is custom LocalLab evidence, not historical original SpawnPK rule or client exploit. It does not assert the new opt-in account lease production guard is invoked by direct non-TCP World registrations.

## Integration acceptance

Exactly **three changed files** compared to Chat1 #2630: one Chat2 existing G1847 test overlay, original Chat2 source handoff, this coordination note. ZERO additional production Java or Gradle changes; focused manifest remains **508 unique**. Require own fresh exact-head hosted Actions `G21201_WORKER_SAVE_OWNER_BINDING_PASS`, `G1862_G4_ACTIVE_TASK_NAME_REUSE_PERSISTENCE_SPLIT_PASS`, Java11 major55, **508/508 focused/full Gradle**. Both source PRs have independent CI gates. Keep Draft/unmerged; no main, client, production gameplay, default flag change, ACTIVE unlink, rewards/replay/repair, raw writer or power loss claims.

Open design issues: Chat1 account #2540/#2559, Chat2 gameplay per-consumer outbox #2587, historical reward attribution #2558.