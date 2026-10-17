# Chat 1 + Chat 2 newest 822 candidate — corrected recipient identity

**Exact core parent:** Chat 1 [PR #2918](https://github.com/Trexzo/SpawnPK-RE/pull/2918), commit `fe3cfa2a4f78a0582f66a9d222b6cf0952fe67b2` (822 focused test candidate) based on earlier [#2916](https://github.com/Trexzo/SpawnPK-RE/pull/2916) 819-tree. Core includes G21355–59 ACTIVE identity, alias and pre-move guard regressions.
**Exact stronger gameplay donor:** Chat 2 [PR #2915](https://github.com/Trexzo/SpawnPK-RE/pull/2915), corrected commit `36e316fa3a34bb0c9173a11a014ef5f5b1f40fed` (817 candidate); source difference since earlier [#2913](https://github.com/Trexzo/SpawnPK-RE/pull/2913), exact `70ffcd6ddfef51c122d2e326d90869a0713040a6`, is **seven Git blobs** only: 2 gameplay production classes, G2021–24 tests and a coordination note.

## Why this overlay is necessary
The older G2021 test mistakenly assumed the numeric `WorldPlayer.generation()` monotonically increases between two distinct replacement `WorldPlayer` objects. A hosted CI run on the earlier Chat 2 commit failed, exposing that a same-name rebind can have the same numeric generation and same `SAME_NAME_REBOUND` origin class. The corrected `LocalLabSettledDeathEnvelope` now pins the actual in-process observed `WorldPlayer` **reference**, and the experimental non-granting terminal attempt fence refuses a later stage for the same event if observed reference OR generation changes. A legitimate stable recipient may still obtain separate stage reservations.

The donor's G2023 runs twelve simultaneous claims for one stage (one successful reservation only). G2024 exercises two claims per each of six stages plus original 43-death capacity-fail-closed control. G2021 tests the actual same-name collision and G2022 stable-player case.

## Provenance and gate
This overlay imports the seven donor Git blobs unchanged. It intentionally **does not touch `server/build.gradle`** (core 822 manifest already contains the same G2021–24 classes and tasks), and does not touch Chat 1 `FilePlayerRepository`, `LocalFinalSaveNegativeWitness`, any real account/gameplay reward/settlement implementation or core tests. Full 822 focused CI + G2021–24 and G21357–59 markers, Java11 major 55 and full Gradle must pass on EXACT HEAD; the prior failed Chat 2 head is not certified. DRAFT only, main remains unchanged.

The attempt fence is unwired CUSTOM_LOCALLAB process-only diagnostic work, not original SpawnPK credit authority, durable death ID/outbox, atomic per-consumer grant+ACK, authenticated owner epoch, or restart replay. #2587 remains open.
