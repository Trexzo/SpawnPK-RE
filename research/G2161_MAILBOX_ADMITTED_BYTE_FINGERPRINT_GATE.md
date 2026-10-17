# G21.61 — admitted World account byte fingerprint consistency

**Parent:** hosted-certified G21.60 `9161d7e8518d97f6471cb277d7f632fdbadc70d8` (run #37938226961 SUCCESS, 360 focused Java11); [issue #2298](https://github.com/Trexzo/SpawnPK-RE/issues/2298).

## Gap
G21.60 before/after NOFOLLOW `BasicFileAttributes` cannot detect an uncooperative same-size in-place rewrite when last-modified time is restored. A world session could admit a decoded account snapshot without evidence that those bytes still match the selected account file.

## Narrow correction
- G21.61 hashes the exact admitted decoder input bytes (SHA-256 through `DigestInputStream`) while holding the existing cooperating G21.59 account publication lock.
- After inherited marker, normalized-path and G21.60 metadata checks, it independently rereads the pinned file with `READ + NOFOLLOW_LINKS` and compares the raw byte fingerprints; a mismatch is **negative-only** `G21.61 MAILBOX_SESSION_ACCOUNT_BYTES_CHANGED ... REJECT_SESSION`.
- Repeats G21.60 NOFOLLOW metadata comparison after the fingerprint read. Preserves raw nonadmitting `repository.load` compatibility and stable empty-account handling.
- New real World regression injects a same-size comment byte mutation and restores original file mtime, proves G21.60 before/after metadata equality, and demands G21.61 rejection. Also checks stable present/missing, raw forensic load, and absence of resource or marker cleanup leaks.
- Focused manifest 360→361, new `g2161MailboxAdmittedByteFingerprintRegression` task; exact-head hosted CI and full Gradle build required before certification.

## Boundary
A two-pass hash consistency check is not atomic filesystem snapshot isolation, a full adversarial ABA guarantee, or protection against a writer racing both checks. No cross-file crash durability, exactly-once inventory COMMIT, reward grant, replay, rollback, quarantine release or power-loss claim. Native C2S185 widget32181 remains NO_GRANT; R25 #1847 untouched. Keep Draft/unmerged.
