# R97 — offline retirement-witness atomic-publication and integrity failures

**DRAFT / OFFLINE ONLY / STACKED ON R96 PR #3141 / UNMERGED / NOT DEPLOYED**

## Source risks addressed

R96's separate PREPARED/COMMITTED witness is published with
`AtomicAccountPublisher`, bound to the R60-locked R63/R64/R89/R90
account and producer identity. The new file is neither a player-login
credential nor a recovery certificate.

R97 explicitly tests failures in the R16 publication path and the
conditions under which a previously COMMITTED witness must be refused.

## Controlled publication faults

A test-only `failNextAtomicReplacementForTest(Phase)` hook invokes the
existing R16 staging writer, lets it write and force staged bytes,
then throws an IOException at the actual replacement callback.
`AtomicAccountPublisher` must delete its temporary file in `finally`.

The test runs two separate real isolated World fixtures:

- **Failed PREPARED replacement:** R89/R90 source ordinal 1 has committed;
  the cooperating World generation is frozen for retirement; replacement
  fails, no R96 witness exists, and unregister stays refused. Inspection
  reports MISSING_UNPROVEN, with no inferred login authority.
- **Failed COMMITTED replacement:** PREPARED is on disk; the actual World
  actor is unregistered; replacement fails before modifying PREPARED.
  The original PREPARED bytes stay unchanged and a fresh JVM separately
  reopens them as PREPARED_QUARANTINED, not COMMITTED.
- Both cases check all five canonical account/ledger/owner/R89/R90 file
  bytes unchanged and confirm no abandoned R16 staging entry survives
  an ordinary thrown replacement callback.

This models a *handled* IOException; it does **not** model loss of power
or the abrupt death of an OS process in between stage creation and
cleanup. R51 still requires fail-closed handling for actual abandoned
stage files.

## Canonical record and R51 ambiguity negatives

R97 first produces a genuine COMMITTED receipt from a real World save
and unregister, then exercises these isolated test-side mutations:

- Change phase without updating the receipt digest: digest mismatch.
- Append an unrecognized key: reject schema mismatch.
- Damage the owner UUID: reject malformed field.
- Replace canonical receipt with an empty file: reject invalid size.
- Leave an adjacent `.r16-*.stage` entry: reject via R51 before
  receipt read, never adopt or delete that ambiguous stage.
- Substitute a directory at the canonical receipt path: reject
  nonregular record.
- Restore the original receipt; explicitly rotate the R90 producer
  incarnation under the real R60 OS lock. Despite the unchanged R89
  committed ordinal, the old COMMITTED witness must fail the
  bound-source identity comparison.

Every refusal verifies that it did not change the evidence bytes.
A fresh independent JVM separately verifies the tampered-digest and
stale-producer refusals. Neither accepts login or performs repair.

## Validation

The focused R97 Linux/Windows CI compiles Java 11 compatible bytecode
and runs `R96OfflineDurableRetirementWitnessTest` (including the
R96 baseline and added R97 fault cases). Separate inherited workflows
continue to exercise the broader R90–R96 regression sets, Linux Gradle
build and Windows atomic persistence tests.

## Boundaries

The R96 SHA-256 checksum detects accidental or non-cooperating simple
tampering; **it is not cryptographic authentication**, because a
hostile local writer could recompute it. No R97 fault injection is
installed in actual LocalSession, account login, World.shared or
production player lifecycle.

Neither R96 nor R97 proves atomic cross-file transactions, durable
directory synchronization, crash-safe temporary cleanup after a
killed process, automatic recovery, safe independent producer
takeover, or exactly-once PvP/reward publication. PREPARED or
mismatched evidence remains quarantined.

**Keep draft and unmerged/undeployed. Require exact-head CI results
before claiming verification.**
