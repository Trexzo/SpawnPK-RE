# R49 — World account epoch and final-save publication: implementation contract

**Status:** DESIGN / RELEASE-BLOCKING, not a implemented account lifecycle.  
**Audit base:** `d529431e9740f1249c2aa21bb2d11697f6947d03` (R48 exact-head Linux + Windows successful).  
**Tracked blockers:** [#2540](https://github.com/Trexzo/SpawnPK-RE/issues/2540) failed final-save durability, [#2559](https://github.com/Trexzo/SpawnPK-RE/issues/2559) cross-World ownership. [Draft #2965](https://github.com/Trexzo/SpawnPK-RE/pull/2965) remains a source-level integration vehicle, **not** release authority.

## 1. What the running code actually does

| Boundary | Current implementation | Crucial limit |
|---|---|---|
| Admission | `LocalSession` / `LocalAccountProfiles` / `FilePlayerRepository.loadForWorldSession` with optional `LocalJvmAccountSessionLease`, `LocalCrossJvmAccountSessionLock` and review vetoes | Default-off participation and historically unleased sessions prevent claims of complete cross-process exclusion |
| Ownership while live | `LocalJvmAccountSessionLease.Token` records one JVM owner, selected path and World generation; `SaveCaptureWitness` binds captured saves to original owner | JVM identity and filesystem observations are not a durable, monotonic named-account owner epoch |
| Final capture | `LocalSession.saveAccountFinal` reserves persistence FIFO; `captureFinalSaveAndUnregister` publishes the owned final snapshot | On wait/write failure the exception is logged and an optional lease is retained in memory, not a crash-durable positive completion |
| Publication | `WorldPlayerPersistence` worker calls `FilePlayerRepository.saveForWorld` and optional forced-postimage checks | A file rename/force/postimage observation alone cannot atomically commit ownership, account state and reconnect authority |
| Negative evidence | Experimental G21.188 ACTIVE, G21.298 final witness, permanent mailbox review fences | Conservative **no-auto-retirement** vetoes; a healthy file does not authenticate positive account handoff |
| RAW paths | `FilePlayerRepository.save`, `AccountStore.save`, `LocalAccountProfiles.save`, standalone strict writer; opt-in guards through R48 | Source-level exact-inode/bytes checks are negative race detectors, not all-writer participation nor account-level fencing |
| Prototype epoch | `LocalDurableOwnerEpochPrototype` and G21.196/G21.198 tests | Untrusted caller-manufactured record, intentionally **not wired** to login or used for reuse/positive authority |

Historical evidence includes `G21177CoreSocketFailedFinalSaveStaleReconnectIntegrationTest`, `G21179CoreSocketFirstSessionFailedFinalSaveIntegrationTest`, `G21180CoreSocketCrossWorldConcurrentAccountIntegrationTest`, `G21195CoreKilledFailedFinalSaveReviewVetoTest`, and `G21196CoreOwnerEpochReservationBindingProcessKillTest`. Review each at an exact head before treating its asserted diagnostic as a passed test. These characterize failure or permanent quarantine; they do not collectively prove a reusable durable account lifecycle.

## 2. Decision for the next implementation seam

Build one *separately opt-in* **account-local authoritative epoch/head** design using existing account/lock and snapshot plumbing; do not add a second ordinary RAW writer or another ad-hoc independent grant pathway.

A possible crash-consistent layout is:

- An immutable, bounded, forced snapshot object whose digest is its content identity; never modify a published object in place.
- One canonical, bounded `HEAD` record: account identity + schema + strictly ordered owner epoch / sequence + state + digest of the specific complete immutable snapshot. The record is the **only** source of truth for session admission once the new mode is explicitly activated.
- Every `HEAD` transition is written through a uniquely owned NOFOLLOW temp, forced, atomically replaced without fallback, parent-directory forced, reread with object identity and exact digest checks. Snapshot object publication and parent force **precede** any `HEAD` reference to it.
- A cooperating cross-JVM account lock must cover complete admission/ownership/final-publication, including checking lock-leaf identity. Lock acquisition alone is not a durable epoch.
- No implicit trust in existing legacy `.properties` postimages, untrusted standalone prototype records, unrecognized sidecars, filesystem mtimes, requested username text, cached World generations, or success of a prior test hook.

This is a **candidate contract**, not a claim that two-file transactions become atomic: the single-head reference can point only to a previously completed forced immutable snapshot. Failed or missing object / marker, unverified rename outcome, ambiguous metadata or race all lead to **negative/manual review**. No assertion of protection from malicious or nonparticipating filesystem writers.

### Proposed state machine (not yet implemented)

| Durable state | Admission / publication rule |
|---|---|
| LEGACY / UNMIGRATED | Existing behavior is preserved when new mode OFF. Explicit operator-reviewed migration required; no automatic reinterpretation of a `.properties` file as an authoritative committed head |
| COMMITTED(epoch, sequence, digest) | Exact account, schema and immutable snapshot validated under the cooperating lock; candidate for a *new* epoch only if no unresolved negative marker or competing ownership exists |
| ACTIVE(epoch, generation, sequence, digest) | Durable negative session ownership published **before gameplay mutation**. Only the exact current owner may publish a later checkpoint or final snapshot. Fresh JVM/World admission must fail closed while ACTIVE survives |
| UNCERTAIN / REVIEW | Missing, malformed, stale, contradictory, incomplete or unforced transition, failed final save, unverified lock continuity, retained review evidence: **deny admission and all normal writers**, preserve bytes and operator evidence |
| FINAL_COMMITTED(epoch, sequence, digest) | Only a proved current-epoch final snapshot with forced immutable object, forced authoritative head and postimage may be a candidate for transition to reusable COMMITTED. No direct assumption that a login/worker timeout was a positive acknowledgment |

The state names are design labels. No existing code is entitled to parse or publish these states as authority until the implementation and regression gates below are complete. In particular an ACTIVE -> COMMITTED handoff requires the **same account's exact epoch, ownership token and durable postimage**; no mere marker disappearance or snapshot equality is permission.

## 3. Required ordering and crash cutpoints

1. **Admission:** acquire one stable cross-process lock; reject all legacy negative witnesses, hardlink/symlink aliases or unsafe account paths; inspect and validate entire current authoritative head and referenced immutable snapshot. If the mode is not explicitly migrated and complete, deny rather than infer a safe default.
2. **Owner claim:** allocate strictly monotonic account-local epoch and produce a forced ACTIVE head while lock ownership is held, before admission can mutate World state. Failure before durable ACTIVE is not a successful login. Owner binding must include exact World/player generation, not a manually supplied value.
3. **Gameplay checkpoint:** capture immutable snapshot under current World ownership, seal bytes, force a unique object, force its directory, publish monotonically increasing ACTIVE head, verify head and object before reporting checkpoint success. Never permit an old checkpoint to overwrite a newer epoch.
4. **Final save:** reserve FIFO, capture + unregister once, publish immutable final snapshot and a current-owner final transition. Any error before complete head verification is UNKNOWN/REVIEW, never an ACK or automatic rollback. Existing final-save completion timeout and interruption paths remain negative.
5. **Release:** only after complete durable final-commit proof may the lease be released and later admission evaluated. An external observer must see either a verified reusable committed snapshot or explicit negative state after each crash cutpoint; NEVER a silently stale bootstrap.
6. **RAW participation:** refuse nonparticipating `save`/profile/store/strict writes while authoritative mode active. Any separately enabled RAW maintenance writer needs the same account epoch and final-publication contract; otherwise reject without temp I/O. Keep default legacy mode unchanged until migration is explicit.
7. **Restart:** independently reload from disk under the cross-process lock. Crash during object creation, object force, object rename, head temp creation/force, head rename, directory force, postimage check, and lock release is checked separately. An ambiguous postmove state cannot cause an unqualified positive session.
8. **Operator recovery:** read-only forensic inventory, backup and explicit recorded human decision first. Automatic journal cleanup, orphan object deletion, account replay/grants, witness retirement or escrow reissue are **out of scope**.

## 4. Test matrix before any positive lifecycle claims

**On Linux or another filesystem proven to support the selected strict primitives:**

- Initial missing account, first bootstrap file, existing healthy account, interrupted initial owner claim, valid repeated save, and invalid same-account owner.
- Two live isolated Worlds in **one** JVM, then two **independent** JVMs, with divergent snapshots and stale second save; one and only one current epoch must be capable of publication.
- Hard-kill at each step in section 3; relogin in a new process; check exact byte postimage and that ACTIVE/REVIEW never hydrates silently stale state.
- Delayed checkpoint from previous generation, out-of-order save worker, FIFO backpressure, final-save timeout, caller interruption and execution failure.
- Cross-process RAW repository/primary/named/strict writers, late opt-out, hardlink and symlink aliases, same-byte inode substitution, same-inode changed bytes, stale path/lock-leaf substitution.
- Reject tampered/unbounded/unsupported head and immutable object, wrong username, wrong generation, duplicate field, digest drift, partial directory force or missing object. Do not relax these into a default account.
- No leaked JVM leases, OS locks, uncommitted temp publication, automatic grant, settlement, marker deletion, snapshot replay, or silent operator override.
- Original v308 graphical client login, logout, return/reconnect, inventory and bank transitions under the **actual** local server process. Source-level packet simulations are insufficient.

**Windows:** the current Windows workflow is a **source-only preflight**. It is not a filesystem-durability or original-client runtime certificate. If native directory force, NOFOLLOW, inode or hardlink evidence is unavailable, reject an attempted strict mode rather than downgrading it.

## 5. Delivery order, review gates and stop conditions

1. **R49 (this document):** settle one account/head ownership model, mark exact existing authority versus unused prototypes, freeze all positive release claims. No production gameplay activation.
2. **Protocol-only implementation:** separate state codec and immutable object/head publication under an exact account lock; bounded deterministic tests plus killed-JVM stage matrix, initially **unwired** to LocalSession. No extra gameplay recipe or login acceptance.
3. **Read-only admission integration:** new opt-in fail-closed reader with migration guard. Prove that missing/active/uncertain/foreign/wrong-epoch records cannot hydrate or accept old snapshots.
4. **Write-path integration:** actual World capture, checkpoint, final save, cross-JVM ownership, and all competing RAW/legacy paths participate or reject; tests must exercise real TCP LocalSessions.
5. **Operator-reviewed recovery and real client:** only after full crash-cutpoint evidence and an explicit product decision on whether/how to retire negative markers. No default-on switch and no issue closure until verified.

**Not certified:** this document adds no durable protocol, token authority, positive release, account migration, crash recovery, gameplay feature, donor recipe registration, or original-v308 runtime compatibility. The permanent negative witnesses must remain untouched. [#2540](https://github.com/Trexzo/SpawnPK-RE/issues/2540) and [#2559](https://github.com/Trexzo/SpawnPK-RE/issues/2559) remain OPEN, and PR #2965 stays DRAFT.
