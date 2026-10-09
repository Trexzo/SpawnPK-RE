# G21.51 — bounded, no-symlink negative Mailbox marker reads

Parent: certified [G21.50 PR #2277](https://github.com/Trexzo/SpawnPK-RE/pull/2277), exact `2501acb14b930c72ed298643be044af50d3191b8`, [hosted CI #37918742062](https://github.com/Trexzo/SpawnPK-RE/actions/runs/37918742062) SUCCESS, 350 Java 11 focused tests. Scope tracked by [issue #2278](https://github.com/Trexzo/SpawnPK-RE/issues/2278).

## Source failure mode
G21.32 `MailboxDurableReviewFence.inspect` called `Files.readAllBytes()` **before** its 2048-byte validation. G21.50 copied that unbounded read for secondary proposal evidence. B/legacy diagnostic parsers prechecked the file size but subsequently used unbounded reads, which could race a malicious replacement/growth. G21.48 transient write-ahead intent cleanup likewise used unbounded bytes for its exact comparison. Review sidecars are untrusted operator evidence: malformed, huge, replaced or symlinked sidecars must **not** become trusted state, automatically clear quarantine, or allocate arbitrary input length.

## Narrow negative-only hardening
- Shared `MailboxNegativeMarkerBoundedRead.read(path,min,max)`: Java 11; accepts only regular-file, in-range NOFOLLOW metadata; opens the file with `READ + NOFOLLOW_LINKS`; uses a fixed `max+1` ByteBuffer; refuses an oversized or changed actual read and never decodes arbitrary file content. Max 4096 in this package. Metadata and descriptor may still race; the descriptor read is bounded regardless.
- Used in G21.32 review parser (80..2048), G21.49 B permanent/intent parser (60..384), A legacy checksum parser (80..512), G21.50 secondary G21.32 evidence scan (80..2048), and G21.48 confirmed-only transient intent identity read (exact expected length).
- Existing marker presence checks and **fail-closed** login/save refusals remain unchanged. This primitive does not publish, overwrite, release or grant anything, and makes no power-loss/atomic snapshot claims.

## Regression
`G2151MailboxBoundedNegativeMarkerReadIntegrationTest` tests direct exact-2048-byte boundary acceptance and 2047 cap refusal; a symbolic-link negative marker; actual 16-MiB sparse G21.32 review, B permanent, B intent and multi-marker oversized inputs; legacy symlink refusal; valid small B permanent marker and independent healthy account; real World session denial for all marked accounts; all positive authority flags false; markers remain present and no JVM publication lease or temp leak.

Focused current-train manifest **350→351 distinct Java11 classes**; `g2151MailboxBoundedNegativeMarkerReadRegression`. Exact-head hosted full CI must report `GRADLE_CURRENT_TRAIN_FOCUSED_PASS count=351 bytecodeMajor=55` before certification.

**No positive settlement authorization.** Native C2S185 widget32181 NO_GRANT. Neither this issue nor any green build certifies power-loss-safe or exactly-once Mailbox inventory COMMIT, rollback/replay, human approval or marker release. Frozen R25 PR #1847 untouched; all PRs remain Draft.
