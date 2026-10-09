# G21.49 — cross-branch B×C force failure reconciliation proof

**Provisional base**: certified G21.48-B exact `28468ad080ca29b958736bfd64e443df3a1aad69`, workflow #37908586109 SUCCESS, 347 focused Java 11 tests. This is not a full branch reconciliation. Track [issue #2274](https://github.com/Trexzo/SpawnPK-RE/issues/2274).

The competing G21.48-C [PR #2273](https://github.com/Trexzo/SpawnPK-RE/pull/2273) independently proved postmove directory-force failure quarantine. The B strict writer has a write-ahead intent and permanent negative marker on its existing UnconfirmedCommitException path. Instead of copying an incompatible alternative marker protocol, this test reproduces C's real World error phases on B and asserts **both** negative guards persist.

Checks: BEFORE_DIRECTORY_FORCE / AFTER_DIRECTORY_FORCE (after actual account atomic move) must return UNCONFIRMED, leave permanent G21.47 negative marker and stranded G21.48 write-ahead intent, refuse restart and guarded World writes. BEFORE_ATOMIC_REPLACE (before write-ahead arm) must leave original account bytes intact and create neither marker. Unrelated account remains usable; no item credit, claim, replay, marker release, temp or lock leak.

Focused manifest 347→348; task `g2149MailboxWalPostmoveForceReconciliationRegression`. Exact-head hosted build required for certification. **Still outstanding**: G21.48-A checksum-bound read-only forensics, differing G21.47 marker formats/names and explicit cross-branch integration; do not promote as complete G21.49, claim atomic account+marker transaction, or enable native C2S185 widget32181 grants. Frozen R25 PR #1847 untouched.
