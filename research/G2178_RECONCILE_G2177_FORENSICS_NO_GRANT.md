# G21.78 — reconcile G21.77 parallel forensic protections (NO GRANT)

**Parent:** exact hosted-certified G21.77 bounded-decoding branch commit `8a1279d213f16883fa8906dc670580dac14532e5`, PR #2331, 377 focused Java 11. **Related:** lock-path preflight PR #2333, hosted-certified commit `5f4f774e69e7f1409fef537d70a307d40806852e` and issue #2334.

## Why this integration exists

The bounded account/marker decoder and forensic lock-path preflight both branched from G21.76 and independently registered the 377th test. Either branch alone lacks the other's safety control.

This integration stacks on PR #2331 and copies the separately certified PR #2333 changes to `MailboxAccountPublicationCoordinator`, the G21.76 early-veto regression expectations, the new G21.77 lock preflight integration test, and its research note. It preserves PR #2331's bounded `FilePlayerRepository` account decode/rehash logic verbatim. Both G21.77 regression tests are registered, yielding **378 focused tests** if the full train passes.

## Assertions / boundaries

- No branch-specific forensic defenses are dropped; normal blocking World publication/save code is unchanged.
- The original G21.76 ancestor-symlink integration test accepts the correct earlier denial from G21.77 before the advisory-lock file is opened.
- The G21.77 bounded decoder test still checks predecode 64MiB+1 and marker 4097B rejection, exact boundary acceptance, stream-growth rejection and normal negative World-session admission veto.
- The G21.77 lock preflight test checks unsafe parent symlink refusal before lock creation, symlink lock-leaf refusal and unchanged sentinel, safe missing-account witness, old blocking save path and lease cleanup.
- Not an atomic secure directory handle traversal under hostile rename races; POSIX ctime still optional; 64MiB raw bytes do not guarantee small Java Properties allocation.
- No positive durable COMMIT, reward/inventory grant, Mailbox CLAIMED, replay, restart admission, journal/marker deletion, reservation release or client ACK. Native C2S185/widget32181 and frozen R25 #1847 remain NO_GRANT/unchanged.

## Certification state

**Uncertified until exact-head hosted GitHub Actions full Gradle / Java 11 / current-train 378 passes.** Prior individual 377/377 green runs do not certify their combination. Keep PR Draft and unmerged.
