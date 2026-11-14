# R56 — deterministic two-account torn trade-save restart evidence

## Why the next step cannot be just another `save()` call

R55 now rolls back **in-memory server state** if a synchronous trade packet publication fails. A fully successful trade still invokes two independent `TRADE_COMMIT` save callbacks. In the live LocalSession integration, these callbacks invoke `saveAccountQuiet`, which captures and enqueues a distinct `PlayerSnapshot` per player. Errors during capture/enqueue are logged, not propagated as a two-party commit failure, and each eventual `FilePlayerRepository.save` publishes one account independently with `AtomicAccountPublisher`.

An atomic filesystem replace of account A is not atomic with an independent replace of account B. If only one replacement reaches disk before interruption, BOTH canonical files can still load cleanly, with incompatible pre/post-trade contents. There is no shared durable trade commit record to tell login that they belong to an incomplete two-account transaction.

## Exact isolated R56 replay (evidence-only)

`R56TradeSplitSaveRestartEvidenceTest` uses real `TradeService`, `BankState`, `PlayerSnapshotCodec`, `FilePlayerRepository`, `AtomicAccountPublisher` and disposable temp files; it does not use the owner's game profile, network sockets, graphical client or local running JVMs. It first saves 1,000 coins owned by A and two sharks owned by B, completes a legitimate one-coin-for-one-shark in-memory trade, then simulates four possible interrupted persistence points by writing zero, one or both final account snapshots.

| Durable files written before simulated restart | A after reload | B after reload | Combined conservation |
| --- | --- | --- | --- |
| Neither updated | 1,000 coins / 0 sharks | 0 coins / 2 sharks | Preserved (old state) |
| Only A updated | 999 coins / 1 shark | 0 coins / 2 sharks | **1 coin lost; 1 shark duplicated** |
| Only B updated | 1,000 coins / 0 sharks | 1 coin / 1 shark | **1 coin duplicated; 1 shark lost** |
| Both updated | 999 coins / 1 shark | 1 coin / 1 shark | Preserved (new state) |

Both torn cases **load successfully as valid individual canonical account snapshots**. Passing this R56 test means the known vulnerability is reproducible; it does **not** mean it is resolved.

## Connected Windows validation, 2026-11-14

- JDK21 Gradle `jar testClasses verifyJava11Bytecode --offline --no-daemon`: **BUILD SUCCESSFUL**, 2,179 Java 11-compatible class files (major 55).
- Four-case `R56_TRADE_SPLIT_SAVE_RESTART_EVIDENCE_PASS` with unchanged/complete controls and both asymmetric single-save outcomes: **PASS**.
- Only fresh private test directories created by the fixture are recursively cleaned; no real accounts or ambiguous stage files touched. No host GUI, network listener, Windows Sandbox, firewall or optional-feature changes.

## Required design before enabling automatic recovery

1. **Durable pair intent:** assign each trade a stable transaction ID, both exact account identities and bounded pre/post snapshot hashes; record a validated, forced intent before any durable unilateral post-state becomes visible. Do not depend solely on each account's `saved.at` timestamp.
2. **Paired admission and generation fence:** verify current WorldPlayer ownership, expected account generations, authorized profiles, both participant writers, and expected save revisions while holding ordered ownership locks. Do not let autosaves or logout saves race ahead of the pair protocol.
3. **Recovery gate on both logins:** a reconnect of **either participant** must consult the shared transaction record *before* accepting their canonical snapshot/defaults or registering into World. If the pair record is incomplete, block both or perform a fully verified, deterministic and idempotent recovery. A journal left by an active owner must not be confused with abandoned crash evidence.
4. **Explicit, testable commit protocol:** stage both bounded/validated snapshots safely, publish with hash/ownership checks, and mark completion only when replay/rollback rules are unambiguous; a journal file's mere presence or age is not proof of abandonment. Never auto-delete or auto-promote untrusted stage bytes.
5. **Crash matrix:** inject interruption before intent, after intent, after A publish, after B publish, before/after committed marker, during replay, and amid concurrent login/autosave. Test fresh JVM restarts, Windows and Linux filesystem semantics, symlink/reparse/lock/path abuse, and idempotent recovery. Clearly separate software fault injection from real power-loss durability and parent-directory fsync.

Until this protocol exists, the safe claim is **individual-account atomic replace plus in-memory rollback for synchronous packet publication errors** — not an atomic two-account trade. R56 introduces NO auto-healing logic and makes NO production-side semantic changes.

R56 is a stacked DRAFT on R55 and is not merged.
