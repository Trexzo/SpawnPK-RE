# Chat 1 G21.190 — optional ONE-SHOT forced ACTIVE account review before World membership

## Exact green prerequisite

- Core [G21.189 Draft #2589](https://github.com/Trexzo/SpawnPK-RE/pull/2589), SHA `9706ea0d00778ef4c6356f8c43088c3becac7180`, hosted [Actions #38562265506](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38562265506) **SUCCESS**, Java11 major55, 489/489 unique focused, full Gradle 7m32s. The G21.189 DEFAULT-OFF read-only existing-review-marker login veto is verified with true localhost TCP sessions and new JVM after external marker creation.
- Joint [G21.189 + Chat 2 G18.5.1 Draft #2590](https://github.com/Trexzo/SpawnPK-RE/pull/2590), SHA `e2df6ee1e58dfb4049f45a1dbf0a36492bd4d7dd`, [Actions #38562440547](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38562440547) **SUCCESS** with 496/496 focused and full Gradle.
- G21.187's real forced-kill account negative is still valid in DEFAULT-OFF mode: without any durable ACTIVE review marker, OS account lock releases on killed JVM and a fresh JVM may hydrate stale BOOTSTRAP after unsaved World mutation.

## New opt-in behavior and intentionally severe usability limit

New property `-Dspk.local.experimentalOneShotActiveReviewOnAdmission=true` (DEFAULT OFF) **requires all three previous protections enabled before any player admission**:
1. `spk.local.experimentalJvmAccountSessionLease=true`;
2. `spk.local.experimentalCrossJvmAccountSessionLock=true`;
3. `spk.local.experimentalReviewIntentAdmissionVeto=true`.

In `LocalSessionPlayerInitializer.initialize`, snapshot the new flag once before account load. After normal initial hydration/default preparation, existing `MailboxLateSessionAdmission.requireStillAdmissible` and G21.189's second absence check, but **BEFORE `world.registerPlayerAndStart`**, call `LocalOneShotActiveReviewOnAdmission.forceBeforeWorldRegistration`. The exact account file path comes from the existing session-owner token. It uses the G21.188 primitive: exclusive NOFOLLOW CREATE_NEW ACTIVE review leaf, force marker file and parent directory, verify marker. Any IO or existing leaf causes fail-closed initialization; never-admitted token releases without clearing marker.

**Absolutely no automatic marker retirement.** A normally logging-out and successfully saved account retains its ACTIVE marker. A later ordinary opt-in session is intentionally denied until some future separately designed/validated operator procedure exists; this branch provides NONE. This is deliberate fail-closed demonstration, **NOT a deployable multi-session product**. There is no durable final-account-save receipt yet, so healthy `SaveTicket` is not sufficient to clear a marker; auto-clearing it would introduce an unproven crash window.

A user can still opt OUT of the whole experimental feature (DEFAULT OFF); therefore this does **not** establish a universal safety fence or default recovery. The G21.190 experiment is meaningful only when ALL four flags are set before admission and remain fixed throughout. It does not guarantee honest generation/ref ownership against nonparticipating RAW writers, hostile pathname replacement/unlink/hardlink, unsupported directory force, heterogeneous filesystem locking or power loss.

## G21.190 actual TCP owner process-kill test

`G21190CoreOneShotForcedIntentKilledJvmTest` is a derived, separate regression based on G21.187's certified crash-cutpoint fixture:

1. Spawn a genuine LocalSession TCP **holder** child JVM on an isolated named profile, with all four flags on. The initial BOOTSTRAP persistence worker must succeed, holder owns a real WorldPlayer and changes its extension `KILLED_UNSAVED` in World without final save.
2. Parent verifies account BOOTSTRAP bytes and **already forced ACTIVE marker** alongside stable OS lockfile while holder is still alive.
3. A second JVM's competing real TCP session must be refused while holder lock held.
4. Parent `destroyForcibly()` terminates ONLY its own disposable holder before final save. Account bytes remain exactly BOOTSTRAP; marker is byte-identical to pre-kill; OS automatically releases temporary lock.
5. A **fresh independent JVM** attempts actual TCP login to stale BOOTSTRAP with all flags on. **Unlike G21.187, login must be DENIED**, World membership stays empty, account/marker bytes unchanged, no session tokens leaked or post-crash writes made.

Expected positive-but-narrow `G21190_ONE_SHOT_PASS optInOneShotForcedBeforeRegistration=true crashNegativeDenialForMarkedAccount=true markerNeverAutoRetired=true defaultOff=true healthyAccountReusableWithoutManualReview=false generalCrashDurableAccountReceipt=false rawWriterExclusion=false powerLossCutpointsProven=false`.

This is one **user-space abrupt process-kill** cutpoint, not power-failure/fence correctness at all possible ordering points. Force file+directory success in this one test is not universal hardware persistence assurance. Test fixture cleanup deletes only its own disposable files *after* children exit, not an actual account review marker.

## Required verification, future work and frozen boundaries

- One unused-by-default helper `LocalOneShotActiveReviewOnAdmission`, one opt-in call in initializer before World registration, one new real-socket interprocess test, Gradle 489→490 unique focused and this research note. No Chat 2 gameplay change.
- Require exact-head hosted Java11 major55, G21.190 marker, G21.189 and G21.188 markers, **490/490 focused** and full Gradle SUCCESS. Until then this branch is **UNCERTIFIED**.
- Real controlled retirement still needs a versioned account owner epoch and **durable account snapshot temp-file force → atomic replace → parent-directory force → receipt verify**, then durable sidecar retirement with crash cutpoints and operator-controlled conflict handling. Existing `FilePlayerRepository.saveForWorld` does NOT yet have verified file+directory force semantics.
- Keep core durable [#2540](https://github.com/Trexzo/SpawnPK-RE/issues/2540), global account ownership [#2559](https://github.com/Trexzo/SpawnPK-RE/issues/2559), and Chat 2 [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558) and [#2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587) open. No main merge, automatic recovery, settlement/reward/grants/replay/cleanup/rollover/pruning/unlink/session release/ACK or historical graphical client proof. Frozen R25 #1847/G21.87/G21.88; Chat 2 gameplay and Chat 3 client untouched.
