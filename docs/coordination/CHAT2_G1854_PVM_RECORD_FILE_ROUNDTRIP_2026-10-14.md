# Chat 2 G18.5.4 — PvM receipt loss across a real repository file and a fresh World

Date: 2026-10-14. Draft/unmerged.
Exact source parent: [G21.191 + G18.5.3 cumulative #2599](https://github.com/Trexzo/SpawnPK-RE/pull/2599), `3e446f5d5c7a12f55493e143b85c58966caa4e6f`, hosted [Actions #38572330731](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38572330731) **SUCCESS**, G1852/G1853, G21190/G21191, 498/498 focused, Java11 major55/full Gradle.

## New specific question

G18.5.3 proved `PvmRecordService.receipts` is an instance-local map: a new `PvmRecordService(world)` over a **same live WorldPlayer** can accept a repeated `(npcId,deathTick)` although the player's extension namespace already shows one G1 kill. That earlier test specifically did **NOT** touch a file repository, a fresh World or snapshot application.

This G18.5.4 experiment changes the boundary in a **disposable, explicit file round trip**:
1. Construct `FilePlayerRepository` with a **private temp directory path resolver** and `World.isolatedForTest(tick,repository)`; register a WorldPlayer.
2. Use **actual unmodified LocalLab provisioning and installed G1 terminal observer** to spawn, kill and settle one canonical NPC. Verify `pvm-record.kills=1`, one G1 in-memory receipt and one committed ground drop.
3. Capture the player's actual `PlayerSnapshotCodec.capture(account,player)` and call `FilePlayerRepository.save(snapshot)` **directly**. This is the repository's **RAW/forensic API**, deliberately NOT `saveForWorld`, not an admitted `WorldPlayerPersistence` final-save ticket and not durable session recovery. Confirm real file exists and `FilePlayerRepository.load(account)` returns extension `extension.pvm-record.kills=1`.
4. Unregister and close original World. Create an **independent fresh World and PvmRecordService**, using a new repository object pointing at the same private file. `PlayerSnapshotCodec.applyValidated(loaded,new WorldPlayer)` is a **test-only explicit hydrate**, not an authenticated real-TCP login. Register rehydrated WorldPlayer and confirm **kills=1** while fresh service receipt map is **empty**.
5. Explicitly replay the **same historical** `npcId,deathTick` to fresh World's `PvmRecordService.recordIfCurrent`. Expect applied=true, kills **1→2** and new in-memory receipt1; next duplicate returns replay, kills unchanged. No NPC death or ground drop on the fresh World.
6. Independently capture/direct-save again, reload and confirm account extension now says `kills=2`. Clean only the private temporary tree.
7. New marker `G1854_PVM_RECORD_FILE_ROUNDTRIP_RECEIPT_GAP_PASS`, retain G1853/G1852/G1851/G1850/G1849/G1848/G1847 and core G21191/190.

This is a **real file-repository serialization and independent World boundary**, a stronger test than G18.5.3, but importantly **NOT an actual LocalSession login/save, final disconnect save, JVM restart/process kill or crash-consistent persistence proof**. No assertion that real production automatically redelivers NPC events, no original SpawnPK server idempotency/credit policy, no claim to an exploit. The test alone intentionally manually replays an old identity to expose the missing persisted G1 receipt.

## Scope, reliability and future work

Only existing checked G1847 test plus this note. No `server/build.gradle`, production runtime, account/persistence, default flags, or protocol changes. The 498 focused manifest remains **498 unique**.

Prospective scoped `CUSTOM_LOCALLAB` per-consumer event/outbox issue [#2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587) must handle both G1852 aggregate replay (G1 dedups but G18 advances again) and G1853/G1854 new-instance/new-World receipt lifetime. Historical kill authority [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558) remains UNKNOWN_SERVER_AUTHORITY. Chat 1's G21.191 postimage primitive is STANDALONE, unused by actual world saves or review-marker retirement; no crash-durable account recovery claim.

Require fresh exact-head GitHub Actions Java11 major55, `G1854_PVM_RECORD_FILE_ROUNDTRIP_RECEIPT_GAP_PASS`, 498/498 focused, full Gradle. Chat 1 owns merge/promotion; no duplicate import of older stacked Chat 2 sources. Frozen R25 unchanged, Chat 3 client deobf separate.
