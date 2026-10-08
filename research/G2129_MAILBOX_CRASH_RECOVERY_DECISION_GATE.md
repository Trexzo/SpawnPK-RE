# G21.29 — Mailbox PREPARED crash-recovery decision boundary (no grant)

**Exact certified parent:** G21.28 `0d10f2758251092c38120b176d8777588f21c718`, workflow [37796386093](https://github.com/Trexzo/SpawnPK-RE/actions/runs/37796386093), 327 focused Java 11 tests.

## Source audit and the hard veto

- G21.22 only records a `PREPARED_NO_GRANT` intent. It is **not** a durable inventory transfer or an idempotent replay authority.
- G21.23 `StrictDurablePlayerSnapshotWriter.Receipt` contains **only** `account`, filesystem `file`, and writer `authority`. It contains **no canonical account snapshot digest**, idempotency key, generation, transaction sequence, or verified postimage proof. It is a receipt for **one strict file-operation result**, not a Mailbox transaction. A real receipt for the PREPARED snapshot can coexist with subsequently visible hypothetical bytes. Comparing only account/path would be unsound.
- G21.24 serializes the opt-in PREPARED strict writer with autosaves and fences stale captured saves; G21.27 reserves the account against conflicting **new** writes. G21.28 waits for earlier FIFO tasks and reads one complete account snapshot after the drain. **That observation is not persistent transaction authority.** An exact hypothetical postimage may be visible after an ambiguous post-rename directory-force failure.
- G21.27 reservation ownership is **in memory**. It cannot be recreated merely from a restarted `PlayerSnapshot`; a restarted process does not regain its exclusive token. No process restart automatic grant/replay/rollback is safe at this milestone.

## Implemented read-only decision

`MailboxPreparedRecoveryDecision.assess` accepts the exact G21.27 owner/generation reservation, exact G21.25 immutable account PREPARED/hypothetical proposal, completed G21.28 same-FIFO observation, and an optional *genuine* G21.23 `Receipt`. It recomputes the live proposal under the owner mutation lock and rejects a retired generation, changed full snapshot, replaced message identity or mismatching observation.

It returns one typed classification:

| Disk and evidence | Decision | Grant / redo / live release |
| --- | --- | --- |
| Exact complete PREPARED account | `EXACT_PREPARED_UNCLAIMED` | **No** (separate G21.28 independently checked cancellation may be attempted) |
| Exact hypothetical, no receipt | `HYPOTHETICAL_VISIBLE_NO_RECEIPT` | **No** |
| Exact hypothetical, matching-account G21.23 receipt | `HYPOTHETICAL_VISIBLE_UNBOUND_RECEIPT` | **No** — receipt cannot be bound to the hypothetical contents or intent |
| Foreign receipt | `RECEIPT_IDENTITY_MISMATCH` | **No** |
| Disk missing or divergent | `MISSING_ACCOUNT_QUARANTINE` / `DIVERGENT_ACCOUNT_QUARANTINE` | **No** |
| Stale owner, mismatched/forged observation | `STALE_OR_UNBOUND_RESERVATION` / `UNTRUSTED_OBSERVATION` | **No** |
| Process restart without the original reservation | `RESTART_REQUIRES_MANUAL_RECONCILIATION` | **No** |

Every Decision explicitly has `durabilityConfirmed=false`, `grantAuthorized=false`, `replayAuthorized=false`, `safeReleaseAuthorized=false`, and `clientSuccessAuthorized=false`. The pure decision never invokes repository writes, account mutation, client packets, or `cancelIfStillUnclaimed`. It is not an executable recovery operation.

## Deterministic regression

`G2129MailboxCrashRecoveryDecisionIntegrationTest` covers exact PREPARED after FIFO read, idempotent repeated assessment, separate safe unclaimed cancellation, visible hypothetical bytes with no receipt, genuine strict receipt for the **wrong snapshot** (correct account), foreign strict receipt, mismatched observation, null-token restart quarantine, missing and divergent records, conservative repair and cancellation, owner logout/new generation, and live inventory/Mailbox nonmutation.

Expected Gradle target: `g2129MailboxCrashRecoveryDecisionRegression`. Focused manifest: 327 → **328 / Java 11**. Only exact-head hosted success may certify this milestone. A successful compile/test does not establish pinned native-v308 visual acceptance or original SpawnPK reward economics.

## Required future authority (not part of G21.29)

Before native v308 widget **32181** may move items, a separate milestone must introduce a crash-reconstructible per-account transaction identity and single-writer strict postimage receipt *bound to the canonical account bytes*, transaction key, owner/generation semantics, and a proven once-only durable/live-state reconciliation. It must address uncertain post-rename failures, queued checkpoints, forced shutdown and duplicate client actions. If these cannot be proven, the native inventory-deposit intent stays **NO_GRANT**.

The bank-deposit widget 32178 is excluded. The frozen R25 promotion PR #1847 is unchanged.
