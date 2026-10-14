# Chat1 G21.199 + Chat2 G18.6.0 — forced postimage witness and sticky G4 partial delivery

## Exact source heads

Chat1 G21.199 standalone account postimage diagnostic [Draft #2624](https://github.com/Trexzo/SpawnPK-RE/pull/2624), exact SHA `9199bcd1d0066b674f75e39ee5a3b05b2473dc06`, on certified G21.198/G18.5.9 [Draft #2622](https://github.com/Trexzo/SpawnPK-RE/pull/2622), SHA `d2c3ca089545a5ba37a6169b96a7e2f7f414bd37`, [Actions #38598573806](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38598573806) SUCCESS (505 focused / Java11/full Gradle). G21.199 adds unused forced postimage witness with test-only real TCP final account save and byte-drift negative; **506 focused** candidate, its own hosted CI must pass separately.

Chat2 exact G18.6.0 donor [Draft #2623](https://github.com/Trexzo/SpawnPK-RE/pull/2623) commit `640d0c96dfc879426cde5ef623775ee87c49ae0f`, same certified ancestor. Import existing G1847 test **exact Git blob `5e331afb24795f3fb5f8f48e486dd44a27bd149e`** plus its original handoff document **blob `86a3044f34f7b860c87d503f34da68abc519d69c`**. No duplicated G18.5.9 or earlier test import.

## Tested limits

G21.199 is a standalone package-private diagnostic: test manually writes a file+directory-forced witness containing G21.196 reservation/generation correlation and SHA256/length of an actual G21.192 forced final account postimage following genuine World TCP session. It refuses a missing witness, rejects duplicate creation and catches even same-size raw fixture account byte drift. The witness has no authenticated owner epoch, cannot guarantee underlying save force, cannot exclude raw writers, and can NEVER authorize G21.188 ACTIVE retirement/relogin/recovery.

G18.6.0 uses two **independent canonical installed LocalLab terminal deaths**. A controlled invalid G4 persistence extension causes a nonthrowing sticky G4 refusal while G1 kill/receipt and G18 DMM still progress in two distinct death events (one respawn between). This is test-only CUSTOM_LOCALLAB behavior, not historical SpawnPK server semantics, a production exploit, automatic replay or outbox fix. Chat2 #2587 remains open.

Compared with G21.199 #2624 this combined tree adds ONLY three files: Chat2's existing G1847 test overlay, donor Chat2 handoff doc, and this coordination note. ZERO additional production Java changes, no Gradle changes, focused manifest stays **506 unique**. Preserve all current Chat1 G21.199 and Chat2 G18.5.9 sources, main/client untouched.

Fresh exact-head hosted CI required: `G21199_POSTIMAGE_WITNESS_NEGATIVE_PASS`, `G1860_G4_INVALID_PERSISTENCE_STICKY_PARTIAL_DELIVERY_PASS`, G21198/G1859, Java11 major55, 506/506 focused and full Gradle. Remain DRAFT/unmerged until certified. No automatic ACTIVE retirement, grant/replay/settlement/repair, original rule inference or physical power-loss proof.