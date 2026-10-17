# Chat 2 G21.182 — exact core × gameplay integration manifest

Date: 2026-10-14. Draft candidate, no merges to main.

**Core parent:** Chat 1 [G21.182 PR #2561](https://github.com/Trexzo/SpawnPK-RE/pull/2561), exact `3f92f5d35db4e1c8416a54e6cc97e852053960d0`, itself stacked on G21.180/181 account admission and same-JVM lease experiments.

**Gameplay source:** Chat 2 [G18.4.6 #2562](https://github.com/Trexzo/SpawnPK-RE/pull/2562) exact `bbdf485cd6ce1422d017ff9f3b92c10f2be57200`, stacked on hosted-green [G18.4.5 #2556](https://github.com/Trexzo/SpawnPK-RE/pull/2556).

## Composition rules

- Start with **exact Chat 1 G21.182 Git tree**. Retain `LocalJvmAccountSessionLease.java`, `LocalSessionPlayerInitializer.java`, `WorldPlayerPersistence.java`, all three G21.180/.181/.182 real-socket tests and research notes, and all other core code unchanged.
- Import **exact Git blob IDs** for Chat 2 G18.4–G18.4.6 gameplay, test and handoff files that do not conflict.
- Only two shared files exist: `server/build.gradle` and `server/src/spk/local/LocalSession.java`.
- Compose Gradle from Chat 2 G21.179 485-test manifest, add exactly the three G21.180/.181/.182 test classes and their **verbatim** dedicated Gradle tasks from Chat 1 G21.182, obtaining **488 unique focused tests**.
- Compose LocalSession from **Chat 1 G21.182** source, preserving experimental JVM account lease token uptake and release-after-confirmed-final-write lifecycle. Insert only Chat 2's exact `uiActions.installDailyMoneyMakingUiHandler(new LocalDailyMoneyMakingUiHandler(...))` post-login binding after player registration and before World-command gate activation.

## Safety / claims

- Java11 class major55 and 488/488/full Gradle require **fresh hosted CI on the combined exact SHA**. Green component CI is not combined certification.
- G21.181/182 JVM lease is opt-in and default OFF, **not durable restart safety**; no original SpawnPK persistence protocol/recovery authority.
- G18.4.5 spawn-recipient generation witness is not proven **kill-credit origin**. G18.4.6 test demonstrates exact lethal attacker may differ from configured LocalLab recipient. Five G4/G14/G15/G16/G18 gameplay reward consumers unchanged; G18.4.4 exact generation credit API remains opt-in and **unwired** to production.
- Original server rules remain `UNKNOWN_SERVER_AUTHORITY`. User goal is eventual original client/server source reconstruction, not claim that Custom LocalLab substitutes for original.
- Chat 1 retains core/persistence/session release promotion authority; Chat 2 gameplay. Chat 3 client deobfuscation separate. Frozen R25 release pointer untouched.

## Status / handoff

New draft PR on G21.182 will be the **one additive integration candidate** once hosted 488 tests pass. Do not merge older stacked Chat 2 PRs separately on top of it. Preserve issue [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558) (actual kill-credit origin) and [#2547](https://github.com/Trexzo/SpawnPK-RE/issues/2547) (DMM objective lifetime). No inferred policy fix.
