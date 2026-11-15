# R74 — Offline read-only reconciliation of R73/R72 write evidence and R63/R64 account state

## Why this audit exists

R72 demonstrated that an uninterruptible persistence writer can commit an account **after its SaveTicket future has been canceled during shutdown**. R73 introduced an intentionally non-expiring, durable write-intent blocker and serialized opt-in registration and owner replacement under the same R60 OS FileLock. This prevents cooperative replacement while a write remains unresolved, but the blocker cannot currently be retired automatically.

R74 adds an **entirely read-only diagnostic, not a release mechanism**. It compares the recorded write intent, the actual R72 I/O outcome markers, canonical snapshot and ledger, released R64 owner, and R59 trade status **under one cross-process lock**. It answers only what evidence agrees; it never grants login, replay, recovery or quarantine release.

The R74 inspector is package-private and not wired into production LocalSession, player login, normal WorldPlayerPersistence, TradeService or existing account recovery. It does not write reports or certificates to disk.

## Protocol

`R74OfflineReconciliationReview.inspect(username)` acquires the existing R60 OS FileLock for the entire read. It refuses if the lock cannot be acquired or any evidence directory is missing, symlinked, overlapping or otherwise indeterminate.

Under the lock, it:

1. Requires a single, exact-schema, test-owned **R73 per-account pre-admission blocker**, with valid owner UUID, owner epoch, captured generation, R59 journal revision, save sequence, and SHA-256 before/after snapshot digests. Missing or ambiguous staged R73 evidence fails closed.
2. Scans bounded R72 phase filenames in the isolated evidence root. It refuses unknown R72-shaped names, malformed or symlinked entries, ambiguous atomic staging, duplicate R72 INTENT records for the same username, or invalid/missing INTENT associated with any observed phase. It does not read staging payloads or clean anything.
3. Finds the account's R72 INTENT and checks exact equality with **all eight shared R73 fields** (username, owner UUID, epoch, generation, revision, save sequence, before digest and after digest). It validates the R72 state through the existing R72 fail-closed marker inspector.
4. Classifies missing ACK, cancellation with uncertain outcome, or worker failure as **blocked**. A complete ACK is still not sufficient for recovery: it only makes the next comparisons meaningful.
5. Calls `R63AccountSaveAdmission.inspectUnderLock` without reacquiring the OS lock. An indexed R59 pending pair blocks reconciliation. The canonical snapshot hash must exactly match the R63 ledger; the current account generation must equal the R73 captured generation **plus one**, its canonical SHA-256 must equal the R73 after digest, and the R59 revision must still match.
6. Uses a new narrow, read-only `R64OfflineSessionOwnership.requireReleasedOwnerUnderLock` helper. It checks the canonical R63 evidence, requires `RELEASED`, and verifies the owner UUID, epoch, generation, hash and journal revision match the original R73 intent and current canonical ledger.
7. Returns a **non-authorizing** review result in memory. No file is changed or removed. Even in the best case, the R73 write-intent blocker remains present and the ordinary R73 takeover preflight still refuses replacement.

### Read-only outcomes

| Review status | Meaning |
|---|---|
| `BLOCK_MISSING_WRITE_INTENT` | No canonical per-account R73 registration was observed |
| `BLOCK_UNACKNOWLEDGED_WRITE` | The intended write has no complete ACK, including an observed cancellation without ACK |
| `BLOCK_FAILED_OR_PARTIAL` | Worker recorded failure; canonical account may have partially advanced |
| `BLOCK_EVIDENCE_MISMATCH` | Canonical snapshot, account generation or revision differs from expected post-write evidence |
| `BLOCK_OWNER_NOT_RELEASED` | Owner is not RELEASED or its account ledger disagrees |
| `BLOCK_PENDING_PAIR` | R59 indexed pending trade still quarantines the account |
| `BLOCK_AMBIGUOUS_EVIDENCE` | Duplicate, corrupt, staged, malformed or conflicting evidence |
| `BLOCK_LOCK_OR_PATH_INDETERMINATE` | R60 held by another JVM, or evidence root/stage cannot be safely inspected |
| `BLOCK_CONSISTENT_BUT_QUARANTINED` | Normal ACK plus matching account/gen/RELEASED state; **no automatic recovery** |
| `BLOCK_CANCELLED_LATE_ACK_QUARANTINED` | Canceled SaveTicket later had ACK and matching canonical state; **manual review required** |

