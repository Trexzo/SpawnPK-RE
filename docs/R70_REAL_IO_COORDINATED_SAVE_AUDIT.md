# R70 — Real IO worker, coordinated account + ledger + owner commits (offline only)

## Status

**DRAFT, stacked on R69, unmerged, OFF BY DEFAULT.** R69 proved stale queued
snapshots can be refused *before* an unversioned raw repository save. R70 adds
a second, distinct opt-in policy for **successful** real-IO saves that updates
the canonical account, R63 generation ledger, and R64 account owner record
through the pre-existing coordinated `R64.saveOwnedUnderLock` operation.

No `World`, `LocalSession`, production account path, socket or game process
installs either test-only admission hook. This is NOT an authorized production
account-save protocol.

## Execution and lock order

1. `WorldPlayerPersistence.captureAndSave`, `checkpointDue`, and
   `captureDeferredFinalSave` capture snapshots under existing World actor
   ownership checks on the World thread. The immutable snapshots are queued
   for the single-thread IO executor.
2. The actual `WorldPlayerPersistence.write` invokes the previously added
   `OfflineSaveAdmission` extension point. The R70 test instance **never
   calls** the supplied direct `PlayerRepository.save` callback.
3. R70 holds a per-hook Java monitor, then acquires R60's cooperative,
   cross-JVM OS lock. It rechecks R64 active owner/epoch, R63 canonical
   generation + hash and R59 journal/pending-pair state.
4. If the immutable snapshot exactly matches the already committed canonical
   digest, it completes as an idempotent no-op: the owner is still revalidated
   and the R63 generation does NOT advance.
5. Otherwise R70 calls `R64OfflineSessionOwnership.saveOwnedUnderLock`
   while the same R60 lock is held. That method saves the account through the
   actual `FilePlayerRepository`, advances R63, publishes R64 and returns
   the updated token. The hook retains that token for the next IO write.
6. A concurrent cooperative owner takeover can occur only outside that R60
   critical section. Once a different JVM publishes owner epoch 2, both
   unchanged and changed old-worker saves fail BEFORE a canonical write.

The application-level single-IO-thread restriction prevents two writes from
one `WorldPlayerPersistence` worker racing within its own queue. The
per-hook synchronized method additionally serializes unexpected direct test
calls to the same object. No World lifecycle or actor lock is acquired while
R60 is held, so R70 introduces no new World -> R60 blocking path *in
production*. Deadlock/reentrancy proof for any future live use remains open.

## Regression acceptance

`R70OfflineCoordinatedIoCommitTest` uses a clean test-owned `World`, its
real async IO worker, the pinned `R64SessionFenceWorker` in a separate JVM,
and the real versioned file repository under temporary directories.

Positive path:
- ordinary account save advances R63/R64 from generation 1 to 2;
- a byte-identical snapshot is validated but does not advance generation;
- one real autosave checkpoint advances to generation 3;
- a real deferred final logout snapshot advances to generation 4;
- the account SHA-256 digest matches the persisted ledger and R64 token
  after every committed write;
- after a separate JVM transfers owner epoch 1 to epoch 2, identical and
  changed saves by the old worker both fail without modifying any of the
  three canonical files; the legitimate successor can advance generation;
- the fixture injects a `PlayerRepository` writer that always fails, proving
  successful R70 saves travel through the coordinated R64 repository route,
  not through `repository.save(snapshot)` outside the R63/R64 protocol.

Failure path, in a completely separate disposable fixture:
- R64 fault injection throws after canonical account + R63 ledger publication,
  but before R64 owner-record publication;
- the real IO future completes exceptionally; `R64.inspect` reports
  `BLOCK_INDETERMINATE` and never authorizes login;
- a subsequent old-hook write fails closed and cannot change the quarantined
  account, ledger or owner files.

Linux and Windows workflows also run the inherited R69, R68, R67, R66, R65,
R64 and R63 regression chain plus Java 11 bytecode verification. CI result
must be checked against the exact pull-request head before any further claim.

## Deliberate limits

- **Not a physically atomic multi-file transaction.** A crash between
  account/ledger/owner publications can leave partial state. R63/R64 detect
  mismatch and refuse further action; automatic recovery and safe journal
  retirement remain unimplemented.
- The off-by-default test hook requires a previously issued R64 token.
  R64's offline UUID/epoch is *not* authenticated login or permission for a
  real player. No production account credentials, takeover protocol or
  session binding are introduced.
- A snapshot arriving later in the IO queue may have been captured earlier
  in World time. R70 maintains strictly monotonic R63 commit generations,
  not necessarily monotonic **gameplay snapshot freshness**. Source-order
  watermarking must be designed/tested before production integration.
- An accepted R70 write intentionally bypasses the supplied raw repository
  delegate and publishes using R64's canonical root. The test ensures that
  root points to an isolated disposable file repository. Do not attach it
  to a live repository adapter without a separate path/ownership audit.
- Only cooperative R60 participants obey the cross-process lock. Legacy
  `FilePlayerRepository.save` call sites and trade callbacks remain
  unaudited for a real rollout.
- `WorldPlayerPersistence` backpressure/shutdown behavior is unchanged.
  Forced shutdown, interruption midway through an OS-level write, filesystem
  durability and real account recovery still require separate tests.

## Proposed R71

Audit snapshot ordering and final-save barriers across checkpoint coalescing,
explicit save queue admission, World teardown and the R60 lock. Verify
out-of-order arrival with a deterministic hold/release test and fail closed
without overwriting a newer commit. Do not integrate into live session code
until credential/session authority and multi-file recovery are independently
designed.

**R70 DRAFT / STACKED ON R69 / NO LIVE INTEGRATION.**
