# R51 — fail-closed account recovery when atomic staging is ambiguous

## Scope and why it matters

The LocalLab file-backed account repository **already** uses
`AtomicAccountPublisher`: it encodes a complete snapshot, writes a uniquely
named adjacent `.<account>.r16-*.stage`, calls
`FileChannel.force(true)`, and requires an atomic replacement of the
canonical `opensrc.properties` / `src.properties` profile. It does not
claim that an individual account snapshot is a durable, cross-resource
Rune/NPC/loot transaction, and does not force a parent directory.

A JVM/process/OS failure **before** the atomic replacement can leave an
unpublished stage next to an older canonical snapshot. Loading that old
canonical (or creating new default account state when it is missing) is an
**ambiguous recovery decision**: the file contents alone do not establish
which already-accepted gameplay actions have completed.

R51 makes a deliberately conservative improvement: **do not automatically
resume or overwrite an account while an adjacent R16 account stage exists.**

This is *recovery quarantine*, **not** automatic staged snapshot promotion,
journal replay, durable commit recognition, crash-atomic gameplay or stale
file cleanup.

## Production code (no new database/threads)

- `AtomicAccountPublisher.requireNoAmbiguousStage(target)` enumerates
  **only adjacent directory-entry NAMES**. It matches the exact target
  filename's `.<filename>.r16-<nonempty unique suffix>.stage` pattern.
  It does **not** open staged content, compare timestamps, acquire a file
  lock, decide whether another publisher is alive or delete any files.
- The method validates the parent path with the existing no-symlink-ancestor
  policy and refuses an unexpected non-directory parent or listing I/O error
  rather than guessing.
- `FilePlayerRepository.load()` calls this guard **before** reading
  canonical attributes and, importantly, before treating a missing canonical
  account as eligible for new-account defaults. Its existing strict
  `LocalAccountLifecycle.loadStrict()` path emits
  `R34_ACCOUNT_LOAD_REJECTED action=NO_WORLD_NO_SAVE` without registering
  the player in World or saving new defaults.
- `AtomicAccountPublisher.publish()` calls the same guard before writing
  a new stage or replacing the canonical. It therefore protects all
  file-backed snapshot publication through this shared writer, including
  legacy compatibility save callers.
- A staging entry for `opensrc.properties` never blocks the independent
  `src.properties` profile. Invalid filename lookalikes do not match.
- An actively open stage is **also ambiguous**. Presence, age and a
  lock-path name do not prove abandonment. R51 deliberately treats it the
  same way and reports a refusal, rather than touching a concurrent writer.

Error class: `IOException`, with a stable diagnostic prefix
`R51_AMBIGUOUS_ACCOUNT_STAGING_DENIED` and action
`NO_LOAD_NO_SAVE_NO_CLEANUP`. No existing profile contents are printed.

## Independent failure and restart acceptance

`R51AmbiguousAccountStageQuarantineTest.java` uses only unique temporary
directories to prove the following:

1. A canonical file saved by `FilePlayerRepository` remains byte-for-byte
   unchanged when a synthetic adjacent stage exists; both `load` and
   `save` deny with the exact R51 reason. The stage remains untouched.
2. If a canonical file is **missing** but a stage exists, neither defaults
   nor a new canonical write is admitted.
3. `opensrc` and `src` stage checks are isolated; neighboring
   malformed filename lookalikes cannot accidentally quarantine a profile.
4. During `AtomicAccountPublisher.publish`'s **actual replacement callback**
   while a forced staging file is present, another repository `load/save`
   is refused; after a successful atomic move, normal load resumes.

`synthetic_v308_ambiguous_account_stage_recovery.py` exercises **three
independent full Java11 Main JVM processes** and synthetic
original-v308-shaped authenticated TCP:

1. **Main JVM #1:** real `localtest` login, HOME bootstrap, bank and
   inventory presentation, orderly logout and canonical account save:
   bank balance **100000**.
2. A **separate, disposable Java fault-injector child process** calls the
   genuine `AtomicAccountPublisher.publish` with an alternate, uncommitted
   bank balance 100099. The publisher writes a real uniquely named adjacent
   `.<account>.r16-*.stage` and `FileChannel.force(true)` completes.
   **Inside the replacement callback and before atomic move**, the child
   deliberately executes `Runtime.halt(71)`. Its exit code and exact
   pre-replace marker are verified. This is a true child-JVM abrupt-exit
   test, but **not** a full game-server crash, OS crash or power-loss test.
   The injector refuses any target outside its strict private disposable
   `spk-r51-ambiguous-stage-*/only-test-account/opensrc.properties` path
   under Java's system temporary directory.
