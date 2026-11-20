# R96 — durable retirement witness after committed World save

**OFFLINE TEST ONLY / DRAFT / STACKED ON R95 #3140 / NO LIVE LOGIN, NO DEPLOYMENT**

## Source gap and contract

R95 verifies that restart cannot trust an R94 per-actor uncertain-save
bit once its JVM has exited. A fully COMMITTED R89/R90 producer ordinal
is not by itself evidence that the originating World actor actually
unregistered. A distinct retirement witness is needed to distinguish a
cooperative finished unregister from retirement that is still pending
or interrupted.

R96 adds a **separate test-only** per-account witness file under
`sessions/<username>.r96-offline-retirement.properties`:
`PREPARED` or `COMMITTED`. All publication uses R16's forced
staging/required atomic replacement, guarded by the existing R60 OS
FileLock and R51 no-ambiguous-stage check. No test stage is
automatically deleted, rolled back, repaired or promoted on restart.

The source identity is bound to R64 username, owner UUID/epoch, R90
stream UUID, incarnation UUID, producer generation, R89/R90 committed
ordinal, World actor ID and actor generation. The source is
independently revalidated against live canonical R63/R64/R89/R90
records inside one R60 critical section. The receipt's SHA-256
provides a corruption check, **not** authentication of an adversarial
in-process writer.

### PREPARED barrier

1. The R93/R94 World test-only lease freezes new captures by marking
   the specific player generation as retiring inside a short World
   lifecycle critical section. Active or uncertain saves reject
   preparation; no disk IO is performed under the World lock.
2. Under R60, check active R64/R63 owner and R89/R90 COMMITTED
   producer with the exact expected ordinal and incarnation.
3. Reject any pre-existing R96 witness or ambiguous adjacent staging.
   Atomically publish a PREPARED witness.
4. The World unregister hook is now gated on an explicit in-memory
   permit; a direct unregister attempted before permit returns false.
   Any preparation failure leaves the actor frozen and unretired.

### COMMITTED barrier

1. On the *same* cooperating World and the same controller, after
   PREPARED succeeded, explicitly permit unregister in memory.
2. Call actual `World.unregisterPlayer(actor,generation)`; require
   success. This section performs no filesystem IO.
3. Recheck the exact committed source and PREPARED witness under R60;
   only then replace the witness with COMMITTED.

An interruption between steps 2 and 3 leaves PREPARED on disk, even
if the in-memory actor was removed. A new JVM may observe this
uncertainty but **may not** infer a safe automatic takeover. Only
COMMITTED is positive evidence that this cooperating offline
controller observed a successful World.unregister call.

### Executable acceptance regressions

`R96OfflineDurableRetirementWitnessTest` uses a real isolated World
actor, real snapshot capture and separate IO worker, R89/R90 durable
commit, World unregister, and three independent new Java processes:

- A deliberately unsaved actor cannot publish an R96 receipt even
  when a caller supplies ordinal 1, and cannot unregister while
  frozen.
- A PREPARED-only interruption leaves the actor registered and
  refuses direct unregister. Fresh JVM reports PREPARED_QUARANTINED.
- An injected failure **after actual World.unregister** but
  **before COMMITTED publish** leaves PREPARED on disk. Fresh JVM
  reports PREPARED_QUARANTINED.
- A completed unregister publishes COMMITTED. Fresh JVM reports
  COMMITTED_WITNESS_ONLY.
- Each fresh process re-inspects the real R64 owner and R89/R90
  source, checks status and ordinal, confirms no login authorization
  or automatic recovery, and byte-compares all five original
  canonical files **and** the R96 receipt before/after inspection.

A dedicated Linux/Windows Java 11 bytecode workflow runs the focused
R96 test. The independently triggered inherited R95–R63 workflows
still run their full regressions; independent server/atomic persistence
builds also continue.

## Explicit boundaries

**No production LocalSession or World installs this protocol.**
There is no automatic replay, login, final-save integration, reconnect
policy, durable cross-JVM process ownership, cryptographic source
authentication, exactly-once PvP or reward publication, cross-file
atomic transaction or host power-loss durability proof. A malicious
writer who can edit the same local files may forge witness fields and
hash; a direct writer bypassing the cooperative gate is not fenced.
Even COMMITTED is an offline observational fact, not login or
new-owner authorization. A restarted World must independently
validate its own admission and lifecycle rules.

A source's R89/R90 records may change after R96 COMMITTED if a later
authorized save or producer rotation is permitted through another
test path. R96 inspection refuses mismatches; it is not a general
historical archive or standalone replay certificate.

**Require exact-head GitHub CI before claiming verified. Keep PR draft
and unmerged/undeployed.**
