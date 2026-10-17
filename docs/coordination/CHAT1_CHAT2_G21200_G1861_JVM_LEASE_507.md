# Chat 1 G21.200 + Chat 2 G18.6.1 — combined JVM lease/World generation and gameplay lifecycle (507 focused)

## Exact source chain

Chat 1 [G21.200 Draft #2627](https://github.com/Trexzo/SpawnPK-RE/pull/2627) exact head `cced6708820f44d7a1a7ea0420f1a0c7e422584f`, stacked on verified Chat1+2 [G21.199/G18.6.0 #2625](https://github.com/Trexzo/SpawnPK-RE/pull/2625) exact SHA `2fa114cf5ad2778aac4a60756758fa95b0df04e1`, hosted [Actions #38602114399](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38602114399) SUCCESS 506 focused, Java11/full Gradle. G21.200 adds a narrow JVM-only opt-in account token binding to exactly registered WorldPlayer identity and generation; manifest 506→507. Its separate CI must pass.

Chat 2 [G18.6.1 Draft #2626](https://github.com/Trexzo/SpawnPK-RE/pull/2626) exact head `5e9e233cb0e1c0ce80d40b9f2457fe24019965b0`, from same certified #2625 parent, adds G1847 installed terminal test **exact donor blob `3a4d3cd695ccf79ac2b8ca48817d6417e42c7781`** and its original scope handoff **exact blob `74c067f8ae2ba34daff077ea236e09f8802dcc2a`**. Preserve all previous G18.6.0 tests and Chat1 owner tests. No additional production Java or Gradle changes beyond G21.200.

## Independently delimited owner and gameplay observations

G21.200: Actual opt-in G21.181 current same-JVM lease is bound immediately following returned `World.registerPlayerAndStart` to a WorldPlayer object and exact generation. Live tests should reject another WorldPlayer with same username/generation, stale generation and different account path; after healthy confirmed final save the lease is released. No persisted owner epoch, save write fencing, forced owner-matched final receipt, ACTIVE retirement or cross-JVM recovery. Post-registration exceptions retain the negative lease; before registration failure still releases.

G18.6.1: the test uses direct World registration (not TCP or persistence) to replace WorldPlayer A with a new same-name B. G4 invalid persistence is object-keyed so B can start a clean task; G18 DMM persists World-locally by username and resumes prior progress; G1 kill receipt remains owner-snapshot-local, with separate death receipts and two coin settlements across G1 due-time respawn. This is LocalLab-only source evidence, not original SpawnPK reward rules nor authentication guarantees. G21.200's JVM lease feature is not invoked by the manual gameplay World test.

## Exact acceptance

This integration branch should differ by only **3 files** from G21.200 #2627: exact Chat2 existing G1847 test overlay, exact donor handoff, and this coordination note. Manifest stays **507 unique**. **Fresh exact-head** hosted CI required, including `G21200_JVM_WORLD_GENERATION_BINDING_PASS`, `G1861_G4_GENERATION_REPLACEMENT_DMM_RESUME_PASS`, Java11 major55, 507/507 focused/full Gradle. Leave DRAFT/unmerged and all feature flags default OFF. No main/client, automatically cleared ACTIVE, grant/replay/repair, hostile raw writer or power-loss proof. Core design #2540/#2559 and per-consumer outbox #2587 remain OPEN.