# Chat 1 / G21.178 — fail-closed SAME-World admission after final-save IO failure

**Parent certified** G21.177 [Draft PR #2542](https://github.com/Trexzo/SpawnPK-RE/pull/2542) exact `dae72ba0408b4e6b0832cdc17f8b6e3d44796951`, hosted [Actions #38464231436](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38464231436) SUCCESS major55, 477/477, full Gradle. Core long-term durability [issue #2540](https://github.com/Trexzo/SpawnPK-RE/issues/2540) remains OPEN; Chat 2 coordination [#2526](https://github.com/Trexzo/SpawnPK-RE/issues/2526). Milestone #2548.

## Source-derived hazard and intentionally narrow fix

G21.177 reproduced an actual TCP `SESSION_END` final-save fault in World B, old bytes preserved, World C **successfully** loaded stale OLD. Same-World admission was also unprotected. Genuine `WorldPlayerPersistence` has one FIFO `ThreadPoolExecutor` for real `SaveTask` and `LoadTask`. G21.178 records a **negative memory-only account set** in `SaveTask.run` when a **finalDisconnect** write's completion is exceptional, before leaving the worker. A rejected final SaveTask also arms the negative set. The subsequent `LoadTask` with `enforceAdmission=true` vetoes that account **before reading any account file** with `G21.178 FINAL_SAVE_WORLD_LIFETIME_VETO_NO_RECOVERY`. Later World-owned writes are blocked for this account while the World lives; the negative set is never cleared by success, reconnection or automatic operator action. Read-only `observeUntrustedMailboxAccount` retains its forensic path. Preexisting strict Mailbox fences are not cleared or replaced; uncooperative direct `FilePlayerRepository.save()` remains outside this protocol.

### Exact real TCP regression

`G21178CoreSocketSameWorldFailedFinalSaveVetoIntegrationTest` commits OLD via real account/session, constructs second World with FilePlayerRepository's existing test-only pre-publication fault callback, logs in and waits for BOOTSTRAP to finish, mutates NEW on the World queue and fails exactly one genuine `SESSION_END` write. Verify old account file bytes unchanged and World negative count one. In **that same World**, a new real TCP session targeting identical named alias is refused before World registration; old account still readable only through forensic `FilePlayerRepository.load`. Then close World and instantiate third World: a real TCP session may still authenticate/hydrate OLD. Thus G21.178 improves **only same-World** admission. It explicitly proves that durable cross-World failure quarantine is **NOT implemented** and the issue #2540 remains.

### Limitations / nonclaims

- This negative set exists only in memory of this World instance. JVM crash/restart or a new World loses it. Does not claim power-loss durability, persistent intent, multi-JVM writer ownership, or absence of races before a final save was queued.
- Only final captured SaveTask failure and rejected SaveTask are scoped here. Abort before capture/queue exhaustion may still need lifecycle coordination.
- It provides no account-recovery API, auto-unquarantine, rollback, replay, cleanup, rollover, pruning, unlink, signer custody, reward grant or Mailbox settlement authority.
- Test uses synthetic revision317 socket, not a graphical v308/v309 original client. Gameplay-owned Chat 2 and client-owned Chat 3 files untouched.

**Acceptance:** Java11 major55, focused 478/478, full hosted Gradle exact SHA, Draft/unmerged. Frozen R25 #1847/G21.87/88 unchanged.
