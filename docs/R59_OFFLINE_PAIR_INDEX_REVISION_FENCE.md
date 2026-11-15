# R59 — Offline whole-directory paired-trade index and revision fence

## Context and purpose

R55 handles synchronous packet-publication rollback in memory. R56 confirms that independently published valid account snapshots can nonetheless lose or duplicate items after a crash between the two saves. R57 introduced an offline immutable PREPARED paired trade intent with SHA-256 before/after snapshot hashes. R58 introduced an offline directory-scanning preflight that can detect either participant.

R59 adds a **single versioned, atomically replaced account-to-transaction index** for a deliberately scoped test directory. It refuses to treat an account as clear if a durable manifest exists but is missing from the index. This is a **prototype of a registry**, NOT yet the authoritative registry for a live server.

## Implementation

`R59TradePairIntentIndex` stores a strict-versioned `paired-intents.index.properties` record. Each entry holds a canonical transaction UUID and two validated account names, not inventory or snapshot data. The file includes a monotonically increasing revision and bounded record count (at most 256).

`publishRevision(root, expectedRevision, transactionIds)`:

- Reads the current index if present and rejects an unexpected revision. The revision guard is single-JVM synchronized only: **this is not cross-process atomic compare-and-swap**.
- Requires every requested transaction to have a parseable R57 PREPARED journal whose transaction UUID matches its canonical filename.
- Refuses duplicate IDs and removal of an indexed unresolved transaction. There is no recovery/commit protocol yet to safely retire an intent.
- Before publication, audits the entire **explicitly supplied dedicated directory** and rejects unknown entries, orphan stages and missing or unindexed manifests. Writes via the existing R16 forced-stage and required atomic-rename publisher, then reports the incremented revision. There is no non-atomic move fallback.

`inspect(root, account)`:

- Requires a real bounded index file with a known schema, exact expected keys, valid canonical UUIDs, ordered account names and sane entry count. Missing or malformed index is **global fail-closed**, even when the directory is empty.
- Checks each indexed manifest against the same directory and validates the manifest's declared UUID and both participant names. Unindexed, missing, staged, corrupt, unexpected or mismatched entries fail closed for **all** accounts, including unrelated users.
- Returns `BLOCK_INDEXED_PAIR` with matching UUIDs for either participant, `BLOCK_INDETERMINATE_INDEX` for ambiguity, or `NO_INDEXED_PENDING_PAIR` for an unrelated account only after a complete validated scan. The clear result is **not authorization to log in**.
- Does not open, rewrite or delete any canonical account snapshot. Does not heal, promote or delete any journal/ambiguous staging file.

## Windows acceptance — November 15, 2026

- `jar testClasses verifyJava11Bytecode --offline --no-daemon`: **BUILD SUCCESSFUL**, 2,197 Java11 bytecode-major 55 classfiles.
- `R59_OFFLINE_PAIR_INDEX_REVISION_FENCE_PASS`: both participants in multiple pairs blocked; absent index fail-closed; incomplete index publication refused; stale revision refused; removal of pending ID denied; new unindexed journal blocks *all* inspection; addition registered at revision 3; journal/index stages preserved; foreign files, malformed index, tampered or missing manifest fail closed; original test-owned canonical profiles and journal bytes unchanged.
- Inherited R58 preflight, R57 journal review, R56 split-save restart reproduction and R55 in-memory rollback: all **PASS**.
- The fixture uses its own disposable directories. No live accounts, original GUI, network game sessions, host optional features, firewall or unrelated Java processes are altered.

## Critical limits — no live deployment

1. **No live writer integration or login hook.** Real `TradeService` does not publish R57 manifests, does not update the R59 index, and `LocalSession` does not consult it. The R56 two-account crash loss/duplication window remains open in production.
2. **No cross-process registry writer ownership.** The revision check is read-before-write with a Java method-level lock. Two non-cooperating processes can each read the same revision and replace the file; the later writer can lose earlier registrations. The manifest completeness audit should cause a later inspection to fail closed when files remain, but cannot make concurrent registration atomic.
3. **No scan/update linearizability.** A new intent can appear between enumeration and a login; a directory scan cannot be used as a real admission gate without a shared writer/reader lock and ownership protocol.
4. **No physical power-loss durability guarantee.** The R16 publisher forces its staging file and requires atomic replacement; parent directory fsync and storage hardware behavior remain outside the verified contract.
5. **No authenticated or durable completion.** The registry and intent hashes are not signatures. Even `BOTH_AFTER` may be unresolved, and intents are never automatically retired. Recovery, staged-data adoption and live login are not authorized.
6. **No complete path-integrity guarantee under adversarial concurrent filesystem mutation.** Existing R16/51 symlink and ambiguous-stage checks are exercised, but stronger no-follow identity, lock ownership, index generations, account generation fencing and controlled test-owned crash replay are needed.

## Next safe milestone (R60)

Implement an offline multi-writer registration ownership protocol and inject controlled contention/crashes around manifest creation, index publish, and concurrent lookup. Test that a writer cannot silently replace a competing revision, and that lookup never returns a false-clear result during a simulated registration race. This must precede any activation of real login gates, saves or automatic roll-forward.

**R59 is a DRAFT STACKED on R58; no merges and no live hooks.**
