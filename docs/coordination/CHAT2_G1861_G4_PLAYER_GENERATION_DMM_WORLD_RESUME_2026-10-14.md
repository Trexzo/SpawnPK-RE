# Chat 2 G18.6.1 — per-WorldPlayer G4 invalid cache vs username-owned G18 world progress under same-name replacement

Date: 2026-10-14.

**Exact source parent:** [joint G21.199 + G18.6.0 draft #2625](https://github.com/Trexzo/SpawnPK-RE/pull/2625), SHA `2fa114cf5ad2778aac4a60756758fa95b0df04e1`, hosted [Actions #38602114399](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38602114399) **SUCCESS**, **506 unique focused** and Java11 major55/full Gradle. Parent draft/unmerged and all G21.199 postimage-witness/account code remains untouched.

## Source-derived lifecycle difference (CUSTOM_LOCALLAB only)

- `LocalLabSlayerRuntime.invalidPersistence` is an `IdentityHashMap<WorldPlayer,String>` and `hydratedPlayers` is a set keyed by `WorldPlayer` identity. An invalid `blood-slayer-g4` snapshot extension is latched fail-closed for that exact player. It is **not** evidence of a durable account-level quarantine policy.
- `LocalLabDailyMoneyMakingRuntime.byPlayer` is keyed by normalized account ref *within the same World* and persists when an owner is unregistered. A replacement player with the same name resumes existing DMM progress.
- `PvmRecordService` increments the receiving `WorldPlayer`'s snapshot extension, while its event receipt map is World-service local. G18.4.7–.9 previously established real installed observer name-only terminal attribution and delayed respawn across changed generations.

## Added G18.6.1 regression

1. Register old WorldPlayer A, activate G18 EASY 0/3, inject **test-only** malformed G4 extension on A before first G4 hydrate, verify G4 soft invalid state. Through the unchanged installed Monster Spawner observer, spawn/die/finalize one canonical NPC. Verify G1 A kills1/receipts1; G18 progress1/3; G4 remains invalid; one coin settled.
2. Unregister A after terminal. Verify its exact generation is invalid and World-owned G18 has one activity instance still. Register **new WorldPlayer B** with the identical name and a fresh empty snapshot extension; verify G4 has NO inherited persistence error and no active task. Verify G18 continues at 1/3 on B and reactivation is idempotent.
3. Start a **legitimate** G4 Monster Hunter task on B (0/1), without altering G4 internals. Deliver the previous canonical G1 due-time respawn and inspect the new NPC's real `RecipientOrigin` for B and B's World generation. Kill/finalize the **new NPC**, verify G4 Monster Hunter task completes 1/1, G18 1→2/3, G1 credits A and B independently (kills1 each), and receipts2 in same World.
4. Confirm each terminal has a distinct one-coin settlement receipt, and aggregate owner-scoped coin amount2. GroundItemRegistry may merge matching stacks, so **do not assert two ground-item objects**.

Marker `G1861_G4_GENERATION_REPLACEMENT_DMM_RESUME_PASS`.

### Limits / outcome

This is a deterministic same-World / in-process WorldPlayer replacement, **NOT** a socket account login, full JVM restart, account file hydration, original SpawnPK recipient/reward policy, validated outbox or persistent replay. No old canonical event is manually replayed. Old G4 invalid state does not automatically migrate to B; this is a **source-derived lifecycle boundary**, NOT advice to discard genuine account persistence problems on relog. Newly spawned NPC carries B as its captured origin; neither consumer has gained original server kill-credit authority. Whether an external client can forge corrupted extensions is **not** established.

This extends the [per-consumer outbox issue #2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587), which requires explicit recipient-generation and per-consumer outcomes before any replay. Original kill policy issue [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558) remains UNKNOWN_SERVER_AUTHORITY.

**Diff:** 2 files (existing G1847 terminal test + this note), 0 production Java, Gradle, manifest/account, or client changes; **506 unique focused entries unchanged**. Fresh exact-head hosted CI must prove G1861/G1860/G1859/G21199 markers, Java11 major55, **506/506** focused and full Gradle. DRAFT/unmerged. Chat1 owns core integration and promotion; avoid stacked duplicate imports.
