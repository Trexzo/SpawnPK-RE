# Chat 2 G21.192 + G18.5.4 — real account file receipt-gap characterization on forced-World-save core

Date: 2026-10-14. Draft/unmerged.

## Immutable exact authorities

**Core parent:** Chat 1+2 [G21.192/G18.5.3 joint #2601](https://github.com/Trexzo/SpawnPK-RE/pull/2601), SHA `2f169cea42acd2a83313dc48bc194695011124f9`, adding DEFAULT-OFF `spk.local.experimentalForceWorldAccountPostimage=true` to **guarded** real `FilePlayerRepository.saveForWorld` only, with atomic rename, file+directory force, independent readback, and genuine two-World TCP test. Does not retire ACTIVE review marker or establish crash-durable account recovery.

**Gameplay/test source:** Chat 2 [G18.5.4 #2602](https://github.com/Trexzo/SpawnPK-RE/pull/2602), SHA `e6d397acf944c26354e09ac22ba8dcc5f7693f50`, independently stacked on certified G21.191/G18.5.3 [#2599](https://github.com/Trexzo/SpawnPK-RE/pull/2599) SHA `3e446f5d5c7a12f55493e143b85c58966caa4e6f`.

## Composition

- Begin with **exact G21.192 #2601 source tree**. Preserve all production `FilePlayerRepository.java`, `WorldPlayerPersistence`, `LocalSessionPlayerInitializer`, account-lock/sidecar logic, `G21192CoreSocketForcedWorldPostimageTest` and **`server/build.gradle` unchanged** (focused **499 unique**).
- Overlay exactly the G18.5.4 existing checked `G1847MonsterSpawnerNameReuseTerminalAttributionTest.java` Git blob `7e61d0e3d3589610146fcb444c9b1d63e8683a4b`. Add G18.5.4 source/limits note blob `620779da4c9e758daa2842cb63f4051bc04c2586` and this joint handoff only; **3 paths, zero production Java change, no manifest new entry**.
- G18.5.4 first obtains a real canonical LocalLab NPC kill and installed G1 observer receipt/kill=1, then uses an explicit **raw/direct FilePlayerRepository.save** in a private temp file. Closes old World, loads account via new repository object, `PlayerSnapshotCodec.applyValidated` into fresh WorldPlayer/World, sees kills1 but zero receipt map, and manually repeats historical id/deathTick: kills2 and fresh G1 receipt1. Directly saves/reloads again to confirm extension kills2.
- **Do not conflate with G21.192's new opt-in guarded `saveForWorld` path**. G18.5.4 deliberately tests raw/forensic repository round trip, NOT a real session final save, authenticated TCP login, JVM restart, forced publication or power-loss cutpoint. It establishes only `CUSTOM_LOCALLAB` consumer receipt persistence/identity gap under explicitly manual test delivery. No original SpawnPK reward policy, unprompted production replay or exploit evidence.

## Validation, ownership, nonclaims

**Both individual source builds and NEW exact combined head must pass** fresh GitHub-hosted G1854/G1853/G1852/G1851/G21192/G21191/G21190 regression markers, **499/499 focused**, Java11 major55, full Gradle. **At initial composition individual #2602 and #2601 hosted runs still pending. Never claim green early.**

Future scoped idempotent per-consumer terminal outbox tracked in [#2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587); original kill/respawn authority remains `UNKNOWN_SERVER_AUTHORITY` [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558); durable account admission/recovery [#2540](https://github.com/Trexzo/SpawnPK-RE/issues/2540)/[#2559](https://github.com/Trexzo/SpawnPK-RE/issues/2559) unresolved. Chat 1 owns core and promotion; Chat 2 gameplay test/recovery; Chat 3 client deobf. No main merge, no changes to frozen R25 pointer. Do not merge predecessor slices separately if this cumulative candidate is accepted.
