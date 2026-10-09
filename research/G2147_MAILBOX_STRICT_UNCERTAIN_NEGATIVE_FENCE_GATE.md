# G21.47 — persistent negative fence for detected strict PREPARED checkpoint uncertainty

**Certified parent:** G21.46 exact `2a992f4681489c6b5fde16436f1b74b92a92528d`, hosted [run #37899632233](https://github.com/Trexzo/SpawnPK-RE/actions/runs/37899632233) SUCCESS / **345 focused Java11 tests**. Frozen R25 promotion PR #1847 untouched.

## Narrow gap

G21.46 intentionally reports `UnconfirmedCommitException` if a player changes or unregisters **after** an account-file ATOMIC_MOVE and directory force but before the strict writer's post-publication callback. The account file might already contain older PREPARED state even though the live World owner diverged; the exception does **not** outlive a restart. G21.32's unrelated negative review record requires a full valid settlement proposal, which should not be guessed from a now-divergent live owner.

## G21.47 negative-only sidecar

`MailboxStrictUncertainFence` adds an independent *write-once, no-grant* sibling marker (`.g2147-strict-uncertain`) for this precise strict checkpoint uncertainty. The content contains no positive claim, inventory, settlement receipt or replay authority. The marker is published by a unique forced temporary file and an exclusive hard link of that inode into the final marker name. The parent directory's metadata is forced and there is no unsafe rename/copy fallback; a pre-existing marker is never overwritten or cleared. A marker's **presence**, even if content is malformed or a symlink, means **manual review required**.

The real strict World writer performs this negative-only publication **inside the same G21.38–G21.40 account-specific cross-JVM publication lock** on G21.46's *postpublication* divergence path. This prevents other cooperating file-backed World writes and G21.34 marker publishers from interleaving **while the detected-uncertainty marker is being published**. Subsequent sessions' FIFO file-backed admission checks and ordinary World saves recognize the new sidecar through `FilePlayerRepository.hasUnresolvedMailboxReviewFence`, both before and after the account load and at final World-save publication. Strict World checkpoints also check sidecar absence on entry and again inside their account publication lock.

If sidecar publication or parent-directory force fails, the strict result **still throws `UnconfirmedCommitException`** and adds the marker failure as suppressed evidence. No affirmative durable quarantine is claimed in that case; persistence may remain absent/uncertain. Neither negative sidecar API automatically removes a previously published marker.

## Regression

`G2147MailboxStrictUncertainFenceIntegrationTest` executes the genuine concrete file-backed World strict PREPARED task, pauses at `AFTER_DIRECTORY_FORCE` after account replacement, changes live movement and resumes. Requires an unconfirmed result and a no-clobber G21.47 negative marker, refuses subsequent ordinary and strict World saves without changing the stored account file, and rejects login in a new World. An unrelated account continues to save and load, and no reward/claim/replay/release occurs. A duplicate sidecar publication preserves the original marker bytes, and unpublished temp files and JVM lock leases are cleaned.

Focused Java11 suite advances **345→346**, with `g2147MailboxStrictUncertainFenceRegression`. Exact-head hosted CI must pass before certification.

## Critical limits

This is **best-effort durable negative publication after a detected uncertain checkpoint**, not a power-loss-atomic account+sidecar transaction. A JVM or hardware crash may happen **between account replacement and sidecar publication**. A failed filesystem marker publication or metadata force also leaves durable quarantine unproven. Locking coordinates only cooperating file-backed writers on the tested filesystem, not arbitrary external processes. A later World mutation after the final callback is also outside this marker path.

This does not grant native Mailbox widget32181, replay a claim, authorize a rollback, clear a marker, or produce an exactly-once item settlement. Bank widget32178 excluded. **NO_GRANT** remains authoritative and R25 PR #1847 is unchanged.
