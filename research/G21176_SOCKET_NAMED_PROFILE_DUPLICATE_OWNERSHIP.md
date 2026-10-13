# Chat 1 / G21.176 — real named-profile concurrent TCP ownership and disk continuity

**Certified parent:** G21.175 [Draft PR #2535](https://github.com/Trexzo/SpawnPK-RE/pull/2535), exact `2fe77f187684cb1f4c5b1f255728be733e6b403a`, [Actions #38457688180](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38457688180), Java11 major55 focused 475/475 and full Gradle SUCCESS. Standing Chat 2 handoff [issue #2526](https://github.com/Trexzo/SpawnPK-RE/issues/2526); G21.176 issue #2537.

## Source-backed admission semantics

`LocalAccountProfiles.chooseForLogin` has explicit historic LocalLab convenience fallback: blank/`localtest`/`opensrc` choose `opensrc` if vacant, then `src` if primary occupied. This is not a global one-alias-one-account rule. **Do not break it.** For an *arbitrary named account*, it returns its canonical name with no fallback. `PlayerRegistry.register` rejects duplicate canonical `byName` ownership and `World.registerPlayerAndStart` enforces that registration; denied `LocalSession` never owns a valid registered session to perform final save.

## Exact live socket regression (no production policy change)

`G21176CoreSocketNamedProfileDuplicateOwnershipIntegrationTest` establishes a real TCP `LocalSession` for arbitrary `g21176-owned` account (hashed `accounts/profiles/profile-SHA256.properties` under test-only temp root), makes World-owned `extension.core_ownership.phase=OWNER_A` mutation. A second real TCP session **at the same time** targets identical named alias; expected to refuse, not overwrite owner, create extra World membership or mutate first state. Original TCP remains connected and does another World-owned `OWNER_A_LATE` update. Disconnect/final-save must persist **exact** later value. Close original World; a fresh World authenticates same generic named account with real socket and hydrates exact value from disk. Second logout/save retains it. No spurious legacy `src` creation or JVM leases.

Test uses synthetic exact revision317 login and ISAAC widget185 via existing G21.173 harness; **not original v308/v309 GUI test**. This adds a third distinct core integration dimension after G21.173/174/175 sequential `opensrc` success/error. No production `PlayerRegistry`, `LocalSession`, persistence or gameplay changes.

Acceptance: dedicated Gradle regression, Java11 major55, focused **476/476**, exact-head hosted full Gradle. Draft/unmerged; Chat 2 handoff #2526 updated after cert.

Non-claims: no exclusion of arbitrary raw/default/legacy/future uncooperative writers, no signed historical provenance/custody, atomic multi-file crash/power-loss epoch, historic settlement, grant, replay, cleanup, rollover, pruning, unlink, release/ACK authority. Frozen R25 #1847/G21.87/G21.88 untouched.
