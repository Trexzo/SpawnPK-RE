# G2082–86: observer continuity through optional session cleanup failure after ground settlement

Exact parent source: Chat2 PR #2958 `f496482f7587797a86328012e508642fb8e2328a` hosted **896/896** Linux/full Java11 Gradle and Windows source-only negative release gate success. This PR changes only `MonsterSpawnerPvmRuntime` after irreversible ground settlement, an existing canonical NPC test bridge, five new test classes, Gradle and handoff. No original client, Chat1 account persistence, World, server credit policy, drop resolution, settlement implementation or consumer grant code changed.

## Concrete source-backed gap
In `MonsterSpawnerPvmRuntime.settlePending`, `settlement.settle(...)` ran before `entries.remove` and then the optional `world.runIfOpen(... combat.retireSessionIfCurrentAndNoTrackedNpcs(...))` block executed **before** construction of `FinalizeResult` and `TerminalObserver.onFinalized`. If optional empty-spawner session retirement unexpectedly raised RuntimeException/Error, the canonical NPC and drop receipt were already irreversibly settled, but the terminal observer could be SKIPPED and `finalizeIfOwned/retrySettlement` couldn't recover the retired entry. This is a source-level failure boundary, not a demonstrated installed cleanup exception before this regression.

## Fix
Catch failure of the OPTIONAL post-settlement session retirement after terminal ownership is removed, log only `failure.getClass().getName()` plus canonical npc/deathTick (never Throwable.toString(), getMessage() or cause), then continue constructing the original FINALIZED result and invoke the observer once. This preserves the existing 896 observer failure type-only log and bounded observer outcome diagnostic ring. An error in optional cleanup is not proof any player progression was applied, and NEVER triggers replay. A package-private test-only per-runtime fault injection hook defaults null, runs only in the owner-absent optional retirement branch, and cannot authorize rewards. Both cleanup and observer failure can be independently logged without revealing synthetic secrets. Runtime monitor never held across external observer.

## Five genuine canonical runtime regressions
- G2082: after real lethal NPC and ground receipt, optional empty-session retirement throws IllegalStateException; FINALIZED and observer still invoked; no second drop/observer on retry.
- G2083: cleanup throws hostile AssertionError overriding `toString()` to throw; new log invokes only the type and the observer still finalizes.
- G2084: healthy empty-session retirement with no fault continues unchanged, no spurious failure log.
- G2085: cleanup exception then terminal observer itself throws; both reasons separately type logged, observer ring THREW, one canonical drop, no stage retry.
- G2086: owner player still present; optional owner-absent retirement not selected and injected failure hook is never called; normal terminal observer success.

Manifest **896→901 unique**; five dedicated JavaExec tasks and critical-retention family. Exact-head CI required 901/901, all G2082–86, preserved G2077–81 and account G21374–76, Java11 major55/full Gradle, Windows source-only negative release gates. Draft/unmerged; `main` unchanged.

## Nonclaims
Failure swallowing is restricted to a post-commit optional housekeeping boundary; it does NOT change actual original SpawnPK kill attribution, the ground settlement service, per-consumer idempotence, durable account final-save ACK, owner epoch, or authorize any automatic NPC reward replay. Issue #2587 remains open; proper durable recipient authority #2558 separate. The test hook exists only for fault injection and is disabled by default.
