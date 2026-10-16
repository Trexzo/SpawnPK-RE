# G19.5.5/56 — bounded post-settlement observer diagnostics in a real World

**Exact parent:** [Chat2 #2818](https://github.com/Trexzo/SpawnPK-RE/pull/2818) at `3f3cb70f276e8587c1fd3e76af59fbefe63755fb`, 675 focused candidate (G1951–54 already integrated into the same gameplay train). This branch changes **test source only**, not terminal observer production policy.

## Required lifecycle evidence

G1951/52 verify a single real post-settlement observer RETURNED/THREW and that retry cannot duplicate its drop. G1953/54 verify installed G1→G14→G15/G16/G18 progression with a malformed G14 consumer and optional stage isolation. The production `MonsterSpawnerPvmRuntime` diagnostic ring retains only 128 recent calls, plus monotone saturating totals. It is necessary to verify those limits against many distinct **real canonical NPC death identities in one runtime**, not fake `FinalizeResult` objects.

**G1955:** single registered WorldPlayer owning a real MonsterSpawnerService session, spawn/kill/finalize 131 distinct canonical NPCs in one World and one runtime (re-activate one spawn budget between deaths). The first installed terminal observer invocation throws after ground settlement; subsequent 130 return. Assert exactly 131 real drop resolutions and one accumulated same-owner 995 ground stack of 655, 131 callback attempts, **128** retained detail entries, **3** evicted, exactly **1** failure counted even after that first failing detail has been evicted, first retained npcId is fourth chronological NPC, last retained is last NPC, and early read-only detached snapshot is unchanged. A repeat settlement attempt for last NPC cannot recreate any drop or observer.

**G1956:** same actual runtime graph with **no terminal observer installed**, three genuine spawned and killed canonical NPCs and ground drops amount 15, but zero callback attempts, failures and retained detail. No false positive entry can be inferred solely from successful drop settlement.

Focused manifest **675→677 unique**, two JavaExec tests. Requires fresh hosted exact-head **677/677**, G1955/G1956 markers, Java11 major55 and complete Gradle. No production Java classes changed; `main` untouched.

This tests **in-memory bounded diagnostics only**, not durable kill-credit/event ID, persisted consumer replay/ACK, or recovery after process death. Issue [#2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587) remains open. Stage `THREW` can reflect partial consumer mutation: never replay awards based solely on this history.
