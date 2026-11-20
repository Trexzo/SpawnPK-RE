# R88 — real R64 account-save process kill and R71 capture-order fence

**DRAFT / STACKED ON R87 #3132 / OFFLINE ONLY / NOT DEPLOYED**

## Goal and production boundary

R87 proved that a **synthetic single-file** takeover fixture fails closed
across actual child-JVM death. R88 moves testing to the **existing R60/R63/R64
offline coordinated account-save paths**, while preserving their deliberate
non-production status.

The three real files under a temporary test-owned account root are:

1. `accounts/opensrc.properties` — canonical
   `FilePlayerRepository`/`PlayerSnapshotCodec` account state;
2. `generations/opensrc.r63-generation.properties` — R63 generation
   and digest ledger;
3. `sessions/opensrc.r64-session.properties` — R64 owner/epoch/phase
   bound to the ledger generation and digest.

These files share R60's **single real OS FileLock**, but publication of all
three files is **NOT one atomic transaction**. An interruption between R63
and R64 leaves a detectable mismatch that MUST be quarantined.

No live `LocalSession`, login, `PlayerCombatResolutionService`, account,
PvP actor, game socket or production `World` is modified or started.

## R88 child-JVM cutpoints

A narrowly scoped `ThreadLocal<OfflineSaveCrashCutpoint>` is added to
`R64OfflineSessionOwnership`, an already offline-only class. Only a
disposable child JVM installs it; no other code installs these callbacks.
The hooks fire during `saveOwnedUnderLock` while the actual R60
cooperative file lock is held.

| Actual kill point | Account | R63 ledger | R64 owner record | Required recovery behavior |
|---|---|---|---|---|
| `BEFORE_R63_SAVE` | Unchanged | Generation 1 | Generation 1 | Existing token still matches; only explicit test-authorized World save may progress |
| `AFTER_R63_BEFORE_OWNER` | New committed snapshot | Generation 2 | Still generation 1 | R64 passive review returns `BLOCK_INDETERMINATE`; stale writes/takeover and both R71 captures refused; preserve all canonical evidence |
| `AFTER_OWNER_BEFORE_ACK` | New committed snapshot | Generation 2 | Generation 2 | Passive review is consistent but **not login authority**; old R71 gate and old R64 owner token are rejected; explicit test-only new World can save with deliberately supplied revalidated token |

The parent JVM creates a brand-new R60 journal and an R63/R64 owner
based on an actual `PlayerSnapshotCodec.capture` account containing a
bank item. A second Java process loads this actual offline account,
changes the bank via `WorldPlayer.bank().spawnItem`, and calls the
**real R64.saveOwned** path. At the selected cutpoint it writes an
out-of-band ready marker and remains paused **inside R60's OS lock**.

The parent verifies that another R60 lock acquisition is refused,
then invokes `Process.destroyForcibly()` and confirms process exit.
A bounded, **read-only** lock-release check accommodates Windows
handle teardown; it does NOT retry a save or claim new ownership.

## Actual R71 World-thread snapshots before the child dies

The parent separately creates an isolated `World.isolatedForTest`,
registers a synthetic `WorldPlayer` loaded from the pre-crash account,
and installs `R71OfflineOrderedPersistenceAdmission` **only in that
isolated test**. It captures two immutable deferred account snapshots
with capture ordinals 1 and 2 on the World actor, *without* submitting
them before the child JVM's save.

**For the pre-R63 kill**, the original R64 owner token remains valid.
Submitting the newer captured ordinal 2 succeeds via the actual
`WorldPlayerPersistence` IO worker and coordinated R60/R63/R64 save.
Submitting the older ordinal 1 later fails `R71_SAVE_DENIED stale
capture ordinal`. Canonical account, ledger and owner bytes are
unchanged by the refused older submission.

**For either post-R63 kill**, both captures are rejected before
writing with `R64_SESSION_DENIED stale generation/hash/index revision`,
irrespective of their numeric capture ordinals. R71's in-memory
high-water mark stays at zero. The canonical account, ledger and
owner bytes must be exact matches before and after both refused
callbacks. The raw `PlayerRepository.save` bypass is configured
to throw if reached.

The post-owner-ack fixture additionally performs an **explicit,
offline-only** revalidation of the now-consistent R64 owner. Its
passive `Review` remains non-authorizing (`loginAuthorized=false`).
A fresh isolated World is deliberately supplied that reviewed token,
loads the already committed account snapshot, changes it, and saves
through a new test R71 gate, proving normal owner-consistent save
paths remain usable **when explicitly configured in a test**.

This step is not automatic login, account repair, owner credential
recovery, durable event replay, or production safety proof.

## Durable ordering is still missing

The R71 accepted capture ordinal is **only in memory**, local to an
offline gate. R88 deliberately does NOT claim an old detached
World capture can be distinguished after restarting the same epoch
with a new memory-only watermark. R64's owner+generation mismatch
protects the tested cross-JVM stale snapshot scenarios, but an
actual durable, authenticated capture high-water mark remains a
future requirement for more general recovery.

R60 locks serialize cooperating processes only. The R63 account,
R63 ledger and R64 owner files are **not** one atomic multi-file
commit; the middle kill point proves the quarantine is required.
There is no power-loss test, directory fsync verification, automatic
stage deletion, corruption repair, live gameplay test, or production
exactly-once reward guarantee.

## Tests and expected verification

- `R88OfflineR64R71KilledSaveTest`: actual second JVM forcible death
  at all three R64 save cutpoints; real account+R63 ledger, real
  R60 lock and fail-closed cross-process read admission.
- Actual `World` actor capture ordinals and deferred
  `WorldPlayerPersistence` worker submission before/after
  killed-JVM R64 save.
- Exact canonical three-file byte comparisons on every denied
  operation, owner review phase checks, and test-only explicit
  revalidation of a fully published successor snapshot.
- Existing R87/R86/R85/R84/R83/R81/R80/R79–R63 regression
  tests, Linux and Windows Java 11 bytecode compatibility, and
  independent full Linux-server and Windows atomic-persistence
  GitHub workflows.

**R88 DRAFT / UNMERGED / NOT DEPLOYED / NO REAL ACCOUNT CHANGES.**
