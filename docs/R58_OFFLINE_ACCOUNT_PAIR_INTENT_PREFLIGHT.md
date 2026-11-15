# R58 — Read-only paired-intent account preflight (offline only)

## Starting point

R55 repaired synchronous in-memory trade publication rollback. R56 proved that separately saved valid account snapshots can contain one-sided trade updates and lose or duplicate items after a restart. R57 introduced a forced/atomically published **offline** PREPARED intent with the before/after account snapshot hashes, and a four-way read-only reviewer.

R58 adds an **offline account-name lookup** against a dedicated intent directory so that an unresolved pair involving *either* participant can be identified without opening their account files. It does **not** connect the check to live login or account saving and cannot yet protect live accounts.

## New bounded preflight

`R58TradePairLoginPreflight.inspect(directory, username)` enumerates one explicitly supplied journal directory with a cap of 256 entries. Every entry must have a canonical filename `trade-r57-<UUID>.properties`, and its declared transaction ID must match that filename. It delegates strict bounded intent parsing and participant validation to R57's existing parser. The parser checks identities, phase, digests, regular-file type and stage ambiguity.

The inspection result is one of:

- `BLOCK_PENDING_PAIR_INTENT`: an unresolved PREPARED intent names the requested account as either participant. A matching transaction ID is reported for diagnosis only; it **does not authorize recovery or snapshot replacement**.
- `BLOCK_INDETERMINATE_JOURNAL`: the directory is absent or unreadable, is a symlink or not a directory, contains unexpected names, staged records, corrupt/missing intent contents, file/manifest transaction mismatches, or exceeds the bounded entry limit. This is a **global fail-closed** result for the directory; no age or lock-file heuristic authorizes cleanup.
- `NO_PENDING_PAIR_EVIDENCE`: a validated, complete scan of this supplied directory finds no PREPARED intent naming the account. This result only clears this **single journal-specific check**; it is **not full login authorization** and does not prove another writer or crash did not create intent evidence elsewhere.

The `canProceedToOtherLoginChecks()` convenience method is false for both block results and true only for the narrow no-evidence result. It is not used by `LocalSession` or the live login pipeline. `automaticRecoveryAttempted()` and `accountSnapshotsOpened()` remain false. Review is observational; it never reads, modifies, promotes, deletes or renames account snapshots, journal files or ambiguous stages.

## Verified Windows acceptance — November 15, 2026

- Windows JDK21 / Gradle `jar testClasses verifyJava11Bytecode --offline --no-daemon`: **BUILD SUCCESSFUL**, major 55 (Java11 compatible), **2,190** classfiles.
- `R58_READ_ONLY_PAIR_LOGIN_PREFLIGHT_PASS`: both participants blocked by one intent, unrelated account clear under a valid directory, no record/record-without-peer edge cases, second pair detection, no stage consumption, malformed candidate and filename/transaction-ID mismatch fail closed, missing directory and invalid user blocked, repeated review idempotent, account/journal bytes preserved.
- Inherited `R57TradePairJournalReviewTest`, `R56TradeSplitSaveRestartEvidenceTest`, and `R55TradePublicationRollbackTest`: all **PASS**.
- Isolated test-owned directories only. No original graphical v308 client, real profiles, private host account files, Windows optional-feature changes, firewall changes or running owner Java processes touched.

## Boundaries before live enforcement

1. **No live integration.** Production `TradeService` does not write R57 intents, and `LocalSession` / `FilePlayerRepository` do not invoke R58 on login. The new preflight cannot block a real login. Do **not** infer otherwise from a passing offline test.
2. **Directory scope is not authoritative.** An empty directory does not prove all possible journal writers use it. A shared durable registry, account-to-intent index and identity/ownership registration protocol are required. Scanning and writing are not currently synchronized, so a newly written intent after inspection can race ahead of a login.
3. **No durable commit** or power-loss guarantee. R57 uses an atomic rename of a forced stage, not a documented parent-directory fsync guarantee. There is no paired durable commit marker or safe replay/rollback algorithm.
4. **No trusted author/authenticated record.** Hash matching is not a signature. A malicious filesystem writer can forge or replace a journal; cross-process filesystem locking and tamper resistance need separate review.
5. **No automatic recovery.** Even both post-state hashes present are not enough to authorize cleanup, login or a final save without an authenticated committed state and idempotent protocol. Legitimate active writers may temporarily leave stages; R58 conservatively blocks instead of guessing.
6. **No reader-writer atomic snapshot.** A directory scan is observational and may see concurrent changes. Results do not authorize game mutations, default-account creation, autosave or logout save.

## Next safe milestone

Implement an **independent offline account-to-pair intent index** and versioned lookup/publish sequencing so the same authority can prove both participant registrations before any canonical profile publication. Add fault injection for the registration gap, concurrent intent writers, unreadable index and stale generations. Only after a reliable shared-authority admission and persistence protocol is proven can live login integration or automatic roll-forward be considered.

**R58 is DRAFT, STACKED ON R57, UNMERGED pending independent CI.**
