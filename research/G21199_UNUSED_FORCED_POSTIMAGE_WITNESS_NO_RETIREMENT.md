# G21.199 — UNUSED forced postimage witness after a real World final save

## Exact verified parent

Based on [G21.198/G18.5.9 combined PR #2622](https://github.com/Trexzo/SpawnPK-RE/pull/2622) exact commit `d2c3ca089545a5ba37a6169b96a7e2f7f414bd37`, [Actions #38598573806](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38598573806) SUCCESS (505 focused, Java 11 major55, full Gradle). Chat1 G21.198 showed an unused reservation/binding pair can be forged and does not bind account contents. The next primitive adds a **content witness**, but explicitly does NOT solve authentication or owner fencing.

## Standalone helper, not a production account receipt

`LocalDurablePostimageWitnessPrototype` is an UNUSED package-private diagnostic. A test that has separately confirmed actual World save success can call `publishAfterConfirmedSave(account)` to observe the exact current regular account file (bounded bytes/fileKey/size checks), the existing G21.196 reservation digest and structurally bound generation, then exclusively write a small new `.g21199-postimage-witness` sibling recording the account's SHA-256 and exact byte count. Force witness file and directory and read back. No overwrite, unlink or retirement method exists. Missing, mismatched, substituted or changed witness/account bytes fail closed in `requireMatchingCurrentPostimage`. File/directory force belongs only to the **witness**; the helper does not itself force or authenticate the account write, verify session membership, hold an OS lock, or exclude raw competing writers.

The positive structural digest comparison is NOT an authenticated postimage receipt. The G21.196 record generation is still caller-supplied, the reservation UUID unauthenticated, and a raw actor could rewrite the account and all structural records. Even a matching pair, readback-verified account bytes, and fully forced witness do NOT authorize G21.188 ACTIVE review retirement, account admission, recovery or replay.

## G21.199 test sequence

`G21199CoreRealWorldPostimageWitnessNegativeTest` is based on the CI-green G21.197 genuine 317/ISAAC TCP World test: test code forces an unused reservation before login, observes the actual registered World generation and writes a forced binding manually, real World-owned extension mutation and G21.192 forced final account save complete, and a G21.190 ACTIVE sidecar remains. Before witness creation, inspection refuses a missing witness. Test manually publishes and verifies the diagnostic postimage witness, rejects duplicate creation, then a fresh World must refuse a real second TCP account login despite matching reservation/binding/witness. Exact account/marker/prototype/witness bytes must be unchanged after rejection.

Finally the test changes one account byte **without changing file length** through direct private-fixture raw I/O. The same witness must reject with `G21.199 ACCOUNT_POSTIMAGE_DRIFT_NO_AUTHORITY`; G21.188 ACTIVE and witness bytes remain unchanged. This is *not* a production exploit, externally sourced Save receipt, power loss or guaranteed race exclusion. No account file is modified except the temporary test fixture.

## Acceptance

Change exactly 4 files against certified #2622: unused helper, new Java integration regression, Gradle manifest **505→506 unique** and this research note. No edits to any existing production class, `LocalSession`, `World`, `FilePlayerRepository`, G21.188 marker, or Chat2 G18.5.9 gameplay test; all experimental flags remain default OFF. Require **fresh exact-head hosted CI** with `G21199_POSTIMAGE_WITNESS_NEGATIVE_PASS`, 506/506 focused, Java11 bytecode major55 and full Gradle. Draft/unmerged. This does not authorize ACTIVE retirement, safe account reuse, trusted epoch, final-save receipt, grants, replay or main merge. Continue [#2540](https://github.com/Trexzo/SpawnPK-RE/issues/2540) and [#2559](https://github.com/Trexzo/SpawnPK-RE/issues/2559); gameplay outbox #2587 remains Chat2-only.