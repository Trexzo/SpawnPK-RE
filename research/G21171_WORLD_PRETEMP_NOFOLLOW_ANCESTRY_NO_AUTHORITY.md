# G21.171 — reject redirected World temp ancestors before serialization (NO AUTHORITY)

**Certified parent:** [G21.170 Draft PR #2520](https://github.com/Trexzo/SpawnPK-RE/pull/2520), exact `47eeaccc3b2b8302426472b1684cc5995df685b0`, [Actions #38408426225](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38408426225) SUCCESS, Java11 major55, focused 470/470, hosted Gradle. Issue #2521.

## Source-derived confidentiality/write-boundary gap

The actual guarded `FilePlayerRepository.saveForWorld` uses G21.170's unique same-directory temporary file, but obtains G21.169's cooperating publication lock **after** serializing the World snapshot. G21.169 checks the lock leaf/ancestor then and vetoes a symlinked directory root, but the World snapshot may already have been serialized into that redirected directory. `saveInternal` previously called `Files.createDirectories(parent)` without an earlier NOFOLLOW ancestor census; a preexisting symlink ancestor could also redirect directory creation.

## Narrow actual World fix

For **`enforceWorldAdmission=true` only**, `FilePlayerRepository` now asks G21.169's `MailboxAccountPublicationCoordinator` to check **existing** directory components with NOFOLLOW before `Files.createDirectories`. Any symlink, non-directory or unknown fileKey refuses `G21.171 WORLD_TEMP_ANCESTRY_UNSAFE_NO_AUTHORITY`. Truly absent directories may be created, then G21.77 strict ancestry validation is reused immediately **before** `Files.createTempFile` or any World snapshot serialization. No new persistent sidecar, lock, terminal admission branch or reward authority. Ordinary direct `FilePlayerRepository.save` and non-World strict writer remain intentionally unchanged. Already-selected account path, existing G21.36/G21.88 negative marker checks and G21.169 lock-guarded final publication remain unchanged.

## Real filesystem regression

`G21171MailboxWorldTempPreSerializationAncestryIntegrationTest`: genuine normal World save and fresh nested-directory World save succeed; existing symlink-parent to an independent victim directory (on supporting filesystems) vetoes **before any account/temp output** and preserves victim sentinel; non-directory ancestor vetoes; deliberately raw direct save via symlink still works to prove that legacy semantics were not silently changed; unrelated World account continues; zero World temp/JVM leases. Symlink availability must be reported, never fabricated. Host CI is Linux, not Windows; no platform-general symlink proof is claimed.

Acceptance: dedicated Gradle regression; Java 11 major 55; 471/471 focused, exact PR SHA full Gradle SUCCESS. Keep Draft/unmerged pending verification.

**Non-claims:** the two NOFOLLOW ancestry scans are not an atomic directory descriptor binding across raw concurrent replacements, full uncooperative writer exclusion, historical provenance or signer custody, or an atomic multi-file crash/power-loss epoch. No rollout/settlement authorization, cleanup/rollover/pruning/unlink/grant/replay/session/release/ACK. Frozen R25 PR #1847, G21.87 and G21.88 untouched.
