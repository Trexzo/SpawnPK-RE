# G21.200 — bind live World generation to existing opt-in JVM account lease

## Certified ancestor and strict scope

Base is certified G21.199 + Chat2 G18.6.0 combined [Draft #2625](https://github.com/Trexzo/SpawnPK-RE/pull/2625), exact commit `2fa114cf5ad2778aac4a60756758fa95b0df04e1`, hosted [Actions #38602114399](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38602114399) SUCCESS with **506 focused/Java11/full Gradle**. The G21.196 prototype reservation/binding and G21.199 forced postimage witness are deliberately UNUSED and unauthenticated, as G21.198 negative tests prove.

## Actual narrowly scoped opt-in runtime change

`LocalJvmAccountSessionLease.Token` now stores **in memory only** its exact registered `WorldPlayer` object identity and returned World generation, after the normal G21.181 same-JVM lease (and optional G21.185 OS lock) has been acquired. Immediately after `World.registerPlayerAndStart` returns, `LocalSessionPlayerInitializer` binds that live World owner/generation into the existing lease if nonnull. Binding refuses a null/unregistered/different account player, an invalid generation, a changed token identity, or incompatible rebinding; its live check refuses different owner identity, stale generation, an unregistered player or account lease no longer in the active map. It is NOT an independently durable authenticated epoch, NOT a cross-process token, and is not yet consulted by `WorldPlayerPersistence.saveForWorld` or `FilePlayerRepository`.

After World membership has committed, any later initializer failure must not release the opt-in account lease under still-owned World state. That post-registration path now intentionally retains the fail-closed JVM token; before a successful World registration it still releases on initialization failure. Healthy final save still uses existing confirmed-save release, clearing in-memory bound identity. **Existing default-off behavior is unchanged** because the binding exists only for a nonnull enabled lease.

## Regression

`G21200CoreJvmLeaseWorldGenerationBindingTest` performs a genuine synthetic 317/ISAAC TCP session with real World registration, observed World generation and cooperating opt-in locks. It checks that the exact live account file + `WorldPlayer` instance + generation are accepted by the new JVM-only test witness, while (a) another `WorldPlayer` forged with identical username/generation, (b) the same player with a stale/future generation, and (c) a different resolved account path all fail. It continues the existing G21.197 standalone epoch prototype correlation test and real G21.192 forced World final save; after successful logout, the released owner must no longer be accepted. G21.190 ACTIVE remains and a fresh World TCP login is still denied.

## Acceptance and limitations

Diff against the certified parent should include only: G21.181 JVM lease class, LocalSessionPlayerInitializer, one new test, Gradle manifest/task, and this research note. No file repository/World/LocalSession save algorithm or Chat2 gameplay source changes. Focused manifest **506→507 unique**. Require fresh exact-head hosted `G21200_JVM_WORLD_GENERATION_BINDING_PASS`, G21199/G1860 and previous markers, Java 11 major55, **507/507 focused + full Gradle** before calling this certified. Keep Draft and unmerged.

**No durable account owner authorization:** A JVM pointer and generation do not survive process death; they cannot authenticate an external replay, tie G21.199 content witness to the actual saving thread, defend against a raw writer, safely retire G21.188 ACTIVE marker, grant login, repair/replay rewards or establish physical power-loss safety. Genuine durable epoch binding/fenced snapshot publication, an owner-matched final-save receipt and independent forced retirement cutpoint protocol remain #2540/#2559. Gameplay per-consumer outbox #2587 remains Chat2. No main/client changes.