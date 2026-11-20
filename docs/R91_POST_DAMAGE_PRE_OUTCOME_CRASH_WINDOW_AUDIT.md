# R91 — canonical HP applied, combat outcomes not emitted (offline test)

**DRAFT / STACKED ON R90 / OFFLINE ONLY / UNMERGED / NO LIVE ACTIVATION**

## Source-verified crash window

The actual `LocalPlayerInteractionHandler.tickAttack` invokes
`PlayerCombatResolutionService.resolveImmediateOwned` after validating
target, World actor generation, range and attack cadence. The service
prepares its damage/timing inputs, then calls the real
`PlayerLifecycleService.applyDamage` under owner/target mutation locks.
That method updates canonical target HP and death lifecycle **before**
`PlayerCombatResolutionService.finish` creates two distinct immutable
`CombatOutcome` objects (`PLAYER_KILL` then `PLAYER_DEATH`). The
active handler publishes target HP packet 134 **after the resolver
returns**, and the production four-argument resolver still uses
`NO_OUTCOME_OBSERVER`.

R90 added an offline-only, test-owned **ATTEMPT_RESERVED_ONLY** witness
before the resolver's damage call. R90 proves that a successful attack
and an interrupted **no-damage** attempt can leave the same reservation
status on disk. But even after canonical HP *does* change, the process
can exit or an injected exception can stop execution before either
semantic event reaches consumers. R90's reservation cannot fill this
gap.

## Changes and precise opt-in boundary

`PlayerCombatResolutionService` retains both public/package-private
existing constructors. A separate optional **six-argument test-only
constructor** accepts `OfflineAfterOwnedDamageForTest`. The original
LocalSession/active PvP four-argument handler and the standalone
five-argument source observer remain unchanged. The R86 explicitly
opted-in isolated active-handler factory alone supplies
`bridge::afterCanonicalDamageBeforeOutcomesForTest`; the bridge
returns immediately when no R91 journal was installed, preserving the
entire R86–R90 test behavior.

Only after `applyDamage` has returned its **actual**
`PlayerLifecycleService.DamageResult`, under the existing current
attacker and victim World ownership locks, can the offline callback run.
The R86 bridge requires a current R90 accepted attempt with matching
actor World generation, real WorldPulse, actual target object and tick.
Its private ThreadLocal is unavailable outside the real owned resolver
scope. No client input, copied `CombatOutcome`, network path or later
callback can generate this evidence.

The new `R91OfflinePostDamageObservationJournal` publishes a separate
`DAMAGE_OBSERVED_ONLY` file with the R90 test attempt UUID, actor
strings, tick, **actual** HP-before/after/applied and death/ignored-dead
result. Publication uses the existing forced-stage/required-atomic
replacement publisher; the reviewer validates strict schema, SHA-256
checksum and the R90 reservation and rejects untrusted/ambiguous
staging. The checksum is accidental corruption detection, **not**
source authentication. The two files and canonical in-memory HP are
three **independent writes**, not one transaction.

### Deterministic fault windows

`R91OfflineRealPvPDamageOutcomeGapTest` reuses the actual R86 active
handler/Player81WorldSync/WorldPulse and R90 pre-damage reservation:

1. **Normal, complete lethal attack.** The R90 reservation is
   published, canonical HP changes from 9 to 0, and R91's post-damage
   witness records that result **before** genuine `PLAYER_KILL` and
   `PLAYER_DEATH` events reach the two normal R86 subscribers.
   The handler returns, HP packet 134 is published and its bound
   objective changes by one.
2. **Fault after canonical HP but before R91 witness publication.**
   Canonical HP is already 0 and victim lifecycle is dead; the R90
   accepted-attempt file exists; the R91 file is **missing**. The
   injected `IOException` aborts `finish` before both semantic
   outcomes, so neither R86 fact nor its objective increment occurs.
   Because the handler never reaches its post-resolver presentation
   step, it also cannot report HP packet 134 delivery. The earlier
   attack animation may have been sent; the test does not claim a
   network transaction rollback.
3. **Fault after R91 witness publication but before outcome emission.**
   Canonical HP and the R91 evidence already show lethal damage, but
   **zero** real kill/death callbacks were emitted. On restart the
   R91 evidence is classified `OBSERVED_QUARANTINED` — exactly the
   same as in a *fully emitted* attack. The evidence **cannot** prove
   semantic delivery, match/collection commit or eligibility to
   reissue an event.
4. **Corrupt and abandoned evidence.** A tampered R91 canonical file
   or ambiguous adjacent staged replacement returns
   `INDETERMINATE_BLOCKED`; staged evidence is left untouched.
5. **Misuse.** Duplicate offline hook registration and fake
   after-damage calls outside the exact real World resolver scope
   fail closed. R91 does not attach after events already occurred.

For each normal/failure window the test subsequently captures the
**actual victim's** `PlayerSnapshot` from a later WorldPulse via the
original `WorldPlayerPersistence.captureAndSave`, bounded IO worker
and temp-owned `FilePlayerRepository` atomic publisher. The saved
canonical account contains HP 0 in `skill.<HP>.current` even when
NO semantic event was emitted. Each scenario launches a **separate
Java process** which reloads the saved account and independently
reviews both R90 and R91 forensic witnesses. Every review returns
no inferred event delivery, reward, login, replay or rollback
authority. No production account directory is touched.

The dedicated R91 workflow compiles Java 11 bytecode and runs R91
plus inherited R63–R90 regressions independently on Linux and
Windows. Full server Gradle and Windows atomic-persistence exact-head
checks must also pass.

## Limits / next safety boundary

- **No production integration:** no LocalSession constructor,
  connection/packet handler, reward system, actual profile path or
  real account receives this test-only hook. Production's
  `NO_OUTCOME_OBSERVER` remains untouched.
- **A post-damage file is NOT a transactional commit:** HP, accepted
  attempt evidence, post-damage evidence, optional real account save,
  and subsequent kill/death/projection delivery all cross different
  state and persistence boundaries. A crash can leave any prefix.
- **No durable source operation identity:** R90's UUID is synthetic
  and local to one verified offline attempt. R91's checkpoint is not
  an independently authenticated, restart-stable event ID.
- **No safe automatic replay:** after seeing HP 0 plus
  `DAMAGE_OBSERVED_ONLY`, a restarted server still cannot tell if
  objective/match/collection consumers saw zero, one or two events.
  Replaying could double count; withholding could leave missing
  projections. The only supported disposition remains quarantine.
- **Not a real power-loss/DirFSync test:** forced bytes and required
  atomic file rename do not prove parent-directory fsync or
  cross-file atomicity. Synchronous test IO under the World
  mutation lock is intentionally unsuitable for unreviewed
  production latency and must not be enabled without redesign.

**R91 DRAFT / OFFLINE ONLY / UNMERGED / NO LIVE ACTIVATION.**
