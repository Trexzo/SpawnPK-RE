# G21.84 — G21.83 journal integrated into World session admission (NO GRANT)

Parent G21.83 certified commit \`eede2a9d9830b0da93d8377869753d8acdab3b65\`, [Actions #38043470105](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38043470105) SUCCESS with 383 focused Java11 tests. Tracks [issue #2347](https://github.com/Trexzo/SpawnPK-RE/issues/2347).

## Production integration

The opt-in G21.83 persistent PREPARED idempotency journal is now an actual *negative-only* session consistency fence in \`FilePlayerRepository.loadForWorldSession\`, inside the existing G21.59 account-local publication lock. The repository checks the pinned journal name before account file decode and again after G21.60/61 NOFOLLOW metadata and independent account-byte digest. The two journal reads bracket the established G21.58 test hook. A journal that appears, disappears, changes byte content, is malformed/oversized/symlinked, belongs to another account or no longer matches the exact decoded PREPARED snapshot and G21.22 message/intent key causes IOException and rejection. No nested OS/JVM account lock is acquired.

**Absent journal on both reads** preserves old legacy/non-G21.22 and valid PREPARED load policy. A valid journal with exact PREPARED UNCLAIMED state is admitted by the *existing* G21.31 policy, never a positive claim. The 4 existing G21.32/47/48 negative markers still veto. Coherent terminal account data with G21.83 journal remains quarantined; a terminal identity is NOT a PREPARED World session. Raw repository forensic \`load\`, strict writer, World grant behavior and client packet handling remain unchanged.

## Hosted regression

\`G2184MailboxJournalWorldAdmissionIntegrationTest\` covers real \`WorldPlayerPersistence.load\` with valid journal PREPARED, PREPARED without journal, legacy without journal, mismatched account, foreign copied journal, corrupt and 1KiB+ records, missing account, coherent terminal, three negative marker classes, symlink leaf and deterministic journal rewrite/appearance/deletion during account read. It verifies original live owner remains UNCLAIMED and inventory-free, clean journal bytes are unchanged, direct raw forensic loads still work and no temp or lock leaks occur.

These checks do not establish hardware crash consistency, mutual exclusion with uncooperative raw writers, an authenticated transaction journal, positive COMMIT, exactly-once inventory mutation, automatic replay, reservation release, or ACK. No native C2S185/widget32181 rewards enabled. The G21.71-78 forensic census remains unchanged; G21.83 is only a session-admission negative guard. Frozen R25 PR #1847 untouched.

Focused manifest 383 -> **384** unique Java11 tests, standalone \`g2184MailboxJournalWorldAdmissionRegression\`. PR stays Draft/unmerged pending exact-head hosted CI.
