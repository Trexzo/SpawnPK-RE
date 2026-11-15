# R64 — Offline session ownership, epoch takeover and late-save refusal

## Starting point

R63 introduced a *cooperative offline* account generation ledger guarded by the R60 cross-process OS lock. An account save requires a token binding the current snapshot hash, generation and R59 journal revision. That does not by itself distinguish two client sessions whose cached account state might be identical. A late logout or autosave callback from a superseded session must not overwrite the profile belonging to a new owner.

R64 introduces an **isolated offline session-owner record** and composes owner checks with the existing R63 generation save under *one* R60 OS lock. This is a separate source-level prototype and is **not wired to real login, LocalSession, TradeService, WorldPlayerPersistence or autosave**.

## Protocol

Each existing, explicitly registered R63 account can have one `<account>.r64-session.properties` record in a distinct test-controlled sessions directory. The bounded exact-schema file stores:

- canonical account name and owner UUID
- positive monotonically increasing owner epoch
- phase `ACTIVE` or `RELEASED`
- current R63 account generation, exact snapshot SHA-256 and R59 journal revision

Only the R64 test-owned functions consume these records:

- `claimFirst` requires an **existing and current** R63 account, fully validated journal with no pending pair and no existing or ambiguous staged owner record. It creates owner epoch 1, phase ACTIVE. It never creates a default account.
- `replaceOwner` requires an explicitly provided **current prior token**, revalidates R63's snapshot, generation and journal revision under the lock, and atomically publishes a **different UUID with incremented epoch**. It can deliberately supersede an ACTIVE owner to reproduce stale disconnected sessions, or reactivate a RELEASED owner. This is **not authentication or login permission**: any test code able to read the prior record and supply the token could request a takeover. A future server-side session identity authority must own this operation.
- `saveOwned` acquires the same R60 exclusive OS lock, reloads the owner record and requires exact UUID, epoch, phase ACTIVE, account generation, digest and journal revision matches. It then invokes R63's `saveIfCurrentUnderLock` under that **same held lock** (no nested/reentrant OS lock), verifies the new generation, and publishes a new session record binding the new snapshot. An old owner, stale generation, stale journal revision or released phase fails before account publication.
- `releaseOwner` is a locked, token-exact logout fence. It changes the record to RELEASED, preserving the generation and owner epoch so any delayed save/logout from the prior ACTIVE token is refused. It does **not** assume all unrelated asynchronous callbacks have stopped in the real server.
- `inspect` obtains R60 lock, verifies R63's ledger and journal and compares the owner record with the account. Results `READY_FOR_OTHER_OFFLINE_CHECKS`, `BLOCK_RELEASED`, `BLOCK_PENDING_PAIR`, `BLOCK_INDETERMINATE`. **All return `loginAuthorized()==false`**; an offline READY result is not a real session admission credential.

R63's existing public offline entrypoints retain their behavior; R64 adds package-private R63 `inspectUnderLock` and `saveIfCurrentUnderLock` seams for composition with R60 held by the outer owner protocol. The original R63 independent tests must remain green.

## Fail-closed interrupted publication

A save under R64 publishes three *individually atomic* files in this order while the cooperating lock is held:

1. canonical profile (R16 account publisher)
2. R63 account generation ledger
3. R64 session-owner record

**These are NOT one atomic multi-file commit.** The protocol intentionally blocks instead of guessing during a crash:

- After 1 but before 2, R63's ledger does not match the canonical account and refuses further saves/inspection.
- After 2 but before 3, R63 is current, but R64's owner record still points at an old account generation/hash. R64 inspection, save and owner replacement refuse; no automatic adoption or rollback.
- Ambiguous R16 stages on canonical, ledger or owner record block rather than being deleted or classified as stale by timestamp.
- R59 journal revision advances or an indexed PREPARED pair appears: an old owner token cannot save and journal-pending players are refused.

R64 records are not cryptographically authenticated. UUIDs are identity markers for a controlled test, not proofs of an authorized user. Direct R63 APIs and other non-cooperative writers can bypass R64, so **no live security or protection claim** is made.

## Isolated Windows acceptance — November 15, 2026

- JDK 21 `jar testClasses verifyJava11Bytecode --offline --no-daemon` **PASS**: 2,235 Java 11-compatible major-55 classes.
- `R64_OFFLINE_SESSION_OWNERSHIP_FENCE_PASS`: a genuine second JVM explicitly takes over the owner record under R60, advances the epoch and changes the owner UUID. A separate stale JVM attempts to save after takeover and is refused without replacing canonical bytes.
- Repeated late save and logout from the original owner fail. The new owner saves generation 3, logs out, is denied late save, and an explicitly supplied RELEASED token permits a third session epoch.
- A session stage file is preserved and denies all R64 access. Another real JVM holding R60's OS lock causes both inspection and saving to refuse. Superseded R59 journal revision and a new pending pair deny R64 session activity.
- Separate test-owned child JVM performs the canonical and R63 ledger save then invokes `Runtime.halt(94)` before the R64 owner record advances. Parent verifies R63 generation 2 is consistent but R64 remains indeterminate, refusing save/replacement and preserving the canonical byte evidence.
- Inherited R63, R62, R61, R60 and original R17 crashed-owner lease regression suites: **PASS** on the disposable connected Windows worktree. Cross-platform GitHub workflow includes R55–R64, R17 and ordinary trade flow.

The tests do not touch real account files or launch the original game client. They use independent temporary account roots and only stop their own child JVMs. No host firewall, Java runtime or optional Windows feature is changed.

## Remaining gates before live integration

1. **No authorization source:** the test-only owner UUID/expected prior token does not authenticate a game user, and `replaceOwner` deliberately models explicit takeover, not secure reconnect or session login.
2. **No production admission/save hook:** actual `LocalSession`, `WorldPlayerPersistence`, direct R63 saving, TradeService and logout paths do not participate. Ordinary in-game late saves can still race outside the new protocol.
3. **No multi-file power-loss durability:** R16 forces and atomically replaces each single file, but this does not guarantee directory fsync or a durable triple-file transaction on all platforms. `Runtime.halt` models process failure only.
4. **No safe ledger/owner repair or journal retirement:** interrupted writes remain blocked; R62's verified COMMIT certificate still does not release the R59 pending intent or permit login.
5. **No end-to-end session generation on protocol packets:** a future real implementation must bind authenticated login, active world actor identity, every accepted input, account mutation, autosave, logout and trade to one authoritative generation, and test concurrency with actual LocalLab sessions.
6. **A non-cooperative writer can bypass the OS lock.** The protocol is only proven for callers using the R60 coordinator and the R64 wrapper. No adversarial filesystem authentication or hardware power interruption is proved.

## Next safe milestone — R65

Create a **test-only session dispatcher / persistence boundary** that binds a simulated login, mutation dispatch and scheduled autosave callback to the same R64 token. Reproduce the precise race: old session captures a save callback, new owner takes over, old callback executes afterward and must be rejected **before writing any canonical or ledger bytes**. Test the new owner’s continued progress, logout fencing and a deliberately failed save without live sockets. Do not activate production hooks until full source integration and isolation are audited.

**R64 is DRAFT, STACKED ON R63, UNMERGED.**