For every status, `loginAuthorized()`, `replayAuthorized()`, `recoveryAuthorized()`, `writeIntentRetired()`, `quarantineReleased()` and `evidenceModified()` are always **false**. The two ACK classifications are evidence descriptions, not permission to remove the blocker.

## Windows acceptance — November 15, 2026

- Isolated `SpawnPK-R74-ReadOnly-Reconciliation-20261115` worktree, JDK21: `jar testClasses verifyJava11Bytecode --offline --no-daemon` **PASS**, **2,307 major-55 Java 11 class files**.
- `R74_OFFLINE_READONLY_RECONCILIATION_PASS`:
  - Real World-owned final save registered through R73+R72, committed via the actual WorldPlayerPersistence worker using R71/R64/R63; normal ACK and matching RELEASED account produced `BLOCK_CONSISTENT_BUT_QUARANTINED`.
  - **Independent child JVM** repeated each inspection and confirmed no authority or recovery flag was set.
  - A byte-for-byte inventory of all disposable account, R63 ledger, R64 owner, journal and R72/R73 marker files before and after each inspector run confirmed **no evidence mutation**. OS lock files are excluded from this byte comparison because Windows denies reads of an actively locked synchronization primitive.
  - Removing an ACK, removing the R73 blocker, changing the account-generation ledger, reverting the owner to ACTIVE, modifying the owner UUID, corrupting the R72 intent after digest, duplicating a R72 account intent and leaving an R16 staging name all produced their corresponding blocking statuses. Test fixture modifications were restored only by the test; **the inspector never cleaned or repaired them**.
  - A separate R59 PREPARED paired-trade intent created after the completed account ACK blocked R74's previously consistent result.
  - A second real I/O worker intentionally ignored shutdown interruption, settled the SaveTicket as canceled, and later committed account+RELEASED data. While its OS lock was held, the inspector refused on lock uncertainty. Once the worker released it and the late ACK appeared, a fresh JVM reported `BLOCK_CANCELLED_LATE_ACK_QUARANTINED` rather than a normal ACK or a safe takeover.
  - Inherited R73–R69 and R64 regression suites run in the same worktree; independent Linux/Windows CI includes the full R55–R74 account, trade and shutdown chain.

## Important limits

1. **No account release or recovery.** R74 never retires an R73 intent, deletes an R72 marker, promotes staging content, replays a save, updates the R63 ledger or resets the R64 owner. A matching ACK + generation + RELEASED only shows that some recorded files agree; it is not proof no other writer can still operate or that the account is safe for production login.
2. **Offline, cooperative scope only.** The inspector is opt-in and read-only. Existing real `LocalSession` login, trade hooks, save queues and direct R64 owner replacement are not protected by R73/R74. Malicious or non-cooperative file writes can bypass the OS-lock protocol.
3. **No power-loss durable proof.** R16 individual atomic file replacement, R72 phase evidence and the R73 account blocker are not one hardware power-loss-atomic transaction. Parent-directory fsync and storage faults remain out of scope.
4. **No universal absence-of-writers proof.** Acquiring R60 establishes only that no cooperating operation currently owns that lock; the system does not prove all processes, threads or external save paths are quiescent. It cannot turn the evidence into a safe retention/retirement proof.
5. **No authorization or user identity.** A recorded owner UUID/epoch is a test-controlled token, not authenticated account ownership.
6. **No unbounded or active repair.** On corrupt or ambiguous marker names/stages, R74 refuses to conclude consistency. It never guesses abandoned files from timestamps, reads staging payloads or infers state from canceled futures.
7. **No R57/R59 trade quarantine release.** An indexed PREPARED pair always blocks, irrespective of R72 ACK or R64 RELEASED.

## R75 priority

Perform an offline **reconciliation gap and retirement-safety proof**: analyze the complete cooperating writer admission set and crash points; show why a matching ACK and a free OS lock are not enough to retire R73 while direct R64 replacement or non-R73 producers exist. Build negative tests for post-review late writes, external uncooperative publishers, and cross-JVM inspection/owner changes. Do not add automatic marker deletion or login acceptance until a durable, authenticated, all-writers-quiescent retirement protocol is independently verified.

**R74 DRAFT, stacked on R73, UNMERGED.**
