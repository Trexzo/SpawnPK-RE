# Chat 1 G21.184 — late opt-in leaves already active unleased World owner untracked

## Baseline

- Certified G21.183 [Draft PR #2564](https://github.com/Trexzo/SpawnPK-RE/pull/2564), exact SHA `eb8af4565a30b141e5fdb1db03e78df4c27f00dd`, [Actions #38543490047](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38543490047) SUCCESS Java11 major55, 483/483 focused, full Gradle.
- Cross-Chat integration: [Draft #2566](https://github.com/Trexzo/SpawnPK-RE/pull/2566) 489-target, and Chat 2's newer [Draft #2567](https://github.com/Trexzo/SpawnPK-RE/pull/2567) 490-target are **separate workstreams**. No gameplay/client changes in G21.184.
- Open core design issues: [#2559](https://github.com/Trexzo/SpawnPK-RE/issues/2559) (global session writer ownership) and [#2540](https://github.com/Trexzo/SpawnPK-RE/issues/2540) (generic failed-final-save crash-durable negative admission).

## Why G21.183 does not address this inverse transition

G21.183 fixes one narrow property-transition issue: an already-issued account token is respected after the experimental flag is switched from TRUE to FALSE. But with the feature DEFAULT OFF, an existing World may already have admitted a normal named session **without any token**. Flipping the property FALSE → TRUE does not retroactively register or protect that old session. The next World may obtain a new same-path token because the token map is empty, despite the older session's existing membership and pending final write.

This is not a speculative memory-corruption attack; the two World registries and their account snapshots are independent. G21.180 previously certified that sequential successful saves from concurrent Worlds can overwrite each other. This regression documents the distinct inverse opt-in transition.

## Actual test and expected NEGATIVE witness

`G21184CoreSocketLateEnableUnleasedOwnerTest`: two simultaneously instantiated real isolated Worlds, one ordinary named profile, real localhost `LocalSession` socket fixtures using synthetic revision317/ISAAC.

1. Set `spk.local.experimentalJvmAccountSessionLease=false` BEFORE World A login. A creates a real BOOTSTRAP profile through its worker, applies `A_FINAL` through `World.submit(owner,generation,...)`, and holds **zero** JVM lease tokens.
2. While A remains online, switch property to TRUE. World B logs into the same named account over a second actual TCP socket, independently registers a distinct `WorldPlayer`, and acquires its own new token.
3. Apply `B_FINAL` through B's own World command. Close A first. Assert real account file contains `A_FINAL` after its successful final save while B still owns its independent session/token.
4. Close B. Assert subsequent successful final save writes `B_FINAL` over A's committed postimage. Verify both persistence failure counters remain zero, the B token releases on confirmed final completion and no publication/JVM watch resources leak.

Expected markers include `retroactiveExistingOwnerLease=false`, `dynamicOptInFullySafe=false`, `successfulStaleWriteHazardReproduced=true`, `safetyFixImplemented=false`.

**No production code is modified in G21.184.** It is a characterization of the existing experimental feature; a green build demonstrates the hazard, not a fix. The documented consumer contract remains: enable only prior to admitting any account sessions and leave the flag fixed for the JVM lifetime; even this does **not** solve cross-process/crash/write bypass issues.

## Verification and future design

- Focused manifest **483 → 484**, dedicated Gradle `g21184CoreLateEnableUnleasedOwnerRegression`. Require exact-head hosted Java11 major55, **484/484** unique focused tests and full Gradle.
- Any future resolution of the inverse-toggle gap would need trustworthy admission accounting for already-active sessions without altering default-off compatibility, or a startup-only immutable configuration contract. Neither is implemented or validated here.
- No cross-process writer fencing, adversarial symlink path stabilization, raw/default/legacy writer exclusion, crash-durable final intent, automatic recovery, original graphical client proof, or original SpawnPK reward authority.
- No settlement/grant/replay/cleanup/rollover/pruning/unlink/session release/ACK. Freeze R25 #1847 and G21.87/G21.88. Keep Draft/unmerged, do not silently merge with Chat 2's gameplay branch.
