# R60 — Cooperative cross-process pair-index locking (offline only)

## Starting point

R56 proves that one-sided account-file saves after a completed player trade can silently lose or duplicate items. R57–R59 developed offline-only PREPARED intent records, two-account lookup, and a revisioned account-to-intent index that fails closed when any journal is missing, unindexed, malformed or inconsistent.

R59's revision check was only synchronized inside a single Java process. Two separate JVMs could read the same revision and publish competing registries. Likewise, a journal scan could return a false "no pending pair" result while a cooperating writer was between manifest creation and index publication.

R60 addresses **only cooperative offline multi-process coordination** through a real operating-system `FileLock`.

## Implementation

`R60TradePairCoordinator` exposes three scoped operations:

- `initializeEmpty(root)`: one-time offline initialization of an explicitly supplied, existing, empty journal directory. It creates a stable lock sentinel adjacent to, **not inside**, the journal directory, acquires an exclusive operating-system lock, and publishes R59's empty index at revision 1. A pre-existing sentinel or ambiguous journal causes refusal; no directory or account is auto-cleaned.
- `registerPrepared(root, expectedRevision, priorIds, id, beforeA, afterA, beforeB, afterB)`: acquires the same exclusive OS lock. Under it, R59 verifies neither participant already has an unresolved pair and both accounts see the expected revision. The coordinator then writes the R57 PREPARED manifest and publishes R59's complete new registry. A stale revision or existing participant refuses *before* writing new manifest data. If manifest creation succeeds but index publication fails, it **preserves** the unindexed manifest, which makes subsequent R59 checks globally fail closed rather than silently allowing an inconsistent save.
- `inspect(root, account)`: obtains the **same exclusive OS lock** for a read-side inspection, then delegates to R59's exact-schema index and manifest completeness checks. If the lock is busy, absent, malformed, unreadable, overlapping or symlinked, returns `BLOCK_INDETERMINATE_INDEX`, not login authorization. A clear result only clears this *offline index check*.

The lock sentinel is named `.<journal-folder-name>.r60-pair-coordinator.lock` in the journal's parent directory. The process opens it without following symbolic links, requires it to be a zero-byte regular file, and checks stable filesystem identity where available. The OS lock is held across the full critical section and released by closing the channel. **The lock file's mere existence, age or timestamp does not prove a lock is currently held.** Neither a stale lock file nor an unindexed manifest is silently deleted.

## Windows verification — November 15, 2026

- JDK 21, `jar testClasses verifyJava11Bytecode --offline --no-daemon`: **BUILD SUCCESSFUL**, 2,204 Java 11-compatible class files, major 55.
- `R60_COOPERATIVE_CROSS_JVM_PAIR_LOCK_PASS`: test-owned second Java process acquires an actual operating-system lock. First process must deny both index reads and registration while lock is held; no partial journal may be written by the refused registration.
- Once the lock is released, an index revision 2 registration succeeds and **both participant names** resolve to the same pending transaction. A stale revision is rejected before writing a new intent.
- Independent worker acquires lock and **writes another PREPARED manifest but deliberately does not update the R59 index**. The parent must deny reads during the live lock and after the worker exits, because the orphan manifest makes the index incomplete. The test verifies the orphan survives and further registration fails, with **no recovery, cleanup, or account state changes**.
- Inherited R59, R58, R57, R56, R55 regression suites: all **PASS** on the isolated connected Windows worktree. The normal trade-flow test is included in GitHub CI.
- The worker is a test-owned child JVM, not the existing original host Java processes. The test creates no game sockets, profile mutations or host configuration changes.

## Critical limitations

1. **Offline only.** Real `LocalSession`, `TradeService`, account loads/saves and autosaves do **not** call R60. Pre-existing R59 direct methods are not automatically locked. No live login protection or two-account durability is claimed.
2. **Cooperative writers only.** A second JVM using the R60 coordinator respects the lock. A non-cooperative process writing the same manifest/index or replacing the sentinel can bypass this protocol. A lock file's existence is not lock ownership.
3. **No authenticated identity or durable player generations.** R60 coordinates test record publication, not actual account/world generations, remote user authentication, or concurrent real login/logout.
4. **No physical power-loss atomicity.** Individual R16 forced-stage and atomic-move publication remains subject to parent-directory fsync/storage guarantees. The lock is released on process termination; it is not a durable transaction commit marker.
5. **An incomplete pair remains blocked.** R60 deliberately has no roll-forward/rollback, commit-marker transition or manual repair operation. A PREPARED manifest without index, or an already indexed unresolved pair, cannot be retired without a separately proved idempotent recovery protocol.
6. **No guarantee against filesystem adversaries or external races.** The locks and file validation make cooperative cross-process tests deterministic but do not establish a secure adversarial multi-tenant filesystem authority.

## Next safe milestone

Define a controlled offline **paired commit/replay simulator** tied to a registered transaction, with immutable before/after snapshots, explicit completion phase, deterministic idempotent restart behavior, and fault injection at each journal/index/account publication boundary. Require tests against a fresh JVM for both accounts. Only after a verified protocol exists should live account admission and persistence be considered.

**R60 is DRAFT, STACKED ON R59, UNMERGED.**
