# Chat 2 G18.4.8 — G1 PvM record also credited to replacement username generation

Date: 2026-10-14.
**Parent** [canonical joint G21.184 + G18.4.7 draft #2572](https://github.com/Trexzo/SpawnPK-RE/pull/2572), exact `952abf4980e23144dfbf1ad06142c79290efe66a`. Chat 1 and Chat 2 independently closed duplicate #2571/#2572; #2572 was subsequently reopened as canonical on Chat 1's published choice, and #2571 remains closed. No main merge.

**Evidence**: `G1MonsterSpawnerPvmLoopService.onFinalized` on `MonsterSpawnerPvmRuntime.FinalizeResult` uses `world.players().byName(finalization.recipientRef)` at **terminal delivery**, not immutable `FinalizeResult.recipientOrigin` captured at NPC spawn. It passes this current WorldPlayer and generation to `PvmRecordService.recordIfCurrent`, which only verifies **present mutation ownership** and increments that player's `pvm-record` snapshot extension under World ownership. The replay key `(npcId,deathTick)` prevents duplicate increments; it does not check whether the recipient's generation was present at spawn or damage admission.

## Test-only extension

Extend the existing, already checked `G1847MonsterSpawnerNameReuseTerminalAttributionTest` (no new test class or manifest change, remains 491 focused on #2572):

1. Original A spawns NPC with immutable A+GA origin, disconnects, replacement B registers with same username, *actual installed LocalLab provisioning terminal observer* processes one synthetic lethal NPC.
2. Assert previous G18.4.7 DMM observation still holds. **Additionally** require `world.pvmRecords().snapshot(original).kills==0`, `snapshot(replacement).kills==1` and `receiptCount()==1`. The G1 record is attached to B's current generation, not the old origin.
3. Control: no user with this name is registered at terminal time. A later replacement registers only to inspect state after the callback; both A/B PvM kill counters stay zero and `receiptCount()==0`.
4. New secondary positive marker `G1848_PVM_RECORD_USERNAME_REUSE_CHARACTERIZATION_PASS`; existing `G1847_NAME_REUSE_TERMINAL_ATTRIBUTION_CHARACTERIZATION_PASS` remains.

**Production code and original authority unchanged**. No assertion that original SpawnPK server should grant the kill to original spawn owner, actual attacker or top damage dealer. G18.4.6 showed those identities may differ. This is a test-only **CUSTOM_LOCALLAB** username-reuse hazard, not a live fix, real-client exploit or historical rule. Changing policy requires first recovering original server attribution evidence or clearly selecting a caller-owned LocalLab rule, then carrying attack identity through death/finalization and retries.

## Hosted certification

New exact child head must pass Java11 bytecode major55, both G1847 and G1848 markers, all **491** current-train focused, and full Gradle. The ancestor #2572's own hosted run must also be separately certified; a historical failure from the uncorrected G1847 control does not override passing corrected source evidence. Draft/unmerged; Chat 1 review/promotion only. DMM lifetime [#2547](https://github.com/Trexzo/SpawnPK-RE/issues/2547), kill-credit attribution [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558), and core account safety [#2540](https://github.com/Trexzo/SpawnPK-RE/issues/2540) remain open.
