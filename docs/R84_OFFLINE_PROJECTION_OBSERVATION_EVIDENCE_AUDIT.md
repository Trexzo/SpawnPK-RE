# R84 — offline post-observer projection evidence; no implied transaction commit

**DRAFT / STACKED ON R83 / OFFLINE ONLY / UNMERGED.**

## Source-exact persistence boundary audit

The actual combat projection services provide **separate** synchronized
in-process interfaces, but not a shared durable commit protocol:

| Projection | Actual observed boundary | What is *not* established |
| --- | --- | --- |
| Objective | `ObjectiveProgressService.advance` adjusts one in-memory objective and `get/snapshot` capture synchronized copies | No per-combat producer operation ID, durable receipt, or commit coupled to Match or Collection Log |
| Match | `MatchSessionService.tryAdjustPresentParticipantScore` and `tryAdjustPresentParticipantTeamScore` adjust independent in-memory score maps; `get/snapshot` return immutable match/team/participant copies | No atomic cross-counter commit; no shared objective/collection transaction |
| Collection Log | `CollectionLogService.observeKillCountsAtomically` is an atomic compare-and-set **inside the Collection Log service**. `snapshot` is synchronized | No atomic transaction with objective/match. `CollectionLogRepository` is an **interface** whose `save` has no declared shared commit protocol |
| Account snapshots | `FilePlayerRepository` persists `PlayerSnapshot` through `AtomicAccountPublisher` forced staged bytes and REQUIRED atomic replace | It is not a durable projection receipt, transaction coordinator or authenticated combat-event identity. Parent-directory fsync and hardware power-loss semantics are not proved |

The real `CombatOutcome` has attacker/victim strings, type, context,
World tick and authority but no original producer-issued stable event ID.
R82's source-local ordinal and R83's synthetic UUID are **test fixtures**,
not persistent identifiers from the game server. They cannot be treated
as authenticated across a restart.

R83 persists only a `PREPARED` witness **before** the first projection
write. A read-only reviewer cannot know whether zero, one, two or all
independent projections ran before the last shutdown.

## R84 added code: observations after actual effects

`CombatOutcomeObserverGroup.installOfflinePostObserverObservationForTest`
provides a new optional callback **after** each original observer has
returned or thrown. It reports the index and whether that one observer
returned normally. The live/default path is unchanged when not
explicitly installed. An observation failure is failure-isolated:
it cannot roll back an objective increment or convert a prior
consumer failure into success.

`R82OfflineSourceScopedCombatReceiptLedger.activeVerifiedOrdinalForTest`
exposes an ordinal only while the current real R79 DomainEventBus
listener's **exact immutable fact instance proof** remains in scope.
It does not invent a key from attacker name or tick.

`R84OfflineProjectionObservationJournal` is an **offline-only forensic
witness**, attached to one group in a disposable test World. For each
successful or failed observer callback it:

1. Requires the exact in-scope verified R79/R82 source fact and an
   existing valid R83 PREPARED witness for the same caller-provided
   **synthetic** operation UUID and local ordinal.
2. Reads actual `ObjectiveProgressService.get`,
   `MatchSessionService.get`, and `CollectionLogService.snapshot`
   after the callback and records the objective progress, match
   participant score, match team score and Collection Log absolute
   count or `UNKNOWN`.
3. Writes one **distinct, OBSERVED_ONLY** properties witness per
   synthetic operation/ordinal/observer index, using the existing
   forced-stage/required-atomic-replace publisher. A SHA-256 checksum
   detects accidental corruption. It is **not a MAC/signature**, nor
   a cryptographically authenticated commit or trusted event ID.
4. Preserves any ambiguous staged file; a missing, malformed,
   duplicate, tampered or staged witness is quarantined.
5. Returns only `OBSERVED_QUARANTINED`, `MISSING_BLOCKED` or
   `INDETERMINATE_BLOCKED` from a read-only reviewer. **Every**
   state has `projectionCommitted=false`,
   `replayAllowed=false`, `recoveryAuthorized=false`,
   `rewardAllowed=false` and `loginAllowed=false`.

Individual snapshot calls are synchronized, but **not** one atomic
snapshot across all three services; another non-cooperative writer
could change a service between reads. A successful evidence write
after an observer returned establishes only that the local test
observed those values at those points in time. It does **not**
establish a durable projection commit, crash-safe cross-service
transaction, or permission to replay. A failed observation write
after a completed objective increment leaves the increment in place.

## Deterministic real-source and second-JVM tests

`R84OfflineProjectionObservationRecoveryTest` reuses the original
R82 real lethal PvP WorldPulse fixture and installs the actual R83
PREPARED witness before the observer group. Two independent real
domain subscriptions still target the same underlying objective,
match and Collection Log projection services.

- Normal lethal `PLAYER_KILL` + `PLAYER_DEATH` emits **six**
  post-observer observations (three observers per distinct real
  source-local ordinal), while the second independent group cannot
  double count anything. Kill observer 0 sees objective 1, participant
  0, team 0, collection UNKNOWN; observer 1 sees 1/2/3/UNKNOWN;
  observer 2 sees 1/2/3/7. The actual game logic does not acquire a
  transaction from that progression.
- A separate JVM reopens the last observation and reports only
  `OBSERVED_QUARANTINED`, with no replay, reward, login or
  completion authorization.
- If first-group fanout is cut after the objective callback, the
  later match and collection **kill** observations are absent. The
  previous objective increment remains, and the second group may
  not replay it.
- If the real Collection Log count resolver fails, the collection
  observer is recorded with `observer.returned=false`; the
  objective/match effects remain and the synthetic collection count
  stays UNKNOWN.
- If evidence writing is deliberately prevented **after** the
  objective observer returned, the Objective Progress service still
  contains the increment; the first observation is MISSING_BLOCKED,
  not a reason to rerun that observer.
- If the R83 PREPARED witness cannot be found or validated in the
  configured fixture root, no new observation witness is allowed
  even if actual in-memory projection writes have happened.
- Tampered post-observer evidence and an ambiguous staged replacement
  yield `INDETERMINATE_BLOCKED` under an independent JVM reviewer.
  The staged file remains untouched.
- Repeated offline observer-policy installation is refused and
  later copied/completed source facts cannot create new observations
  after the genuine R79 producer proof expires.
- The R84 dedicated GitHub workflow checks Linux, Windows, Java11
  bytecode, all R63–R83 inherited regressions, and separate cross-JVM
  reviewers. The full server and Windows atomic-persistence checks
  are independent exact-head gates.

## Remaining restrictions

This work does **not** activate any policy in production combat,
login, accounts, objective rewards, matches, real Collection Log
statistics or network code. The synthetic Collection Log count 7 is
an offline caller-provided fixture, not SpawnPK's real PvP formula.

The R83 and R84 witness files are independent publications, not a
single commit. Missing/OBSERVED_ONLY evidence cannot safely tell an
operator to roll back or replay. Source-issued **durable** operation
identity, per-projection commit semantics, a transaction protocol and
recovery policy would all be required for safe automatic replay.

No storage-hardware power-loss proof, parent-directory fsync
guarantee, source-authenticated persistent event identity, measured
performance gain, or live account state change is claimed.

**R84 OFFLINE ONLY / DRAFT / UNMERGED / NO LIVE ACTIVATION.**
