# Chat 2 G18.7.0 — even a CURRENT COMPLETED G4 file collides with the SAME completed World task

Date: 2026-10-15.

**Exact certified cumulative parent** [G21.205 + G18.6.7/8/9 draft #2653](https://github.com/Trexzo/SpawnPK-RE/pull/2653), SHA `6e92aa1d3e04e42390b1ec007043d999ebfd5264`, hosted [Actions #38733550687](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38733550687) SUCCESS, **516 unique focused**, Java11 major55/full Gradle. DRAFT/unmerged; previous 515 combined #2652 also hosted green.

## Unique reliability boundary beyond G18.6.8

G18.6.8 showed a VALID but deliberately STALE ACTIVE file colliding with an already-completed same-World Slayer task. G18.7.0 avoids staleness completely: save A's CURRENT COMPLETED task after its real canonical death, then reload that identical completed snapshot into new same-name WorldPlayer B in the SAME still-running World. Source `LocalLabSlayerRuntime.ensureRestoredOwned` does not compare persisted task state/identity to latest task; it refuses on `slayer.active(name) != null || latestTask(name) != null` regardless of decoded ACTIVE/COMPLETED or whether state agrees. Thus B should receive `PERSISTED_STATE_COLLIDES_WITH_LIVE_RUNTIME` **even when the file is up to date**, not just when it's stale.

## G18.7.0 dedicated focused test

1. Register A; legitimately start Monster Hunter G4 at tick77 and DMM EASY. One real installed Monster Spawner NPC death at tick91 with immutable A origin completes the live G4 task to COMPLETED1/source assigned77/transition91 and credits G1 kills1/receipt1, DMM1/3, one coin.
2. **Only after the genuine completion**, explicitly call `FilePlayerRepository.save(PlayerSnapshotCodec.capture(A))`; reload the physical file and retain exact on-disk bytes. This is a current valid COMPLETED account image, not a stale historical state.
3. Unregister A; load file through `FilePlayerRepository.load` and `PlayerSnapshotCodec.applyValidated` into new B with identical name; register B into SAME World. G4's World/local `latestTask(name)` already holds the completed task. `slayer.status(B)` soft rejects the perfectly codec-valid and logically matching COMPLETED state with `PERSISTED_STATE_COLLIDES_WITH_LIVE_RUNTIME`. B's G1 per-player persisted kills starts at1 from the file, whereas the World-local event receipt count remains1.
4. Run the genuine G1 due-time respawn ticket. It spawns a DIFFERENT NPC with immutable new B/generation; one second real canonical NPC death finalizes and adds one independent G1 receipt (B saved kills1→2), World DMM1→2/3, and second one-coin settlement; G4 B remains quarantined with its matching COMPLETED file namespace unchanged. Physical account file bytes remain original completed image until a deliberate future write. Two independent NPC deaths, NOT duplicate event delivery.

Marker: `G1870_CURRENT_COMPLETED_G4_FILE_SAME_WORLD_COLLISION_PASS`.

## Authority/nonclaims

Strictly CUSTOM_LOCALLAB, test-owned **explicit** file save and validated load; NOT authenticated TCP account relog, real asynchronous forced final save, existing G21.205 captured JVM lease/worker admission, durable account epoch, original SpawnPK Slayer/reward policy, external exploit or recovered original behavior. This is a deterministic semantic hydration collision even without stale file content, not yet a source-policy fix. Do not silently skip collision, grant rewards, merge or retire review markers without explicit owner/consumer semantics.

**Diff scope:** exactly 3 files: new independent `G1870SlayerCurrentCompletedFileSameWorldCollisionTest.java`, one manifest entry plus fixed guard count **516→517 unique**, and this source note. ZERO production Java, existing G1847 tests, account/G21.205 saved core, main/client/default flags or original-server behavior changes.

**Fresh hosted exact-head CI required:** G1870/G1869/G1868/G1867/G1866/G21205, 517/517 unique focused, Java11 major55/full Gradle. Parent source success does not certify successor. DRAFT/unmerged. Outstanding original kill attribution [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558), per-consumer outbox [#2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587), account #2540/#2559.