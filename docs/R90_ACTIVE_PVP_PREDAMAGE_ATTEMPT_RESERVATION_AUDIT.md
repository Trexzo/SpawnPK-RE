# R90 — source-side pre-damage accepted-ATTEMPT reservation, not a durable kill ID

**DRAFT / STACKED ON R89 / OFFLINE TEST ONLY / UNMERGED.**

## Exact real source boundary

The existing `LocalSession` creates a `LocalPlayerInteractionHandler`,
which handles an accepted player Attack. Its `tickAttack` first validates
the target, ownership, range and attack cadence; may emit an attack
animation; then runs the real `PlayerCombatResolutionService`.
The original LocalSession handler uses the **four-argument**
`NO_OUTCOME_OBSERVER`; R86's separate, explicit offline factory
installs a source-proven semantic observer. R87–R89 build only on
that test path.

The real `PlayerCombatResolutionService.resolveImmediateOwned`
checks current attacker and victim World generations, prepares the
timing/damage, and then applies HP damage through
`PlayerLifecycleService.applyDamage` while holding the current
World ownership locks. Only **after** that side effect does
`finish` allocate `PLAYER_KILL` and `PLAYER_DEATH` facts for a
confirmed lethal hit. Those facts still contain only the original
six fields—actor strings, type, context, World tick and authority.
They have **no producer-issued durable operation ID**.

The R89 *synthetic token* was minted inside a post-damage observer
callback. That was sufficient for testing value-and-receipt atomic
publication, but it cannot represent the interval between a
legitimate accepted attack and the first semantic callback. A
process could stop after damage and before an outcome receipt
exists. Issuing another UUID after restart would not identify the
same combat operation.

## R90 intervention — explicit isolated test preflight

`R86OfflineActivePvpOutcomeBridge` now supports
`installOfflineAcceptedAttemptForTest` **before any source events
are published**. No existing `LocalSession` constructor installs
this journal. The default R85, R86, R87, R88 and R89 paths retain
their original no-journal behavior.

Inside the R86 active handler's **already validated, World-owned,
two-player generation/mutation critical section**, and **before
calling the original real PvP resolver**, the bridge can optionally
invoke `R90OfflineAcceptedAttackJournal.reserve`. The journal:

1. Requires an actual WorldPulse execution context, two distinct
   World-owned attacker/target objects and exact current actor
   generations. The calling handler has already passed target
   validation, attack range/cadence and paired ownership checks.
2. Creates a unique **synthetic test attempt UUID**, with actor IDs,
   both registered World generations, the actual attack tick and
   weapon ID as *diagnostic* fields. The UUID is an ID for the
   **admitted attempt only**, not evidence a hit was resolved or a
   kill/death event occurred.
3. Uses a fixture-owned temporary directory, a cooperative OS file
   lock and `AtomicAccountPublisher.publish` with forced staging
   and REQUIRED atomic rename to publish a strict single-file
   `ATTEMPT_RESERVED_ONLY` witness. It verifies the canonical
   file before letting the real resolver proceed. It never accepts
   a preexisting canonical witness, malformed schema or ambiguous
   `.r16-*.stage` evidence.
4. Rejects new attempts when its bounded test reservation capacity
   is exhausted or a fault/IO exception prevents reliable
   reservation. A denied reservation stops the call to the actual
   PvP damage resolver and therefore prevents HP mutation. An
   attack animation *may already have been transmitted* before the
   preflight; this is not an atomic network transaction.
5. For genuine lethal source outcomes, a private ThreadLocal binds
   the **same** accepted attempt reservation to the distinct kill
   and death facts, but only **inside** the existing R86 exact-object
   DomainEventBus listener proof. The association is discarded
   immediately after the resolver returns.
6. On another JVM, the reader only reports
   `RESERVED_QUARANTINED`, `MISSING_BLOCKED` or
   `INDETERMINATE_BLOCKED`. **Every result refuses** inferred
   damage completion, kill confirmation, replay, scoring commits,
   login and rewards. A SHA-256 evidence checksum detects accidental
   corruption only; it is NOT a MAC/signature or authentication.

An **attempt reservation alone cannot tell whether HP changed**.
The critical caller-owned failure injection
`INTERRUPT_AFTER_RESERVATION_BEFORE_DAMAGE` publishes the witness
successfully and then refuses to run the damage resolver. A real
complete lethal attack and a no-damage interrupted attempt therefore
have the SAME restart review category:
`RESERVED_QUARANTINED`. This proves why a durable attempt ID
does **not** become a durable combat-outcome commit certificate.

## Source-exact failure and restart tests

`R90OfflineActivePvpPreDamageReservationTest` reuses the original
R86 real active handler fixture with actual
`LocalPlayerInteractionHandler.handleResolved/tickAttack`, genuine
Player81WorldSync and real WorldPulse. The tests verify:

- Successful reservation exists **before** the first genuine
  `PLAYER_KILL` callback. One admitted accepted attempt links
  its two distinct source-proven kill/death facts while the R86
  listener remains active. HP goes to zero, lifecycle death and
  HP packet 134 still occur, and the active source audit receives
  both facts. The attempt association ends with the callback.
- Injected refusal **before** reservation prevents actual HP
  damage, kill/death callbacks and semantic projection updates;
  no canonical witness exists. The child JVM reports
  `MISSING_BLOCKED`, not replay permission.
- Injected interruption **after** an otherwise valid reservation
  but **before** canonical damage leaves HP unchanged at 9,
  with no semantic outcomes; the canonical reservation still
  exists and a second JVM reports
  `RESERVED_QUARANTINED`, exactly as with a genuinely completed
  source hit.
- Direct reservation outside WorldPulse and a duplicate/late
  installation are refused. Late replay of a completed source fact
  cannot retrieve the original current attempt association.
- Tampering with the canonical evidence and inserting a staging
  file next to the valid witness produce
  `INDETERMINATE_BLOCKED`. The original staged evidence is
  not renamed, promoted, deleted or assumed to be abandoned.
- Independent child-JVM read-only reviews cover each category;
  no replay, rollback, login, reward, score or account migration
  occurs.

The dedicated R90 Linux/Windows CI compiles Java11-compatible
server bytecode and executes R90 plus inherited R63–R89 suites.
The independent full server build and Windows atomic persistence
job must pass on the **exact final PR head**.

## What R90 does NOT solve

- This is **not** a production-authoritative issuer. The
  accepted-attempt UUID is randomly generated in a disposable
  offline test and is only locally coupled to real active handler
  ownership. It is not authenticated against a durable game
  account/owner generation after restart.
- An accepted attack can fail timing/damage preparation or stop
  before applying HP. This witness does not certify that the
  attempted hit resolved, that either semantic fact was emitted,
  or that any projection consumed it.
- The target HP state is a separate mutable object and account
  save; an attempt file is not atomic with damage, objective
  scores, match, Collection Log or account persistence. A crash
  can leave multiple legitimate uncertainty windows.
- The existing event `CombatOutcome` has no durable logical ID.
  Kill and death remain separate events, and the test association
  does not change their public schema or any network packet.
- The default `LocalSession` active attack continues using
  `NO_OUTCOME_OBSERVER`; R90 does not wire a live combat journal,
  save location, event stream, rewards, or actual player account.
- File force/rename does not establish parent-directory fsync or
  storage power-loss durability. The lock serializes only
  cooperating test writers.

A future R91 should examine the **damage-applied but outcome-unemitted**
gap, along with account-save ordering and power-loss semantics,
rather than inventing replay permission from one reserved record.

**R90 DRAFT / OFFLINE TEST ONLY / UNMERGED / NO LIVE ACTIVATION.**
