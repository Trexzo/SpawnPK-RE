# G21.211 — primary AccountStore.save bypasses opt-in JVM RAW account gate

## Starting authority

Hosted-green cumulative [G21.210/G18.7.4 #2670](https://github.com/Trexzo/SpawnPK-RE/pull/2670), head `4933ff656e6d7274b2ca9539cdda32b3178a10b4`, Actions #38746226237 SUCCESS with **526** focused, Java11 major55/full Gradle. Named `LocalAccountProfiles.save` now joins the G21.208 optional in-JVM write reservation, but it delegates the primary `opensrc` account to a separate legacy `AccountStore.save`. The latter creates a fixed `.tmp` and replaces the primary file without participating in any in-JVM session gate.

## Genuine account overwrite gap

Test holds a real `WorldPlayer` with primary `opensrc` canonical identity and a bound opt-in `LocalJvmAccountSessionLease` with **both** `spk.local.experimentalJvmAccountSessionLease=true` and `spk.local.experimentalRawWriteJvmLeaseGate=true`. After a *real* queued ordinary World save and original token witness, the account file contains a new World extension. A `FilePlayerRepository.save` for the same exact path is denied as expected; separate test-only direct `AccountStore.save(bank,equipment,movement,pet,player)` then overwrites the primary account via its historical compatibility path, losing the newer extension while the original World lease remains active and bound.

Marker `G21211_PRIMARY_ACCOUNTSTORE_HELD_JVM_LEASE_BYPASS_PASS`. The test does **not** assert ordinary TCP/session use of `AccountStore.save`; this invocation is an explicit direct, test-only forensic writer. A PASS is new **negative evidence**, not an ownership fix. Does not touch production Java, default flags, original SpawnPK client or frozen R25. Exactly test class, Gradle manifest/task count **526→527**, research note.

## Remaining requirements

Independent exact-head GitHub Actions Java11 bytecode major55, full Gradle and **527/527 focused**, including preceding G21210 and G1874. Do not merge into main or claim global writer fencing, durable cross-JVM epochs, startup/authenticated intent, ACTIVE marker retirement, safe positive relog/recovery or G1 reward authority. Issue #2559 remains open.
