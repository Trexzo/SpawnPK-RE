# G21.170 — isolate World temporary serialization from legacy direct save (NO AUTHORITY)

**Certified parent:** G21.169 [Draft PR #2518](https://github.com/Trexzo/SpawnPK-RE/pull/2518), `ee9406b7c9a49c13700205f5c3f366d2e10b605e`, [Actions #38406650933](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38406650933) SUCCESS Java11 major55, focused 469/469 and full Gradle. Issue #2519.

## Source-confirmed collision

Both the genuine guarded `FilePlayerRepository.saveForWorld` and intentionally unguarded `save` previously serialized account snapshots into the exact shared sibling path `<account>.properties.tmp`. The cooperating G21.38/G21.169 publication FileLock is acquired only **after** World serialization. A direct raw legacy save ignores that lock, truncates the shared `.tmp`, and atomically moves it into the account file. If interleaved after World serialization but before its replacement, the World publisher could find a missing temp or use overwritten bytes despite its own correct snapshot.

## Narrow live World fix

G21.170 changes only `enforceWorldAdmission=true` in `FilePlayerRepository.saveInternal` to allocate a unique same-directory temp with `Files.createTempFile(parent, accountName+".g21170-world-", ".tmp")`. The original raw `save()` **retains** its fixed `<account>.tmp` path, arbitrary writes and old admission semantics. World still performs the same G21.36 and G21.88 negative marker checks, G21.66 disk terminal veto, G21.169 guarded publication lock and existing `replaceSnapshotTemp` atomic-move/fallback behavior. Only the World-owned temp is removed after a failed World operation.

## Deterministic real-file regression

`G21170MailboxWorldTempLegacyCollisionIntegrationTest` covers: (1) preexisting direct raw `.tmp` sentinel untouched through successful normal guarded World publication, (2) actual direct `save()` to the **same account** during the preexisting `beforeWorldReplace` callback, after World serialized but before World acquired its publication lock, while World-owned unique temp survives and the correct World snapshot wins, (3) injected World failure cleans only World-owned temp, preserving account and unrelated legacy temp, (4) unrelated account still saves, (5) direct raw writer can still overwrite account **after** World returns, proving no claimed comprehensive writer isolation; no temp or JVM lease leaks.

**Acceptance:** Java11 major55, focused **470/470**, dedicated Gradle regression, hosted full Gradle on exact PR SHA. Keep Draft/unmerged until verified.

## Safety boundaries

This fixes **temporary-file ownership collision**, NOT direct legacy/raw/default/future writer exclusion from the account, nor canonical per-account durable transaction epoch, independent signed historical custody, multi-file crash/power-loss atomics, or any cleanup/rollover/pruning/unlink/grant/replay/session/release/ACK authority. Original R25 #1847 / G21.87/88 and original writer flow except guarded World temp pathname remain untouched.
