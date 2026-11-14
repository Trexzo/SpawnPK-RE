# R57 — Offline paired-trade intent record and fail-closed review

## Purpose

R56 reproduced a real cross-account persistence problem: a completed trade uses two independent atomic account replacements. A crash after only one replacement can leave both canonical files individually readable while their combined inventories have lost or duplicated items.

R57 implements the **first independent evidence component** for a future paired commit. It is intentionally **not connected** to production `TradeService`, `LocalSession`, `FilePlayerRepository` login/save admission, or automatic account recovery.

## New code

- `R57TradePairJournal.writePrepared(...)`: accepts a unique transaction UUID, two distinct ordered account names, and each party's immutable before/after `PlayerSnapshot`. Writes an intent record containing only account identities, snapshot **SHA-256 digests**, format, transaction ID and PREPARED phase. The record contains **no raw gameplay state**. Uses the existing R16 staged forced-file / required atomic move publisher in a private test directory; rejects a pre-existing intent. This is an offline test seam, **not** an immutable multi-process transaction log.
- `R57TradePairJournal.review(...)`: reads a bounded, exact-key, versioned intent. Requires a regular, non-symlink record; refuses ambiguous `.r16-*.stage` evidence without reading, deleting or promoting it; checks identity, canonical UUID and hexadecimal hashes; calculates stable length-prefixed UTF-8 snapshot digests independent of `Properties` ordering or per-save timestamps.
- Returns one of `BOTH_BEFORE`, `ONLY_A_AFTER`, `ONLY_B_AFTER`, `BOTH_AFTER` or `UNRECOGNIZED`. **Every result has `loginAllowed=false`**, because this component is not a validated completion/recovery protocol. A malformed journal throws an `IOException`; it does not become account defaults or a clean commit.
- `R57TradePairJournalReviewTest`: exercises a real `TradeService` coin/shark exchange with `FilePlayerRepository` before/after snapshots, then uses the four R56 split-save checkpoints. Each test loads the canonical account snapshots from disk before reviewing. It checks that reading/reviewing does not modify either account, that duplicate intent writes are refused, that unknown account contents do not match either permitted state, and that malformed or ambiguous staged intent evidence is rejected.

## Windows validation — 2026-11-14

- JDK21 + Gradle `jar testClasses verifyJava11Bytecode --offline --no-daemon`: **BUILD SUCCESSFUL**, Java11 classfile major 55, **2,185** class files.
- `R57_PAIRED_TRADE_INTENT_OFFLINE_REVIEW_PASS`, four cases and negative controls, exit 0.

## Threat and crash-consistency boundaries

This is **evidence**, not operational protection:

1. It is not invoked before any actual login or trade/save. An R56-style torn account pair is still possible in live LocalLab persistence.
2. `AtomicAccountPublisher` forces its stage and uses an atomic rename but does not prove power-loss durability of the parent directory entry. Presence of a record does not imply the record survived an actual power interruption.
3. The immutable-intent check is single-process/read-before-write only; concurrent, malicious or non-cooperative processes and journal path races are not yet fenced.
4. Snapshot hashes ensure reproducible matching, **not authenticated integrity** against an attacker who can rewrite the journal. No signing, HMAC or trusted ledger is implemented.
5. `BOTH_AFTER` does not mean committed: neither durable commit marker nor both-account generation/owner fence exists. R57 never authorizes recovery, deletion, stage adoption, login or rollback.
6. A real pair protocol still needs a protected location/index for discovering all pending pair intents from **either** account, locked ownership and generation admission, a durable coordinator phase transition, coordinated save scheduling, idempotent crash/restart replay, and negative controls for symlinks, reparse points, corrupt/missing peers and concurrent autosaves.

No graphical v308 client was launched, no Windows optional features/firewall modified, no production accounts opened, and no existing owner JVM terminated.

## Next acceptance boundary

Before enabling any R57-derived live behavior, build a **read-only login preflight** for both participants that fails closed on unresolved journal evidence, with no automatic recovery. Then independently prove that pending trade intent cannot be bypassed through either account login, default-account initialization, autosave, or final logout save. After that, develop a paired durable commit and idempotent replay protocol under explicit crash-point tests.

**R57: DRAFT, STACKED ON R56, UNMERGED until independent CI.**
