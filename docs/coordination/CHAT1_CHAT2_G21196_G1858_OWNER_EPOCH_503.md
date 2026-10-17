# Chat 1 G21.196 + Chat 2 G18.5.8 — cumulative test prototype (503 focused)

## Exact origins

**Chat 1 basis:** standalone non-admitting owner-epoch reservation/binding [Draft #2615](https://github.com/Trexzo/SpawnPK-RE/pull/2615) exact SHA `da8133ffa0c060a63f735230ac4f85b8e4d68e50`, stacked on certified G21.195/G18.5.7 [#2613](https://github.com/Trexzo/SpawnPK-RE/pull/2613) SHA `709e85da11635f1d0137fa864d8cf32bf09ae80d`, Actions #38590398280 SUCCESS (502 focused / Java11/full Gradle). G21.196 adds unused prototype and process-kill tests, focused manifest 502 to 503. Its own exact-head Actions must pass independently.

**Chat 2 donor:** G18.5.8 [Draft #2614](https://github.com/Trexzo/SpawnPK-RE/pull/2614) exact SHA `685ad5f50c752d103547c42be80dd79af474728a`, same certified common parent #2613. Imported its existing G1847 test overlay exact Git blob `5bcb117cafbbf83e6f7f640d7c94a970ddef98dd` and its G18.5.8 coordination document exact Git blob `38c52f80e512954a0ca782d1007dd80bccaa820f`. No repeated imports of older G18.5.x tests.

## Boundaries

G21.196: standalone pre-World generation forced reservation and separately forced caller-supplied generation binding, with two external JVM-kill cutpoints and next-process witness inspection. **NOT wired** to account login, World, G21.188 ACTIVE, save/receipt, or any retirement. Its random epoch and manually supplied generation are not authenticated. Even a consistent pair cannot authorize account reuse.

G18.5.8: deliberately corrupted in-memory G18 objective test during one actual installed LocalLab canonical NPC terminal observer. G1's consumer writes a receipt before G18 throws; finalizer marks terminal finalized and no retry, with one ground settlement and due-time respawn. This is CUSTOM_LOCALLAB test-only partial-consumer failure evidence, not original SpawnPK rule authority or a public exploit. Future per-consumer outbox issue #2587 remains open.

**Exactly three files added/overlaid** compared with #2615: Chat2 existing G1847 test overlay, its exact donor handoff note, this cumulative note. ZERO changes to server production classes or `server/build.gradle` beyond G21.196 already present. Preserve all existing G21.195 tests and prior G18.5.x. Manifest stays **503 unique**.

## Acceptance

Require fresh exact-head hosted Actions: G21196_OWNER_EPOCH_CUTPOINT_PASS, G1858_INSTALLED_G18_CONSUMER_FAILURE_NO_RETRY_PASS, G21195, Java 11 major55, 503/503 focused and complete Gradle. Do not assume either separate parent CI certifies composition. Draft/unmerged, no main/release/client changes, no flags default-on, no grants/replay/settlement/review marker cleanup/recovery or machine power-loss claim.