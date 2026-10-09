# G21.48 — strict PREPARED postmove directory-force uncertainty quarantined

**Certified parent:** G21.47 `dfceba9870efbd6a72c151695ef6ff935726ca0b`, [hosted run #37911404084](https://github.com/Trexzo/SpawnPK-RE/actions/runs/37911404084) SUCCESS, focused 346 / Java 11. Frozen R25 promotion PR #1847 untouched.

## Actual uncovered error path

The strict file-backed World PREPARED writer's G21.23 `publishStrictReplacement` first performs `Files.move(temp,file,ATOMIC_MOVE)`, then forces the account directory. If `BEFORE_DIRECTORY_FORCE`, filesystem directory force, or `AFTER_DIRECTORY_FORCE` throws, the writer deliberately emits `UnconfirmedCommitException`, since account bytes **may already be replaced**. G21.47 began publishing a durable negative uncertainty sidecar for a separate G21.46 late World owner mismatch **after a successful directory force**; this preexisting postmove force-failure path was not covered. The negative review fence would otherwise be absent on later restart despite an uncertain strict receipt.

## Bounded G21.48 repair

For the **real concrete file-backed World strict PREPARED writer**, while already holding the G21.38–G21.40 per-account JVM/FileLock and before returning from account publication, catch the precise `UnconfirmedCommitException` raised by `publishStrictReplacement`'s *postmove* metadata force stage. Attempt to publish the existing G21.47 write-once, no-clobber negative-only strict uncertainty marker **inside that lock**. Preserve and rethrow the original `UnconfirmedCommitException`, never claim account bytes were rolled back or that directory force completed. If marker creation or metadata force fails, attach `G21.48 POSTMOVE_QUARANTINE_UNPROVEN` as suppressed error evidence; the strict outcome remains **UNCONFIRMED** and durable quarantine is **not claimed**.

This does not mark definitive pre-move failures. Historical direct `saveStrict` and custom PlayerRepository adapters keep their earlier scopes. The normal `FilePlayerRepository` World admission paths and strict G21.42/G21.47 checks already reject any G21.47 marker at load and save boundaries, including malformed or symlink markers.

## Integration acceptance

`G2148MailboxStrictPostmoveForceFenceIntegrationTest` drives genuine file-backed World strict barriers with deterministic fault injection:
- Throw at `BEFORE_DIRECTORY_FORCE`, after ATOMIC_MOVE: strict future unconfirmed, previously stored account bytes actually replaced, G21.47 sidecar present; restarted World refuses login; guarded ordinary save cannot overwrite.
- Throw at `AFTER_DIRECTORY_FORCE`: still an unconfirmed strict result and persistent negative sidecar; no false positive receipt.
- Throw at `BEFORE_ATOMIC_REPLACE`: failure occurs before move; no uncertainty sidecar is synthesized and original file bytes remain intact.
- Unrelated account loads and saves normally; zero live rewards, no Mailbox claim, no auto-release or replay, no unpublished temps or JVM lock leaks.

Java11 focused manifest **346→347**, task `g2148MailboxStrictPostmoveForceFenceRegression`. Full exact-head hosted CI must pass before certification.

## Residual limits

A process crash can still occur after replacing the account file but before persisting the negative sidecar. Filesystem failures during the marker write/force may also leave its durable presence unproven. G21.48 does **not** provide an atomic file+marker transaction, a hardware durability guarantee, positive COMMIT, exactly-once inventory+Mailbox settlement, automatic rollback, marker release, or native C2S185 widget32181 claim. **NO_GRANT** authority remains unchanged. Bank widget32178 excluded. R25 #1847 untouched.
