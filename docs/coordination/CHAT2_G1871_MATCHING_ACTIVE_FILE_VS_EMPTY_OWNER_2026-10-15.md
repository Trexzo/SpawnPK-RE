# Chat 2 G18.7.1 — matching current ACTIVE disk file denied, empty successor completes same World task

Date: 2026-10-15.

**Certified cumulative parent**: [G21.205 + G18.7.0 #2654](https://github.com/Trexzo/SpawnPK-RE/pull/2654), exact commit `a1c00f1b03dee38d2b67f01c3bd9a23ee8724ae2`, hosted [Actions #38736017724](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38736017724) SUCCESS with **517 unique focused**, Java11 major55 and full Gradle. All PRs remain draft/unmerged.

## Independent source-backed case

G18.7.0 already proved even a current valid **COMPLETED** G4 file is denied against the same-World completed `SlayerTaskService.latestTask(name)`. G18.7.1 tests the more damaging **CURRENT and MATCHING ACTIVE** case and adds a third **EMPTY extension** replacement control. `LocalLabSlayerRuntime.ensureRestoredOwned` rejects a codec-valid persisted extension on a same-name replacement when the World has ANY live `slayer.active` or latest task; it does not verify whether the saved ACTIVE snapshot matches that live task. An owner with no extension skips collision detection and can see the very same World task.

1. World A registers player A and starts Monster Hunter at tick77 (ACTIVE0), activates G18 DMM EASY, **explicitly saves the CURRENT ACTIVE account file** using `FilePlayerRepository.save(PlayerSnapshotCodec.capture(...))`. A has no prior kill or progress; this snapshot is NOT stale. Spawn genuine canonical NPC with immutable `RecipientOrigin=A`.
2. A unregisters without completing anything. Replacement B under same name uses **REAL file load plus `PlayerSnapshotCodec.applyValidated`**, inheriting exact codec-valid ACTIVE77. G4 hydration sees the still-ACTIVE World task and refuses B with `PERSISTED_STATE_COLLIDES_WITH_LIVE_RUNTIME`. B's `slayer.status(name)` has no exposed task despite a real `SlayerTaskService.active(name)` at progress0.
3. First real installed Monster Spawner NPC death (A-origin, B-current) settles one coin, credits G1 B kills1/receipt1 and advances World-owned G18 EASY to1/3. G4 does NOT advance the ACTIVE World task (progress remains0); B's valid ACTIVE extension stays intact, file bytes unchanged. No thrown observer exception or replay.
4. B unregisters. New same-name player C has **EMPTY G4 extension** and sees the EXACT SAME active World task ID/progress0 without conflict. A real G1 due-time respawn creates a DIFFERENT NPC bound to C's exact `WorldPlayer`/generation. Second distinct canonical death completes the original G4 task for C and persists COMPLETED1 on C; G1 B1/C1/receipt2, G18 2/3 and two independently settled one-coin NPC deaths. B's original matching ACTIVE file remains unmodified. No same-terminal replay.

Expected marker: `G1871_MATCHING_ACTIVE_FILE_BLOCKS_G4_BUT_EMPTY_OWNER_COMPLETES_PASS`.

## What this does and does not prove

This is a **CUSTOM_LOCALLAB** same-World player hydration/policy contrast. The matching ACTIVE file is explicit test-managed on-disk persistence, not an authenticated TCP LocalSession reload, actual async World final-save or G21.205 JVM account admission. The subsequent blank replacement intentionally has no snapshot and is **not** a proposed reconciliation fix. It proves the current username-scoped runtime task can be available to an empty extension while a matching valid account image fails closed, but does NOT recover original SpawnPK Slayer/attacker/recipient rules, authenticate ownership transfer, or justify reward replay/account recovery.

**Diff vs #2654 exactly three additive files:** dedicated new `G1871SlayerMatchingActiveFileVsEmptyReplacementTest.java`, one focused manifest entry and both fixed guards in `server/build.gradle` **517→518 unique**, and this note. No production Java, G21.205 account core, G1847 existing tests, defaults, main or client changes.

Fresh hosted exact-head CI required G1871/G1870/G1869/G1868/G21205, **518/518 focused**, Java11 major55 and full Gradle. Keep draft/unmerged. [Gameplay reliability outbox #2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587), [original kill authority #2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558), [account #2540](https://github.com/Trexzo/SpawnPK-RE/issues/2540) and [#2559](https://github.com/Trexzo/SpawnPK-RE/issues/2559) remain OPEN.