3. The harness verifies the canonical SHA256 is unchanged, discovers
   exactly one actual forced-but-unpublished stage by **filename only**,
   and separately checks that this test-owned stage contains the intended
   uncommitted alternative snapshot. Production code still never reads it.
4. **Main JVM #2:** genuine C2S widget912 initial login requests the
   same profile; strict load refuses before World registration or account
   default save. Exact SHA256 of both canonical and staged evidence remains
   unchanged.
5. The harness explicitly verifies those bytes, then removes **only the
   stage generated by its own immediately preceding private child**. No
   generic `*.stage` cleanup or operator-account mutation is performed.
6. **Main JVM #3:** normal original-v308-shaped socket login reloads
   the verified preexisting canonical profile, including bank **100000**,
   and completes an orderly account save and shutdown.

Expected result:
`R51_REAL_JVM_AMBIGUOUS_STAGE_QUARANTINE_AND_REVIEWED_RESUME_PASS`.

Initial Windows execution on 2026-11-14:
**2,173 Java11 major55 class files**, Gradle
`jar testClasses verifyJava11Bytecode` **BUILD SUCCESSFUL**;
the Java in-memory/filesystem faults and the real three-Main-JVM test
**PASS**. No proprietary original GUI was launched.

CI invokes both test programs on Linux and Windows, followed by inherited
R50/R49 fault-injection, R48/R47/R46/R45 live-JVM synthetic gameplay
journeys and all other existing workflow checks.

## Reconciled inherited R17 abrupt-owner recovery contract

The inherited `AccountCrashLeaseRecoveryTest` (R17) already creates a real
forced-but-unpublished R16 stage, kills only its dedicated child JVM, and
checks that a second independent JVM can reacquire the shared OS account
directory lease. Its **former** assertion then unconditionally saved a
new primary account while that exact orphan remained. R51 intentionally
rejects this as an ambiguous overwrite.

R51 updates **the test's expected recovery policy**, not the production
quarantine:

- After killing the exact test-owned publisher, the released directory
  lease is reacquired normally and the old primary/secondary bytes survive.
- A second fresh JVM **must receive**
  `R51_AMBIGUOUS_ACCOUNT_STAGING_DENIED` when trying to overwrite the
  primary with the unresolved stage still present; the original primary
  and its forced staged bytes remain unchanged.
- This same JVM can still publish the unaffected `src.properties`
  secondary account (new balance 8), proving exact-account isolation.
- The harness checks it owns exactly one temporary staging entry under
  its own `spk-r17-crash-restart-*` directory, explicitly removes
  **only that test-owned entry** after inspection, then starts a third
  independent JVM that saves primary balance 100003 and secondary 8.
- The existing R17 success marker remains, and now additionally proves
  `ambiguousPrimaryDeniedBeforeReview=true`,
  `secondaryWroteDespitePrimaryQuarantine=true` and
  `reviewedPrimaryRestartSucceeded=true`.

This resolves a genuine incompatibility between the old R17 expectation
and R51's conservative recovery policy. No real account stages are cleaned
automatically. The separate R19 strict port-free check still runs before
the newly port-intensive R51 acceptance step in CI to prevent transient
Linux TCP `TIME_WAIT` entries from obscuring R19's listener test.

## What an actual ambiguous stage would mean

A matching stage is **evidence of an unfinished or concurrent publication**,
not proof of a corrupted account, an abandoned writer, or a more recent
committed action. Do not delete or automatically promote it based on age.
An operator would first preserve the canonical and adjacent stage evidence,
identify any active writer and separately decide what recovery is appropriate.
R51 has **no automated reconciliation or unblock operation**; its test
removes only a synthetic marker under `TemporaryDirectory`.

R51 does not journal the R47 Rune debit, NPC hit, ground loot or R46
Magic XP together. A crash after an accepted spell but before account-save
capture may still lose the intended debit and let transient NPC/loot reset on
restart. Parent-directory fsync, **game-server and OS/power-loss fault injection**,
truly durable transaction IDs, exact-once reward settlement and graphical
original v308 client acceptance remain separate work. The isolated child
JVM halt covers only the narrow already-forced-before-atomic-move boundary.

**DRAFT / STACKED ON R50 / UNMERGED** pending independent CI and review.
