# R63 — Cooperative offline account generation and save admission fence

## Why R63 exists

R56 demonstrated split-save item loss/duplication during independently persisted trades. R57–R62 added a PREPARED intent, revisioned indexed participants, cross-process file locking, a COMMIT/roll-forward simulator and a separate verified offline completion certificate. The completion proof deliberately **does not release quarantined accounts**.

The missing boundary is ordinary account persistence: unrelated autosaves, disconnect saves and reconnects could otherwise race a recovery or publish an old profile after it had advanced. R63 introduces a **standalone offline account-specific generation ledger and read/modify/write fence** that uses the SAME cooperative R60 OS lock as indexed trade registration and R61/R62 recovery checks.

**No live hooks were added. Existing LocalSession / TradeService / WorldPlayerPersistence / logout save and account login semantics are unchanged.** The R56 live two-account persistence risk therefore remains.

## Generation record

Each explicitly registered test-owned existing account has a separate strict-schema record in a dedicated **state directory**, distinct from the journal directory and canonical account directory:

`<account>.r63-generation.properties`

The bounded record stores an account name, generation (positive monotonic 64-bit integer) and SHA-256 of its canonical `PlayerSnapshot`. It stores neither inventory contents nor credentials.

`initializeExisting(journal, accounts, states, account)` acquires R60's exclusive cross-JVM lock. It requires an existing canonical profile, a known and complete R59 journal index with **no pending trade for that player**, a valid account identity and **no pre-existing ledger or staged ledger ambiguity**. It does not create default accounts or overwrite an existing ledger. Initializes generation 1 via R16 forced-stage/required atomic file replace.

`inspect(...)` also acquires R60's exclusive lock. It validates the journal index, detects any indexed pending pair, opens the strict generation record, loads an existing canonical account and verifies the exact persisted snapshot hash. Results:

- `READY_FOR_OTHER_OFFLINE_CHECKS`: canonical snapshot, ledger and journal are consistent. Returns an offline **Token** containing account name, generation, snapshot hash and current journal revision. This is **not authorization to log in**.
- `BLOCK_PENDING_PAIR`: the player has an unresolved indexed PREPARED trade. No token, no account IO authorized.
- `BLOCK_INDETERMINATE`: missing, corrupt, staged or inconsistent generation/account/journal evidence; ambiguous OS lock; foreign/altered snapshot; or a missing ledger. No token, no implicit reset or recovery.

`saveIfCurrent(..., token, proposed, injectFault)` obtains the SAME R60 OS lock, then re-runs `inspectUnderLock`. It refuses a stale generation, old canonical digest, superseded R59 journal revision, pending pair, foreign username, incorrect snapshot version, unchanged save or generation overflow. Only after the current token matches exactly does it atomically publish the proposed canonical account via the existing `FilePlayerRepository`, reload and verify the result, then publish the incremented generation record.

## Crash safety contract

The two writes (canonical account then generation ledger) are **NOT a single atomic transaction**. R63's correctness rule is specifically **fail closed** on interruption, not automatic recovery:

- If the process stops **before** the canonical file publishes, the prior account and generation remain consistent (subject to existing R16 stage ambiguity).
- If it stops **after** the canonical publication but **before** the ledger publication, the old ledger's digest no longer matches the new account snapshot. On restart, `inspect` refuses admission; a token from the old generation cannot authorize a save. R63 never guesses a new generation or overwrites the account.
- If the generation ledger's forced-stage publication fails or leaves ambiguous staging, R16/R63 refuse a guessed recovery and preserve available evidence.
- A different cooperating process cannot mutate canonical account/ledger while an R60 registration, offline replay, completion or save owns the exclusive lock.

There is **no physical power-loss durability proof**, parent-directory fsync guarantee, automatic ledger repair, cross-process identity authentication, trusted session generation mechanism or non-cooperative writer protection. Canonical login and autosave in the actual server remain outside this lock.

## Connected Windows acceptance — November 15, 2026

- Isolated JDK21 `jar testClasses verifyJava11Bytecode --offline --no-daemon`: **BUILD SUCCESSFUL**, 2,227 Java 11-compatible classes (major 55).
- `R63_OFFLINE_GENERATION_SAVE_FENCE_PASS`: initial ledger and generation advancement under the R60 lock, stale generation refusal, superseded journal-index revision refusal, pending-pair save denial, unrelated account continued admission with refreshed index token.
- **Separate live child JVM** holds a real R60 operating-system lock while the test attempts a read/save: both refuse, and no account bytes change.
- A direct non-cooperative account overwrite generates a canonical/ledger digest mismatch; inspection refuses rather than overwriting the unexpected snapshot.
- Ambiguous generation staging evidence is preserved and refused, never automatically cleaned.
- **Separate test-owned child JVM** saves one account, then calls `Runtime.halt(93)` before updating its generation ledger. Parent confirms the canonical file changed, but subsequent inspection and stale-token saves refuse, preserving the exact crash evidence. This is abrupt JVM exit only, NOT physical power loss.
- Inherited R62 completion, R61 fresh-JVM replay, R60 cooperative lock, R59 index and R17 crash owner recovery: all PASS in the isolated Windows worktree.
- New Linux/Windows GitHub matrix covers R55–R63 and normal trade-flow regressions.

No real accounts were opened, no real game client launched, no sockets created by the new R63 test, no running owner JVM terminated and no host firewall/system configuration changed.

## Necessary next boundary

R64 should create a **test-only shared session generation / ownership protocol**, including login acquisition, autosave, logout save, explicit abort, and replay under a single authority. It must prove a stale logged-out session can never submit a late save after a new session obtains ownership, and that a failed/crashed save leaves both journal and account evidence preserved. Only later may the live LocalSession and persistence pipeline opt in behind a gated setting.

**R63: DRAFT, STACKED ON R62, UNMERGED. Offline only.**
