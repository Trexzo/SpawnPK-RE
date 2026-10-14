# Chat 2 G21.183 cumulative bridge — tested gameplay plus latest JVM lease flag guard

Date: 2026-10-14. Draft integration, no main merge.
**Core parent** Chat 1 [G21.183 #2564](https://github.com/Trexzo/SpawnPK-RE/pull/2564), exact `eb8af4565a30b141e5fdb1db03e78df4c27f00dd`. **Chat 2 gameplay source** [G18.4.7 #2565](https://github.com/Trexzo/SpawnPK-RE/pull/2565), exact `bba368cf5d359766a59254955f71e4a775dc3ed1`, stacked on [G21.182 cumulative #2563](https://github.com/Trexzo/SpawnPK-RE/pull/2563).

## Composition

Start with **Chat 1 G21.183's exact tree**, which already includes G21.180-183 socket/account tests, the default-OFF experimental JVM session lease, and its G21.183 pre-existing-token opt-out bypass fix.

G21.183 changes four files compared with its G21.182 core base: new socket test, new research note, `server/build.gradle` and `LocalJvmAccountSessionLease.java`. It does **not** modify `LocalSession.java` beyond G21.182. Therefore the already composed Chat 2 version of `LocalSession.java` is applied as its exact Git blob: all G21.182 account-lease acquisition and release logic retained plus the exact-generation post-login G18.4 Daily Money Making UI handler.

Import other Chat 2 non-conflicting source/test/coordination files as immutable Git blob SHA from G18.4.7. For the sole remaining shared file `server/build.gradle`, compose Chat 2's 489-test manifest with the **verbatim** G21.183 core test and dedicated Gradle task, updating count **490 unique**. No code in `LocalJvmAccountSessionLease.java` or `WorldPlayerPersistence.java` is replaced.

## Functional scope

- G18.4 Daily Money Making activation/tracking, exact WorldPlayer-generation admission, optional exact-origin credit API (unwired), G18.4.3 activity-lifetime characterization.
- R5 loadout no-target negative safety, Monster Spawner reconnect/retirement regression.
- G18.4.5 immutable spawn-time recipient generation stamp through canonical settlement.
- G18.4.6 actual admitted canonical lethal attacker versus configured LocalLab loot recipient.
- G18.4.7 NEW expected negative: real installed LocalLab progression observer may credit a replacement same-username generation for an old NPC terminal event after the original generation disconnects; controls absent recipient, immutable spawn witness, and opt-in old-generation rejection.
- G21.180-183 Chat 1's two-World named account regressions, opt-in lease, failed-final lease retention, mid-session property opt-out protection.

## Gating, no inflated claims

Need **new exact-head** GitHub Actions `G1847_NAME_REUSE_TERMINAL_ATTRIBUTION_CHARACTERIZATION_PASS`, G21183 regression, Java11 bytecode major55, focused **490/490**, full Gradle success. Individual G21.183 and G18.4.7 green runs do not certify the combination.

No original SpawnPK server reward/kill-credit policy is established; `UNKNOWN_SERVER_AUTHORITY` remains for historical behavior. The observer remains username-only; no live progression routing change, exploit assertion or fix. Opt-in JVM lease remains default OFF and no durable crash/restart recovery. Existing G18.4.7 test uses synthetic lethal damage but the actual production provisioning callback, not client packet input. R25 release pointer frozen. Chat 1 owns merges, account lease and persistence. Chat 2 owns gameplay; Chat 3 client deobf separate. [#2547](https://github.com/Trexzo/SpawnPK-RE/issues/2547), [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558), [#2540](https://github.com/Trexzo/SpawnPK-RE/issues/2540) remain open.

Do not separately merge older stacked Chat 2 PRs if this cumulative candidate is accepted.
