# G21.80 — strict terminal ordering audit (NO GRANT)

Parent: certified G21.79 commit `0704f269243877c842059a30a2405f49607b2d73`, 379 Java 11 focused; [Actions #38039868529](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38039868529) SUCCESS. Issue [#2339](https://github.com/Trexzo/SpawnPK-RE/issues/2339).

## Scope

The existing G21.66 terminal strict writer has precise file-operation ordering:
1. Validate immutable prepared vs terminal identity, prepare temp and serialize.
2. Force temp bytes and enter per-account G21.39 cooperating publication lock.
3. Revalidate world owner/negative marker / exact prepared account, then force write-ahead negative G21.48 marker BEFORE atomic account replacement.
4. Perform ATOMIC_MOVE, force parent directory, recheck World ownership; on postmove uncertainty, arm permanent negative G21.47 marker while still holding the account lock.
5. Only after confirmed post-write owner and marker cleanup return a canonical snapshot-bound **file operation** Receipt.

Even success is NOT a crash-durable positive multi-record COMMIT, not a portable restart credential, and does not establish World live application, reservation release, or ACK.

## Implementation

`MailboxStrictTerminalOrderingAudit` is a pure, non-admitting read-only categorizer combining an exact G21.64 terminal proposal, its expected canonical account path, a process-local G21.23/30 Receipt when present, and a G21.71 read-only disk classifier:
- PREPARED with no receipt: no replacement, unsettled.
- G21.32/47/48 negative marker: manual review, including a stale/matched receipt; marker veto always dominates.
- Coherent terminal without in-process receipt: visible but quarantined.
- Coherent terminal with matching in-process receipt: **strict file operation confirmed**, NOT durable Mailbox transaction COMMIT.
- PREPARED with claimed receipt: conflict/quarantine.
- Invalid/legacy/missing evidence: quarantine; foreign or mismatched receipt input refused.

Every state holds all four G21.79 positive proof families missing and publishes NO_GRANT/no-replay/no-release/no-ACK fixed-false fields. No filesystem I/O occurs in the audit itself. A point-in-time G21.71 observation cannot be reused as durable settlement authority.

## Real writer fault matrix

`G2180MailboxStrictTerminalOrderingAuditIntegrationTest` seeds genuine PREPARED snapshots in real files, then invokes `saveStrictTerminalForWorld` with exact expected repository file, prepared canonical fingerprint and deterministic no-op pre/post owner callback fixtures (test harness only, NEVER a production settlement caller). Fault seams:
- **Five early failures** before any account replacement: BEFORE_TEMP_CREATE / AFTER_TEMP_CREATE / AFTER_SERIALIZE / BEFORE_FILE_FORCE / BEFORE_ATOMIC_REPLACE -> PREPARED, no receipt, ordinary PREPARED session load preserved.
- **One write-ahead fault:** AFTER_WRITE_AHEAD_INTENT -> stranded G21.48 marker, PREPARED bytes, session veto, no receipt.
- **Two postmove faults:** BEFORE_DIRECTORY_FORCE / AFTER_DIRECTORY_FORCE -> G21.47 uncertain marker + G21.48 marker, terminal may be visible, session veto, no receipt.
- **Normal strict file operation:** matching snapshot-bound Receipt + coherent terminal, yet restart is still quarantined, live owner remains PREPARED, Mailbox UNCLAIMED and inventory unchanged.

Test additionally checks foreign receipt rejection, no receipt not sufficient to admit a terminal, unchanged raw bytes during the audit, no stale Java advisory leases and no temp-file leaks. The existing writer and packet code are **unchanged**.

## Explicit omissions

There is no hardware power-fail durability proof, no durable positive COMMIT record, no restart idempotency, no World-owned atomic live apply, no reservation release and no ACK protocol. No direct or native C2S185/widget32181 reward grant. G21.79 and R25 #1847 remain unchanged.

380 focused Java 11 unique tests required and exact-head hosted full Gradle CI; keep draft/unmerged until green.
