# Chat 1 G21.187 — killed account-owner JVM permits stale BOOTSTRAP reentry (NEGATIVE)

## Certified base and scope

- Exact parent core **G21.186** [Draft PR #2576](https://github.com/Trexzo/SpawnPK-RE/pull/2576) SHA `57932aff0d6efe9082e4a2875e592034b65bbfe1`, hosted [Actions #38553387890](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38553387890) SUCCESS: Java11 major55, **486/486** focused, full Gradle 8m01s. Its positive evidence proves *cooperating* JVMs, using actual synthetic revision317/ISAAC185 LocalSession TCP logins, deny overlapping account access while a session holds the experimental OS lock, then accept and hydrate the exact confirmed final save after healthy teardown.
- Canonical **joint** Chat 1+2 [Draft #2578](https://github.com/Trexzo/SpawnPK-RE/pull/2578), exact SHA `78cda6d8dfb5cb6553b8d3a04e3745ae833f2ed0`, [Actions #38553606089](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38553606089) SUCCESS Java11 major55 **493/493** and full Gradle 7m57s. Chat 2 gameplay G18.4.7/G18.4.8 is a separate *negative source observation*, not recovered original SpawnPK reward policy.
- G21.187 adds **one test class, one Gradle manifest/task entry and this research note**. No production code or default runtime behavior changes. Both optional JVM+cross-JVM session-lock properties are turned on **only inside the disposable test process**.

## Crash-cutpoint test

`G21187CoreKilledJvmStaleBootstrapReentryTest` launches actual Java child processes and localhost TCP LocalSession connections, sharing ONE temporary, isolated, first-time ordinary named account file path.

1. External **holder JVM** accepts genuine synthetic revision317/ISAAC LocalSession login under both experimental flags and cooperative OS `FileChannel.tryLock`; it **waits for real BOOTSTRAP persistence worker success** and verifies that file exists with no `extension.core_killed_session.phase` field.
2. The holder performs a real `World.submit(owner,generation,...)` snapshot extension mutation `KILLED_UNSAVED` in its own registered World, then signals `holder.ready`. It keeps the actual TCP socket/session open. Test World pulses every 1 s to allow normal server lifecycle, without making the last unsaved mutation a confirmed final account save.
3. Parent checks a stable sibling `.session.lck` file and unchanged BOOTSTRAP snapshot bytes. A **second process** attempts its own real synthetic TCP login while the holder is alive and must be refused (the G21.186 healthy cooperation guarantee).
4. Parent calls `Process.destroyForcibly()` on **only that disposable holder subprocess**. Wait for nonzero exit. Crucially, this bypasses both final `SaveTicket` and graceful session cleanup; the OS necessarily releases the process file lock when it dies, but **no durable account-negative intent was ever published**. Verify the original account BOOTSTRAP bytes are byte-identical (no final KILLED_UNSAVED).
5. A fresh **third JVM** starts a new World, creates a genuine LocalSession and successfully authenticates same account using both experimental flags, hydrates the older BOOTSTRAP **without** `KILLED_UNSAVED`, performs its own new `AFTER_KILL_RELOGIN` World mutation and completes a new healthy final save.
6. Parent reads `AFTER_KILL_RELOGIN` as the new on-disk postimage. Check stable lockfile is never unlinked, `src` alias never created, no long-lived test runner tokens or unrelated publication watch leaks. All process operations are bounded and never target running user applications/accounts.

Expected honest marker `G21187_KILLED_JVM_PASS negativeStaleBootstrapReentryProven=true crashDurableAccountNegative=false safeAutomaticRecovery=false crossProcessLockIsDurableIntent=false rawWriterExclusion=false originalGraphicalClientProven=false ...`. A PASS indicates an observable **safety gap**, not that the stale account is correct. This is a test of abrupt user-space process termination after a specific confirmed bootstrap capture, not an actual sudden power failure or process kill at every filesystem cutpoint. It must not be generalized to whole-game crash consistency.

## Remaining solution work

OS file locks are **temporary process-lifetime exclusion**; they cannot certify stale snapshots after process death. Real recovery requires an explicit durable per-account intent/epoch protocol, fail-closed admission before hydration, verified write-ordering and negative marker crash cutpoints, cross-process ownership cooperation with RAW/legacy writers, bounded NOFOLLOW parent and file checks, and a documented manual account conflict-resolution path. It cannot auto-replay or grant rewards from recovered bytes.

Open core [#2540](https://github.com/Trexzo/SpawnPK-RE/issues/2540) durable failed-final-save safety and [#2559](https://github.com/Trexzo/SpawnPK-RE/issues/2559) global account writer model. Frozen R25 #1847, G21.87/G21.88; no settlement, grant, replay, cleanup, rollover, pruning, unlink, account release or ACK authorization. Chat 2 gameplay and Chat 3 SpawnPK-Client untouched. Draft/unmerged, feature flags DEFAULT OFF.

## Exact-hosted certification required

Core focused count **486→487**. Before any certification, run hosted GitHub Actions on the exact new head: Java11 bytecode major55, `G21187_KILLED_JVM_PASS`, **487/487** focused and complete Gradle SUCCESS. Separate previous G21.186 and joint green runs do NOT certify this new test.
