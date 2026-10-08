# G21.35 — last-chance Mailbox review-fence and snapshot recheck before World registration

**Certified parent:** G21.34 exact `782aa2add46482f92b1f9f64bbe32d852d5091a8`, hosted [workflow 37827886992](https://github.com/Trexzo/SpawnPK-RE/actions/runs/37827886992), 333 focused Java11, BUILD SUCCESSFUL. Frozen R25 promotion #1847 untouched.

## Production gap

The actual `LocalSessionPlayerInitializer.initialize` performs `LocalAccountLifecycle.load` through the G21.31/G21.32 guarded `WorldPlayerPersistence.load`, then executes starter, pet, appearance, default-loadout and other player initialization before finally calling `world.registerPlayerAndStart`. The existing G21.32 review marker writer is opt-in and can be invoked by an independent thread; even after G21.34 no-clobber exclusive publishing, it does **not** hold `world.loginInitializationLock`. An account that was clear during the initial load can become review-fenced **before its live World registration**.

## Narrow G21.35 repair

`LocalAccountLifecycle.LoadResult` now retains the **G21.30 canonical SHA-256 of the exact normalized snapshot initially applied** to the detached session's WorldPlayer. The existing two-argument legacy constructor remains source-compatible and stores null; the actual `WorldPlayerPersistence` account-source overload sets the digest on successful load.

Immediately before `world.registerPlayerAndStart`, production `LocalSessionPlayerInitializer` runs `MailboxLateSessionAdmission.requireStillAdmissible(account,accountLoad,persistence)`. It performs a **second normal `WorldPlayerPersistence.load`**, not the untrusted forensic read, reusing the single existing FIFO worker with the unchanged **G21.31 journal** and **G21.32 durable sidecar** admission checks. An account now fenced (even if account file is gone or journal has been removed) throws a session-rejecting exception **before World membership**. For initially loaded accounts, latest normalized complete-account SHA must equal the exact original capture; deletion, replacement, identity changes or corrupted snapshots fail closed. For originally missing accounts, unexpected file creation fails closed; an unchanged missing account and an unchanged ordinary profile remain admissible.

Test-only package-private constructor injection `Runnable beforeRegistration` defaults to **no-op in production**. It is called before the last-check boundary only to create deterministic race fixtures. It is not a new handler/permission, not a way to bypass the check and never sends success packets.

## Acceptance and residual limitation

`G2135MailboxLateSessionAdmissionIntegrationTest` pauses the **actual** initializer after first hydration and before the final check on a test thread; a separate thread completes G21.34 marker publication on the same canonical account file, then resumes initialization. The initializer must reject, account must not appear in `world.players()`, no reward credit or Mailbox CLAIMED state may be introduced and the negative marker must remain on disk. Other checks: clean profile registers normally; a fence on another account is isolated; valid but altered, removed or newly appearing account snapshots are rejected; stable no-file accounts still register. The test does not instantiate an actual TCP session and therefore does **not** claim a captured login-success packet assertion; it tests the code path invoked before packet creation.

Focused Java11 manifest **333→334**, new task `g2135MailboxLateSessionAdmissionRegression`. Exact-head hosted CI must show all checks and full build SUCCESS before certification.

**Residual race clearly not fixed:** A truly independent fence writer can publish in the interval **after the second check but before registration or while already logged in**. This extra guard greatly narrows the measured initial-to-registration window, but is NOT an atomic cross-process lock or a runtime revocation system. A future coherent shared admission/marker publication primitive and active-session fence discovery would be needed for a stronger guarantee. It remains **no item grant, no automatic replay/rollback, no fence release and no original SpawnPK policy claim**. Native C2S185 widget32181 stays preview-only NO_GRANT; bank widget32178 excluded.
