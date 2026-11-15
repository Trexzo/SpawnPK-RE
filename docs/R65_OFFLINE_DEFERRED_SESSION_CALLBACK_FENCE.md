# R65 — Offline deferred autosave and logout callback fencing

## Problem

R64 introduced cooperative account session ownership epochs, R63 generation/hash fencing, and R60 cross-process OS locking. In real applications, an asynchronous save callback may be **captured** while session A is still current, remain queued for a period, and execute after session B has acquired the same account. A safe persistence dispatcher must validate the captured owner's authority **at execution**, not merely when the save was queued.

**R65 is an offline deterministic test boundary.** It is NOT connected to LocalSession, WorldPlayerPersistence, a production game socket, TradeService or real account folders.

## Implementation

`R65DeferredSessionSaveDispatch` models two callback types: `AUTOSAVE` and `LOGOUT_FINAL_SAVE`.

- `capture(reason, ownerToken, proposedSnapshot)` freezes a test-owned R64 session-owner token (owner UUID, epoch, active/released phase, R63 account generation, canonical hash and R59 journal revision) and a versioned immutable post-mutation snapshot. Capture does **not** read any account file or authorize a future save. It refuses an inactive owner, foreign snapshot or unchanged snapshot.
- `dispatch(journal, accounts, generations, sessions, callback)` acquires R60's **real exclusive OS FileLock** and calls `R64OfflineSessionOwnership.saveOwnedUnderLock`, which compares the captured token with the currently persisted owner record, checks owner identity/epoch/phase, verifies the current R63 account generation and SHA-256 snapshot, checks the journal revision and ensures there is no indexed unresolved trade. Only after all these checks does it publish the canonical account, R63 generation and updated R64 owner record.
- For `LOGOUT_FINAL_SAVE`, the dispatcher then calls `releaseOwnerUnderLock` **before releasing the same OS lock**. A cooperating new session cannot take over between the final save and the RELEASED record.
- R64's existing public `saveOwned` and `releaseOwner` now delegate to those same protected under-lock implementations. The under-lock methods reject calls without R60's same-thread held context, rather than relying on a comment or a nested tryLock. All preexisting R64 tests must remain green.
- Re-dispatching an old callback after a successful save fails due to the changed account generation/digest, and cannot double-apply the captured state.

A returned outcome **never** indicates live login authorization or automatic recovery. The callback UUID is a diagnostic identifier, not an authentication secret, delivery ledger or exactly-once queue acknowledgment.

## Connected Windows acceptance — November 15, 2026

- Gradle `jar testClasses verifyJava11Bytecode --offline --no-daemon`: **BUILD SUCCESSFUL**, 2,242 classes, Java11 major 55.
- `R65_DEFERRED_CALLBACK_FENCE_PASS`: two separate executor threads capture autosave and final-logout callbacks while session A is current, then wait at deterministic latches. A **second, independent test-owned Java process** explicitly takes over the account and publishes session B (different UUID, epoch 2) via R64 while the queued callbacks remain paused.
- The parent releases each callback **after takeover**, one at a time, so both reach the actual stale-owner gate rather than short-circuiting on OS-lock contention. **Both fail as stale owner**, and byte-for-byte comparisons confirm none of the canonical account, R63 ledger or R64 owner record changed.
- Session B successfully publishes a new autosave afterward; re-executing that exact callback fails without advancing generation. Session B's final-save+logout callback publishes RELEASED under one OS lock. Old callbacks remain unusable; explicitly replacing the released owner advances to epoch 3.
- Another real test-only JVM holds the OS lock while a current callback tries to dispatch: the callback refuses and preserves canonical bytes. Once released, the same current callback can succeed.
- A R60 PREPARED trade registered after callback capture blocks that callback rather than overwriting a participant's account.
- Inherited R64, R63, R62, R61, R60 and R17 crash/lease regression suites all **PASS** on the local disposable Windows worktree. The dedicated independent Linux/Windows CI includes the R55–R65 regression chain and ordinary trade flow.

## Safety limitations

1. **No live integration.** R65's capture and dispatch are test-only functions. Production login, actual async save callbacks, disconnect handling and game mutations continue to use their existing paths. The R56 two-account crash risk remains until a complete production persistence protocol is independently tested and explicitly enabled.
2. **No authenticated owner.** The R64 token is a controlled test identity, not a game player's credential. The actual server must bind authenticated login, world actor identity, dispatch queue, mutation generation and all persistence paths to one trusted authority.
3. **Not physically crash-atomic.** A final-save/logout sequence publishes several individually atomic files. A process failure after updating the R64 owner to ACTIVE with a new account generation but before publishing RELEASED can leave logout unconfirmed; no automatic login/recovery inference is allowed. Real hardware power-loss and parent-directory fsync behavior are unproven.
4. **Cooperative callers only.** Direct R63 saves, real WorldPlayerPersistence and non-cooperative writers still bypass R64/R65. The R60 lock is not proof of a system-wide filesystem trust boundary.
5. **No durable queue semantics.** R65's callback payload is in-memory, not a persistent write-ahead queue; callback UUIDs are not an exactly-once commit protocol. Repetition is rejected by stale generations for **this** offline fixture, not guaranteed for all application dispatchers.
6. **No safe quarantine release.** R57/R59 unresolved trade participants remain blocked, even after R61/R62 verification. R65 cannot retire intent records, promote ambiguous stage files, or authorize a login.

## Next safe milestone

R66 should test a **single-session admission/dispatch boundary** built on an authoritative server-side session generation. Simulate delayed gameplay mutations and save callbacks across a reconnect, and prove stale queued game events cannot change the in-memory account before the persistence fence is reached. Include runtime work-queue cancellation behavior, not just disk-write denial. The first integration should remain opt-in and test-only until normal LocalLab session protocol and all real save paths have been audited.

**R65: DRAFT, STACKED ON R64, UNMERGED.**
