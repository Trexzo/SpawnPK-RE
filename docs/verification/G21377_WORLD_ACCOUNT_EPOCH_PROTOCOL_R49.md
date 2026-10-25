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

## 6. R146–R151 hosted evidence: live exclusion is not durable final-save authority

**Evidence head:** [`9270745a`](https://github.com/Trexzo/SpawnPK-RE/commit/9270745a242b46a4f44b60817580eb61c12e9047) (R151), [Linux Gradle #40199765645](https://github.com/Trexzo/SpawnPK-RE/actions/runs/40199765645) SUCCESS, [Windows source-only #40199765604](https://github.com/Trexzo/SpawnPK-RE/actions/runs/40199765604) SUCCESS. The same branch's R149 [`bdbb559e`](https://github.com/Trexzo/SpawnPK-RE/commit/bdbb559e860a2c053f37089aa398d13dad383e4a) and R150 [`d732789a`](https://github.com/Trexzo/SpawnPK-RE/commit/d732789a9082cdb77eba1aa5406d918ed0326cc1) also passed both workflows at their exact heads. These are source-revision synthetic TCP + hosted filesystem observations, not original-client or crash/power-loss certification.

The test matrix is **four scenarios**: initial account file present vs absent before first login, each crossed with independent-JVM same-byte inode substitution vs hardlink insertion at an authentic World final-save postmove cutpoint. The first World reports an uncertain/failed final save and retains its in-memory same-World veto. All cases preserve the observed foreign postimage after the failed final and original World shutdown.

| Evidence | Observed result on hosted Linux | Safety interpretation |
|---|---|---|
| R146 | A separate JVM can read the uncertain account using `FilePlayerRepository.loadForWorldSession` after original World exit | In-memory failed-final veto was not durably published |
| R147 | A fresh World authenticates a real loopback TCP session for that account | This is **unsafe readmission**, not recovered ownership |
| R148 | Later ordinary save replaces the canonical file while the unrelated hardlink retains its old inode and bytes | Neither alias nor new account proves reconciliation |
| R149 | Fresh World closes with its own new canonical postimage unchanged; optional final-review witness is absent | Later normal final success cannot retroactively ACK the first failure |
| R150 | A third independent JVM, while the second World is **live**, cannot obtain its cross-JVM OS account lock or authenticate the same account over TCP | Cooperative *live* session exclusion works in this tested opt-in configuration |
| R151 | After second World closes/releases, a third JVM can acquire the OS lock and authenticate over genuine TCP | Released lock conveys **no durable claim** about the original failed final |

The Linux R151 run emitted `R151_POSTRELEASE_THIRD_JVM_GAP_CONFIRMED` four times. The tests assert the before/after cutpoint and account identities in disposable per-case files. The R151 third JVM uses ordinary save/close behavior; it is **not** a recovered session or a proof that the previous failure was harmless.

### Concrete next implementation gate (not yet satisfied)

For any future opt-in implementation to reverse the demonstrated unsafe outcome, it must satisfy all of the following against these *same genuine TCP scenarios*:

1. **Before hydration:** bind account path, schema, an authentic monotonic durable account epoch and the selected previously committed snapshot under an unambiguously owned cross-JVM lock. Publish a durable **ACTIVE/negative** account HEAD before the new World can mutate gameplay state. A failed, absent, unforced, unknown-version or partially readable claim is not a successful login.
2. **Before each checkpoint/final:** accept only an immutable snapshot from the exact current session owner/generation and strictly increasing sequence. Force the complete snapshot object and its directory before it can be referenced by a forced authoritative HEAD. Stale, default-off, bypass RAW and unexpected link/inode changes require a negative decision; they cannot silently publish a successful state.
3. **On any final-save ambiguity:** do not release positive admission authority based only on `SaveTicket` completion, byte equality, inode stability, a later normal save or an OS-lock release. After a **hard kill** of the exact owning process at each documented publication cutpoint, a separately started World must either observe a fully validated reusable committed HEAD or refuse admission with preserved operator-review evidence. Refusal is required on unproven outcome, not a guessed rollback or inferred successful replay.
4. **Across two independent JVMs:** replay R150 while the first process holds the lock, then R151 after it releases. Only a fully validated previous-epoch final transition may permit the subsequent owner; simply closing the first session and removing the lock must not convert an unresolved failed-final state into a new grant.
5. **Operational prerequisites:** specify every writer participating (World, checkpoint, BOOTSTRAP, RAW, named profile, strict path); define explicit migration and opt-out behavior; bounded canonical record parsing, NOFOLLOW and durability-capability preflight, forced directory + exact postimage verification, and a separate operator audit/recovery contract. Reject unsupported Windows/filesystem semantics rather than treating source compilation as durability support.

**Go/no-go:** no positive adoption of the new authoritative HEAD in `LocalSession`, `WorldPlayerPersistence` or `FilePlayerRepository` until a standalone codec/publication layer and killed-JVM cutpoint matrix are independently tested. The existing `LocalDurableOwnerEpochPrototype` is intentionally non-admitting and cannot be treated as the new authenticated HEAD. Existing R146–R151 successful tests are **red evidence for release**: they prove which failure mode the eventual strict protocol must eliminate. Keep #2540 and #2559 OPEN, PR #2965 DRAFT, and all automatic recovery/marker-retirement/owner-handoff/ACK paths disabled.

## 7. R152 codec-only milestone — no authority

A standalone untrusted HEAD byte format and build-gated regression have been introduced as a first implementation seam. LocalAccountEpochHeadCodec canonicalizes fixed-order bounded ASCII fields (account, positive epoch, sequence, World generation, state, previous HEAD digest and immutable snapshot digest). The regression covers deterministic roundtrip for four enum states and rejects malformed, duplicate, reordered, noncanonical, oversized and digest-mismatched inputs.

This is NON-ADMITTING ONLY. Neither parsing COMMITTED nor matching a snapshot digest grants ownership, validates epoch ancestry, proves a durable write or permits any login, release, final-save ACK, cleanup or recovery. There is no filesystem publication here, no process-kill crash test and no integration with the live admission or save path. The next gates remain immutable object + forced HEAD publication, postimage identity checks, killed-JVM cutpoints and deliberate fail-closed opt-in admission. Issue #2540 and #2559 stay open; #2965 stays draft.
