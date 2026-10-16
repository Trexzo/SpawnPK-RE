# G21.283/G21.284 — ancestor NOFOLLOW witness for exact account forensics

**Exact parent:** [Draft #2822](https://github.com/Trexzo/SpawnPK-RE/pull/2822) `06bbf5324c7605e4a5064323fedfc765b6b2c9c2`, G21281/G21282 bounded double-digest account byte witness, 681 focused candidate; independently certified combined baseline is [#2819](https://github.com/Trexzo/SpawnPK-RE/pull/2819) `bf6ac4c62698568290f05f288e07baeb978d187f` with hosted **677/677**. Chat 2 observer and canonical NPC sources remain unchanged.

## Source-derived gap

The read-only G21.279 operator inspector performs an initial `MailboxAccountPublicationCoordinator.requireSafeWorldTempAncestry(account,false)` (NOFOLLOW ancestor metadata check). Later it hashes only the account's **final component** via `FileChannel.open(..., NOFOLLOW_LINKS)`. If a foreign actor replaces an **ancestor directory** with a symlink to the original directory after initial ancestry preflight, both account digests, inode identity, size and mtime can still match—because final-component NOFOLLOW does **not** prevent traversal through an intermediate symlink.

## Actual production correction

G21.283 repeats the existing READ-ONLY NOFOLLOW **entire account ancestry** preflight after the first bounded SHA-256 (and any package-only per-call fault seam), *before* reading metadata and sidecars again. It performs another ancestry preflight immediately before returning a completed report, after the second bounded SHA-256 and final account stat. Unsafe or substituted ancestors throw `G21.283 FORENSIC_ACCOUNT_ANCESTRY_CHANGED_NO_AUTHORITY` rather than printing a clean report.

This adds no new locks, files, ownership tokens, marker manipulation, scan outside the selected account ancestry, account admission, recovery, mailbox grant, durable epoch or final-save acknowledgment. It is observational only. A malicious foreign process could still make temporary ABA substitutions between probes or immediately after the final check; NO atomic snapshot or durable parent-directory lock is proven.

## Deterministic real-file regressions

- **G21.283:** create selected account in its own directory; at the exact after-FIRST-digest callback move original parent directory to a sibling, then create a relative symlink under its former name to that ORIGINAL directory. Selected account's underlying bytes, inode and final-component type remain unchanged. Without a second ancestor scan the inspector could return an apparently stable report; with G21.283 it must fail closed. No lock/review marker created. The fixture restores the original directory before cleanup.
- **G21.284:** create a symlink to an unrelated account's parent directory while scanning a stable selected account in a different ancestry. Inspector must still produce the selected account's exact SHA-256 and zero negative-marker count; foreign bytes preserved. Prevents global symlink veto. Symlink fixtures follow the pre-existing G21280 portability convention and disclose actual host support; Linux hosted runner should exercise them.

**Manifest:** inherited 681 distinct + two real filesystem test classes = **683**, two named Gradle JavaExec gates and only one production Java change in `LocalAccountForensicEvidence.java`. Existing G21279/80/81/82 and Chat2 G1955/56 tests remain.

**Gate:** independently hosted exact-head 683/683 including G21281–84, G1955/56, G21277/78, Java11 major55 and full Gradle. Parent heads require their own hosted CI. No release/merge, auto-recovery, marker cleanup or durable owner ACK.
